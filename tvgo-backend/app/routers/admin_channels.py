from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database
from typing import List, Optional
import time

from .. import schemas
from ..auth import get_current_active_admin
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/channels",
    tags=["admin-channels"],
    dependencies=[Depends(get_current_active_admin)],
)

# Simple in-memory cache for channels
_channels_cache: Optional[List[schemas.Channel]] = None
_cache_timestamp: float = 0
CACHE_TTL = 60  # Cache for 60 seconds


def _invalidate_cache():
    global _channels_cache, _cache_timestamp
    _channels_cache = None
    _cache_timestamp = 0


def _channel_document_to_schema(document: dict) -> schemas.Channel:
    channel_id = document.get("id") or document.get("_id")
    drm = None
    if document.get("drm_type") and document.get("drm_license_url"):
        drm = {"type": document["drm_type"], "licenseUrl": document["drm_license_url"]}
    return schemas.Channel(
        id=channel_id,
        name=document.get("name"),
        group=document.get("group"),
        logo=document.get("logo_url"),
        streamUrl=document.get("stream_url"),
        drm=drm,
        lang=document.get("lang"),
        country=document.get("country"),
        badges=document.get("badges"),
        metadata=document.get("metadata"),
        programSchedule=document.get("programSchedule"),
        streamerName=document.get("streamer_name"),
        order=document.get("order"),
    )


@router.get("", response_model=list[schemas.Channel])
def admin_list_channels(db: Database = Depends(get_db)):
    global _channels_cache, _cache_timestamp
    
    # Return cached result if still valid
    if _channels_cache is not None and (time.time() - _cache_timestamp) < CACHE_TTL:
        return _channels_cache
    
    # Fetch from database and cache
    # Sort by order (asc) then name (asc)
    channels = list(db["channels"].find().sort([("order", 1), ("name", 1)]))
    _channels_cache = [_channel_document_to_schema(ch) for ch in channels]
    _cache_timestamp = time.time()
    return _channels_cache


@router.post("", response_model=schemas.Channel)
def admin_create_channel(payload: schemas.ChannelCreate, db: Database = Depends(get_db)):
    existing = db["channels"].find_one({"_id": payload.id})
    if existing:
        raise HTTPException(status_code=400, detail="Channel with this id already exists")

    document = {
        "_id": payload.id,
        "id": payload.id,
        "name": payload.name,
        "group": payload.group,
        "logo_url": str(payload.logo_url) if payload.logo_url else None,
        "stream_url": str(payload.stream_url),
        "drm_type": payload.drm_type,
        "drm_license_url": str(payload.drm_license_url) if payload.drm_license_url else None,
        "lang": payload.lang,
        "country": payload.country,
        "badges": payload.badges,
        "metadata": payload.metadata,
        "streamer_name": payload.streamer_name,
        "order": payload.order,
    }
    db["channels"].insert_one(document)
    _invalidate_cache()
    return _channel_document_to_schema(document)


@router.put("/reorder")
def admin_reorder_channels(payload: schemas.ChannelReorderRequest, db: Database = Depends(get_db)):
    """Batch reorder channels"""
    from pymongo import UpdateOne
    
    operations = []
    for item in payload.items:
        operations.append(UpdateOne({"_id": item.id}, {"$set": {"order": item.order}}))
    
    if operations:
        db["channels"].bulk_write(operations)
        
    _invalidate_cache()
    return {"status": "ok"}


@router.put("/{channel_id}", response_model=schemas.Channel)
def admin_update_channel(channel_id: str, payload: schemas.ChannelUpdate, db: Database = Depends(get_db)):
    update_data = payload.dict(exclude_unset=True)
    if update_data:
        update_fields: dict[str, object] = {}
        for field, value in update_data.items():
            if field.endswith("_url") and value is not None:
                update_fields[field] = str(value)
            else:
                update_fields[field] = value

        result = db["channels"].update_one({"_id": channel_id}, {"$set": update_fields})
        if result.matched_count == 0:
            raise HTTPException(status_code=404, detail="Channel not found")
    else:
        if not db["channels"].find_one({"_id": channel_id}):
            raise HTTPException(status_code=404, detail="Channel not found")

    document = db["channels"].find_one({"_id": channel_id})
    _invalidate_cache()
    return _channel_document_to_schema(document)


@router.delete("/{channel_id}")
def admin_delete_channel(channel_id: str, db: Database = Depends(get_db)):
    result = db["channels"].delete_one({"_id": channel_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Channel not found")
    _invalidate_cache()
    return {"status": "ok"}
