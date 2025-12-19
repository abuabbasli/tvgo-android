from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_active_admin
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/streamers",
    tags=["admin-streamers"],
    dependencies=[Depends(get_current_active_admin)],
)


def _streamer_document_to_schema(document: dict, db: Database) -> schemas.StreamerResponse:
    streamer_id = document.get("_id") or document.get("id")
    # Count channels associated with this streamer
    channel_count = db["channels"].count_documents({"metadata.streamer_name": document.get("name")})
    return schemas.StreamerResponse(
        id=str(streamer_id),
        name=document.get("name", ""),
        url=document.get("url", ""),
        status=document.get("status", "disconnected"),
        last_sync=document.get("last_sync"),
        channel_count=channel_count,
    )


@router.get("", response_model=schemas.StreamerListResponse)
def list_streamers(db: Database = Depends(get_db)):
    """List all configured streamers."""
    streamers = list(db["streamers"].find())
    items = [_streamer_document_to_schema(s, db) for s in streamers]
    return schemas.StreamerListResponse(items=items, total=len(items))


@router.post("", response_model=schemas.StreamerResponse)
def create_streamer(payload: schemas.StreamerCreate, db: Database = Depends(get_db)):
    """Create a new streamer configuration."""
    # Generate a simple ID from the name
    import re
    streamer_id = re.sub(r"[^a-z0-9]+", "-", payload.name.lower()).strip("-")
    
    existing = db["streamers"].find_one({"_id": streamer_id})
    if existing:
        raise HTTPException(status_code=400, detail="Streamer with this name already exists")
    
    document = {
        "_id": streamer_id,
        "name": payload.name,
        "url": payload.url,
        "status": "connected",
        "last_sync": None,
        "created_at": datetime.utcnow(),
    }
    db["streamers"].insert_one(document)
    return _streamer_document_to_schema(document, db)


@router.get("/{streamer_id}", response_model=schemas.StreamerResponse)
def get_streamer(streamer_id: str, db: Database = Depends(get_db)):
    """Get a specific streamer by ID."""
    document = db["streamers"].find_one({"_id": streamer_id})
    if not document:
        raise HTTPException(status_code=404, detail="Streamer not found")
    return _streamer_document_to_schema(document, db)


@router.put("/{streamer_id}", response_model=schemas.StreamerResponse)
def update_streamer(
    streamer_id: str,
    payload: schemas.StreamerCreate,
    db: Database = Depends(get_db),
):
    """Update an existing streamer."""
    result = db["streamers"].update_one(
        {"_id": streamer_id},
        {"$set": {"name": payload.name, "url": payload.url}},
    )
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="Streamer not found")
    
    document = db["streamers"].find_one({"_id": streamer_id})
    return _streamer_document_to_schema(document, db)


@router.delete("/{streamer_id}")
def delete_streamer(streamer_id: str, db: Database = Depends(get_db)):
    """Delete a streamer."""
    document = db["streamers"].find_one({"_id": streamer_id})
    if not document:
        raise HTTPException(status_code=404, detail="Streamer not found")
    
    # Cascade delete channels
    streamer_name = document.get("name")
    if streamer_name:
        db["channels"].delete_many({
            "$or": [
                {"streamer_name": streamer_name},
                {"metadata.streamer_name": streamer_name}
            ]
        })
        
    result = db["streamers"].delete_one({"_id": streamer_id})
    
    # Invalidate channel cache
    try:
        from . import admin_channels
        admin_channels._invalidate_cache()
    except ImportError:
        pass # Should not happen, but safe to ignore if structure differs
        
    return {"status": "ok"}


@router.post("/{streamer_id}/sync")
def sync_streamer(streamer_id: str, db: Database = Depends(get_db)):
    """Mark a streamer as synced (update last_sync timestamp)."""
    result = db["streamers"].update_one(
        {"_id": streamer_id},
        {"$set": {"last_sync": datetime.utcnow(), "status": "connected"}},
    )
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="Streamer not found")
    
    document = db["streamers"].find_one({"_id": streamer_id})
    return _streamer_document_to_schema(document, db)
