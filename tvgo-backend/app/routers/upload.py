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
    try:
        print(f"Starting image upload: {file.filename}, content_type: {file.content_type}")
        url = await upload_image_to_s3(file)
        print(f"Upload successful: {url}")
        return {"url": url}
    except Exception as e:
        import traceback
        error_details = traceback.format_exc()
        print(f"Upload failed: {str(e)}")
        print(f"Full traceback: {error_details}")
        from fastapi import HTTPException
        raise HTTPException(status_code=500, detail=f"Upload failed: {str(e)}")

