from fastapi import APIRouter, Depends
from pymongo.database import Database
from pydantic import BaseModel

from .. import schemas
from ..auth import get_current_company_or_admin as get_current_company
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/config",
    tags=["admin-config"],
)


class DashboardStats(BaseModel):
    total_channels: int
    active_channels: int
    inactive_channels: int
    total_streamers: int
    total_packages: int
    total_users: int


@router.get("", response_model=schemas.ConfigResponse)
def admin_get_config(
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    from .public import build_config_response
    return build_config_response(db, company["_id"])


@router.put("/brand", response_model=schemas.ConfigResponse)
def admin_update_brand(
    brand_payload: schemas.Brand,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db),
):
    """Update brand config for this company."""
    company_id = company["_id"]
    
    document = {
        "company_id": company_id,
        "app_name": brand_payload.appName,
        "logo_url": str(brand_payload.logoUrl) if brand_payload.logoUrl else None,
        "accent_color": brand_payload.accentColor,
        "background_color": brand_payload.backgroundColor,
    }

    db["brand_config"].update_one(
        {"company_id": company_id}, 
        {"$set": document}, 
        upsert=True
    )

    from .public import build_config_response
    return build_config_response(db, company_id)


@router.put("/features", response_model=schemas.ConfigResponse)
def admin_update_features(
    features_payload: schemas.Features,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db),
):
    """Update features config for this company."""
    company_id = company["_id"]
    
    document = {
        "enable_favorites": features_payload.enableFavorites,
        "enable_search": features_payload.enableSearch,
        "autoplay_preview": features_payload.autoplayPreview,
        "enable_live_tv": features_payload.enableLiveTv,
        "enable_vod": features_payload.enableVod,
    }

    db["brand_config"].update_one(
        {"company_id": company_id}, 
        {"$set": document}, 
        upsert=True
    )

    from .public import build_config_response
    return build_config_response(db, company_id)


@router.get("/stats", response_model=DashboardStats)
def get_dashboard_stats(
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Get dashboard statistics for this company."""
    company_id = company["_id"]
    
    total_channels = db["channels"].count_documents({"company_id": company_id})
    active_channels = db["channels"].count_documents({"company_id": company_id, "is_active": {"$ne": False}})
    total_streamers = db["streamers"].count_documents({"company_id": company_id})
    total_packages = db["packages"].count_documents({"company_id": company_id})
    total_users = db["subscribers"].count_documents({"company_id": company_id})
    
    return DashboardStats(
        total_channels=total_channels,
        active_channels=active_channels,
        inactive_channels=total_channels - active_channels,
        total_streamers=total_streamers,
        total_packages=total_packages,
        total_users=total_users,
    )
