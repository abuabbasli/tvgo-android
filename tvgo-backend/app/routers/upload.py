from fastapi import APIRouter, Depends, File, UploadFile

from ..auth import get_current_active_admin
from ..aws_s3 import upload_image_to_s3
from ..config import settings

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin",
    tags=["admin-upload"],
    dependencies=[Depends(get_current_active_admin)],
)


@router.post("/upload-image")
async def admin_upload_image(file: UploadFile = File(...)):
    url = await upload_image_to_s3(file)
    return {"url": url}
