from fastapi import APIRouter, Depends
from pymongo.database import Database
from pydantic import BaseModel

from .. import schemas
from ..auth import get_current_active_admin
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/config",
    tags=["admin-config"],
    dependencies=[Depends(get_current_active_admin)],
)


class DashboardStats(BaseModel):
    total_channels: int
    active_channels: int
    inactive_channels: int
    total_streamers: int
    total_packages: int


@router.get("", response_model=schemas.ConfigResponse)
def admin_get_config(db: Database = Depends(get_db)):
    from .public import build_config_response

    return build_config_response(db)


@router.put("/brand", response_model=schemas.ConfigResponse)
def admin_update_brand(
    brand_payload: schemas.Brand,
    db: Database = Depends(get_db),
):
    document = {
        "_id": "brand",
        "app_name": brand_payload.appName,
        "logo_url": str(brand_payload.logoUrl) if brand_payload.logoUrl else None,
        "accent_color": brand_payload.accentColor,
        "background_color": brand_payload.backgroundColor,
    }

    db["brand_config"].update_one({"_id": "brand"}, {"$set": document}, upsert=True)

    from .public import build_config_response

    return build_config_response(db)


@router.put("/features", response_model=schemas.ConfigResponse)
def admin_update_features(
    features_payload: schemas.Features,
    db: Database = Depends(get_db),
):
    document = {
        "enable_favorites": features_payload.enableFavorites,
        "enable_search": features_payload.enableSearch,
        "autoplay_preview": features_payload.autoplayPreview,
        "enable_live_tv": features_payload.enableLiveTv,
        "enable_vod": features_payload.enableVod,
    }

    db["brand_config"].update_one({"_id": "brand"}, {"$set": document}, upsert=True)

    from .public import build_config_response

    return build_config_response(db)


@router.get("/stats", response_model=DashboardStats)
def get_dashboard_stats(db: Database = Depends(get_db)):
    """Get dashboard statistics."""
    total_channels = db["channels"].count_documents({})
    active_channels = db["channels"].count_documents({"is_active": True})
    total_streamers = db["streamers"].count_documents({})
    total_packages = db["packages"].count_documents({})
    
    return DashboardStats(
        total_channels=total_channels,
        active_channels=active_channels,
        inactive_channels=total_channels - active_channels,
        total_streamers=total_streamers,
        total_packages=total_packages,
    )

