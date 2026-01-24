import uuid
import os
from pathlib import Path
from typing import Optional

import boto3
from botocore.client import Config
from botocore.exceptions import NoCredentialsError
from fastapi import HTTPException, UploadFile
from .config import settings


# Local upload directory (fallback only)
UPLOAD_DIR = Path("/tmp/tvgo-uploads")
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)


def get_s3_client():
    """
    Get S3 client. Works with:
    1. Explicit credentials from settings (aws_access_key_id, aws_secret_access_key)
    2. IAM role (when running on AWS Lambda/ECS - no explicit credentials needed)
    3. Environment variables (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
    """
    if not settings.s3_bucket_name:
        return None

    region = settings.aws_region or os.environ.get('AWS_REGION', 'eu-central-1')

    try:
        # If explicit credentials are provided, use them
        if settings.aws_access_key_id and settings.aws_secret_access_key:
            session = boto3.session.Session(
                aws_access_key_id=settings.aws_access_key_id,
                aws_secret_access_key=settings.aws_secret_access_key,
                region_name=region,
            )
            return session.client("s3", config=Config(signature_version="s3v4"))
        else:
            # Use default credential chain (IAM role, env vars, etc.)
            return boto3.client("s3", region_name=region, config=Config(signature_version="s3v4"))
    except Exception as e:
        print(f"Failed to create S3 client: {e}")
        return None


async def upload_image_to_s3(file: UploadFile) -> str:
    """Upload image to S3. Requires S3 to be configured."""
    s3 = get_s3_client()
    extension = (file.filename or "image").split(".")[-1] or "png"
    unique_id = uuid.uuid4().hex
    region = settings.aws_region or os.environ.get('AWS_REGION', 'eu-central-1')

    if not s3 or not settings.s3_bucket_name:
        raise HTTPException(
            status_code=500,
            detail="S3 is not configured. Please set S3_BUCKET_NAME environment variable."
        )

    key = f"images/{unique_id}.{extension}"
    bucket = settings.s3_bucket_name

    try:
        s3.upload_fileobj(
            Fileobj=file.file,
            Bucket=bucket,
            Key=key,
            ExtraArgs={"ContentType": file.content_type or "application/octet-stream"},
        )
    except NoCredentialsError:
        raise HTTPException(
            status_code=500,
            detail="AWS credentials not configured. Please set up IAM role or AWS credentials."
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to upload to S3: {e}")

    if settings.s3_public_base_url:
        base = str(settings.s3_public_base_url).rstrip("/")
        return f"{base}/{key}"
    else:
        return f"https://{bucket}.s3.{region}.amazonaws.com/{key}"


async def upload_image_from_url(image_url: str, prefix: str = "logos") -> Optional[str]:
    """
    Download image from external URL and upload to S3.
    Used for EPG logos that need to be stored in S3.

    Args:
        image_url: External URL of the image to download
        prefix: S3 key prefix (folder name)

    Returns:
        S3 URL of uploaded image, or None if upload failed
    """
    import urllib.request
    from io import BytesIO

    if not image_url:
        return None

    s3 = get_s3_client()
    region = settings.aws_region or os.environ.get('AWS_REGION', 'eu-central-1')

    if not s3 or not settings.s3_bucket_name:
        print(f"S3 not configured, skipping upload from URL: {image_url}")
        return None

    try:
        # Download image from external URL
        req = urllib.request.Request(
            image_url,
            headers={'User-Agent': 'Mozilla/5.0 (compatible; tvGO/1.0)'}
        )
        with urllib.request.urlopen(req, timeout=30) as response:
            image_data = response.read()
            content_type = response.headers.get('Content-Type', 'image/png')

        # Determine file extension from content type
        ext_map = {
            'image/png': 'png',
            'image/jpeg': 'jpg',
            'image/jpg': 'jpg',
            'image/gif': 'gif',
            'image/webp': 'webp',
            'image/svg+xml': 'svg',
        }
        extension = ext_map.get(content_type, 'png')

        # Also try to get extension from URL
        if '.' in image_url.split('/')[-1]:
            url_ext = image_url.split('.')[-1].split('?')[0].lower()
            if url_ext in ['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg']:
                extension = url_ext if url_ext != 'jpeg' else 'jpg'

        unique_id = uuid.uuid4().hex
        key = f"{prefix}/{unique_id}.{extension}"
        bucket = settings.s3_bucket_name

        s3.upload_fileobj(
            Fileobj=BytesIO(image_data),
            Bucket=bucket,
            Key=key,
            ExtraArgs={"ContentType": content_type},
        )

        if settings.s3_public_base_url:
            base = str(settings.s3_public_base_url).rstrip("/")
            return f"{base}/{key}"
        else:
            return f"https://{bucket}.s3.{region}.amazonaws.com/{key}"

    except Exception as e:
        print(f"Failed to download/upload logo from {image_url}: {e}")
        return None
