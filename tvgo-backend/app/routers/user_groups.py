from datetime import datetime
import uuid
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_active_admin
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/user-groups",
    tags=["admin-user-groups"],
    dependencies=[Depends(get_current_active_admin)],
)


def _group_doc_to_response(doc: dict) -> schemas.UserGroupResponse:
    """Convert MongoDB document to UserGroupResponse"""
    return schemas.UserGroupResponse(
        id=doc["_id"],
        name=doc["name"],
        description=doc.get("description"),
        user_ids=doc.get("user_ids", []),
        user_count=len(doc.get("user_ids", [])),
        created_at=doc.get("created_at"),
        updated_at=doc.get("updated_at"),
    )


@router.get("", response_model=schemas.UserGroupListResponse)
def list_user_groups(
    skip: int = 0,
    limit: int = 100,
    search: Optional[str] = None,
    db: Database = Depends(get_db)
):
    """List all user groups"""
    query = {}
    if search:
        query["name"] = {"$regex": search, "$options": "i"}

    total = db["user_groups"].count_documents(query)
    cursor = db["user_groups"].find(query).skip(skip).limit(limit).sort("created_at", -1)
    
    items = [_group_doc_to_response(doc) for doc in cursor]
    return {"items": items, "total": total}


@router.get("/{group_id}", response_model=schemas.UserGroupResponse)
def get_user_group(group_id: str, db: Database = Depends(get_db)):
    """Get a single user group by ID"""
    doc = db["user_groups"].find_one({"_id": group_id})
    if not doc:
        raise HTTPException(status_code=404, detail="User group not found")
    return _group_doc_to_response(doc)


@router.post("", response_model=schemas.UserGroupResponse)
def create_user_group(
    payload: schemas.UserGroupCreate,
    db: Database = Depends(get_db)
):
    """Create a new user group"""
    # Check for duplicate name
    if db["user_groups"].find_one({"name": payload.name}):
        raise HTTPException(status_code=400, detail="A group with this name already exists")

    group_id = uuid.uuid4().hex
    now = datetime.utcnow()
    
    doc = {
        "_id": group_id,
        "name": payload.name,
        "description": payload.description,
        "user_ids": payload.user_ids,
        "created_at": now,
        "updated_at": now,
    }
    
    db["user_groups"].insert_one(doc)
    return _group_doc_to_response(doc)


@router.put("/{group_id}", response_model=schemas.UserGroupResponse)
def update_user_group(
    group_id: str,
    payload: schemas.UserGroupUpdate,
    db: Database = Depends(get_db)
):
    """Update a user group"""
    existing = db["user_groups"].find_one({"_id": group_id})
    if not existing:
        raise HTTPException(status_code=404, detail="User group not found")

    update_data = payload.dict(exclude_unset=True)
    
    if "name" in update_data:
        # Check name uniqueness (excluding current group)
        conflict = db["user_groups"].find_one({"name": update_data["name"]})
        if conflict and conflict["_id"] != group_id:
            raise HTTPException(status_code=400, detail="A group with this name already exists")

    if update_data:
        update_data["updated_at"] = datetime.utcnow()
        db["user_groups"].update_one({"_id": group_id}, {"$set": update_data})

    updated = db["user_groups"].find_one({"_id": group_id})
    return _group_doc_to_response(updated)


@router.delete("/{group_id}")
def delete_user_group(group_id: str, db: Database = Depends(get_db)):
    """Delete a user group"""
    result = db["user_groups"].delete_one({"_id": group_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="User group not found")
    return {"status": "ok", "message": "Group deleted"}


@router.get("/{group_id}/users", response_model=schemas.SubscriberListResponse)
def get_group_users(
    group_id: str,
    skip: int = 0,
    limit: int = 50,
    db: Database = Depends(get_db)
):
    """Get all users (subscribers) in a group"""
    group = db["user_groups"].find_one({"_id": group_id})
    if not group:
        raise HTTPException(status_code=404, detail="User group not found")

    user_ids = group.get("user_ids", [])
    if not user_ids:
        return {"items": [], "total": 0}

    # Fetch subscribers
    cursor = db["subscribers"].find({"_id": {"$in": user_ids}}).skip(skip).limit(limit)
    
    # Import the helper from admin_users
    from .admin_users import _subscriber_document_to_schema
    
    items = [_subscriber_document_to_schema(doc) for doc in cursor]
    total = db["subscribers"].count_documents({"_id": {"$in": user_ids}})
    
    return {"items": items, "total": total}
