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

