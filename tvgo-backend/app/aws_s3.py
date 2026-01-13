import uuid
import os
from pathlib import Path
from typing import Optional

import boto3
from botocore.client import Config
from fastapi import HTTPException, UploadFile
from .config import settings


# Local upload directory
UPLOAD_DIR = Path("/tmp/tvgo-uploads")
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)


def get_s3_client():
    if not settings.aws_region or not settings.s3_bucket_name:
        return None
    session = boto3.session.Session(
        aws_access_key_id=settings.aws_access_key_id,
        aws_secret_access_key=settings.aws_secret_access_key,
        region_name=settings.aws_region,
    )
    return session.client("s3", config=Config(signature_version="s3v4"))


async def upload_image_to_s3(file: UploadFile) -> str:
    """Upload image to S3 or local storage if S3 is not configured."""
    s3 = get_s3_client()
    extension = (file.filename or "image").split(".")[-1] or "png"
    unique_id = uuid.uuid4().hex
    
    # If S3 is configured, upload there
    if s3 and settings.s3_bucket_name:
        key = f"images/{unique_id}.{extension}"
        bucket = settings.s3_bucket_name

        try:
            s3.upload_fileobj(
                Fileobj=file.file,
                Bucket=bucket,
                Key=key,
                ExtraArgs={"ContentType": file.content_type or "application/octet-stream"},
            )
        except Exception as e:
            raise HTTPException(status_code=500, detail=f"Failed to upload to S3: {e}")

        if settings.s3_public_base_url:
            base = str(settings.s3_public_base_url).rstrip("/")
            return f"{base}/{key}"
        else:
            return f"https://{bucket}.s3.{settings.aws_region}.amazonaws.com/{key}"
    
    # Fallback: Save locally
    filename = f"{unique_id}.{extension}"
    file_path = UPLOAD_DIR / filename
    
    try:
        content = await file.read()
        with open(file_path, "wb") as f:
            f.write(content)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to save file: {e}")
    
    # Return URL that points to our static file server
    return f"/uploads/{filename}"


async def upload_image_from_url(image_url: str, prefix: str = "logos") -> Optional[str]:
    """
    Download image from external URL and upload to S3.
    Used for EPG logos that need to be stored locally.
    
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
        
        # If S3 is configured, upload there
        if s3 and settings.s3_bucket_name:
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
                return f"https://{bucket}.s3.{settings.aws_region}.amazonaws.com/{key}"
        
        # Fallback: Save locally
        filename = f"{unique_id}.{extension}"
        file_path = UPLOAD_DIR / prefix
        file_path.mkdir(parents=True, exist_ok=True)
        file_path = file_path / filename
        
        with open(file_path, "wb") as f:
            f.write(image_data)
        
        return f"/uploads/{prefix}/{filename}"
        
    except Exception as e:
        print(f"Failed to download/upload logo from {image_url}: {e}")
        return None
