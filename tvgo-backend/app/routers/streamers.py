from datetime import datetime
from typing import Optional
import re

from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_company_or_admin as get_current_company
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/streamers",
    tags=["admin-streamers"],
)


def _streamer_document_to_schema(document: dict, db: Database, company_id) -> schemas.StreamerResponse:
    streamer_id = document.get("_id") or document.get("id")
    # Count channels associated with this streamer (for this company)
    channel_count = db["channels"].count_documents({
        "company_id": company_id,
        "metadata.streamer_name": document.get("name")
    })
    return schemas.StreamerResponse(
        id=str(streamer_id),
        name=document.get("name", ""),
        url=document.get("url", ""),
        status=document.get("status", "disconnected"),
        last_sync=document.get("last_sync"),
        channel_count=channel_count,
    )


@router.get("", response_model=schemas.StreamerListResponse)
def list_streamers(
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """List all configured streamers for this company."""
    streamers = list(db["streamers"].find({"company_id": company["_id"]}))
    items = [_streamer_document_to_schema(s, db, company["_id"]) for s in streamers]
    return schemas.StreamerListResponse(items=items, total=len(items))


@router.post("", response_model=schemas.StreamerResponse)
def create_streamer(
    payload: schemas.StreamerCreate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Create a new streamer configuration."""
    streamer_id = re.sub(r"[^a-z0-9]+", "-", payload.name.lower()).strip("-")
    
    # Check for existing in this company
    existing = db["streamers"].find_one({"_id": streamer_id, "company_id": company["_id"]})
    if existing:
        raise HTTPException(status_code=400, detail="Streamer with this name already exists")
    
    document = {
        "_id": streamer_id,
        "company_id": company["_id"],  # Link to company
        "name": payload.name,
        "url": payload.url,
        "status": "connected",
        "last_sync": None,
        "created_at": datetime.utcnow(),
    }
    db["streamers"].insert_one(document)
    return _streamer_document_to_schema(document, db, company["_id"])


@router.get("/{streamer_id}", response_model=schemas.StreamerResponse)
def get_streamer(
    streamer_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Get a specific streamer by ID."""
    document = db["streamers"].find_one({"_id": streamer_id, "company_id": company["_id"]})
    if not document:
        raise HTTPException(status_code=404, detail="Streamer not found")
    return _streamer_document_to_schema(document, db, company["_id"])


@router.put("/{streamer_id}", response_model=schemas.StreamerResponse)
def update_streamer(
    streamer_id: str,
    payload: schemas.StreamerCreate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db),
):
    """Update an existing streamer."""
    result = db["streamers"].update_one(
        {"_id": streamer_id, "company_id": company["_id"]},
        {"$set": {"name": payload.name, "url": payload.url}},
    )
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="Streamer not found")
    
    document = db["streamers"].find_one({"_id": streamer_id, "company_id": company["_id"]})
    return _streamer_document_to_schema(document, db, company["_id"])


@router.delete("/{streamer_id}")
def delete_streamer(
    streamer_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Delete a streamer and its channels."""
    document = db["streamers"].find_one({"_id": streamer_id, "company_id": company["_id"]})
    if not document:
        raise HTTPException(status_code=404, detail="Streamer not found")
    
    # Cascade delete channels for this company
    streamer_name = document.get("name")
    if streamer_name:
        db["channels"].delete_many({
            "company_id": company["_id"],
            "$or": [
                {"streamer_name": streamer_name},
                {"metadata.streamer_name": streamer_name}
            ]
        })
        
    db["streamers"].delete_one({"_id": streamer_id, "company_id": company["_id"]})
        
    return {"status": "ok"}


@router.post("/{streamer_id}/sync")
def sync_streamer(
    streamer_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Mark a streamer as synced."""
    result = db["streamers"].update_one(
        {"_id": streamer_id, "company_id": company["_id"]},
        {"$set": {"last_sync": datetime.utcnow(), "status": "connected"}},
    )
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="Streamer not found")
    
    document = db["streamers"].find_one({"_id": streamer_id, "company_id": company["_id"]})
    return _streamer_document_to_schema(document, db, company["_id"])
