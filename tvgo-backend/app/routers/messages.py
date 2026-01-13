from datetime import datetime
import uuid
from typing import Optional, List

from fastapi import APIRouter, Depends, HTTPException, Query
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_active_admin, get_current_subscriber
from ..config import settings
from ..database import get_db

# Admin router for message management
admin_router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/messages",
    tags=["admin-messages"],
    dependencies=[Depends(get_current_active_admin)],
)

# Public router for subscribers to fetch their messages
public_router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/messages",
    tags=["messages"],
)


def _message_doc_to_response(doc: dict) -> schemas.MessageResponse:
    """Convert MongoDB document to MessageResponse"""
    return schemas.MessageResponse(
        id=doc["_id"],
        title=doc["title"],
        body=doc["body"],
        url=doc.get("url"),
        target_type=doc.get("target_type", "all"),
        target_ids=doc.get("target_ids", []),
        is_active=doc.get("is_active", True),
        created_at=doc.get("created_at"),
        read_by=doc.get("read_by", []),
    )


def _message_doc_to_subscriber_response(doc: dict, subscriber_id: str) -> schemas.SubscriberMessageResponse:
    """Convert MongoDB document to SubscriberMessageResponse"""
    read_by = doc.get("read_by", [])
    return schemas.SubscriberMessageResponse(
        id=doc["_id"],
        title=doc["title"],
        body=doc["body"],
        url=doc.get("url"),
        created_at=doc.get("created_at"),
        is_read=subscriber_id in read_by,
    )


# ============ Admin Endpoints ============

@admin_router.get("", response_model=schemas.MessageListResponse)
def list_messages(
    skip: int = 0,
    limit: int = 50,
    active_only: bool = False,
    db: Database = Depends(get_db)
):
    """List all messages (admin view)"""
    query = {}
    if active_only:
        query["is_active"] = True

    total = db["messages"].count_documents(query)
    cursor = db["messages"].find(query).skip(skip).limit(limit).sort("created_at", -1)
    
    items = [_message_doc_to_response(doc) for doc in cursor]
    return {"items": items, "total": total}


@admin_router.get("/{message_id}", response_model=schemas.MessageResponse)
def get_message(message_id: str, db: Database = Depends(get_db)):
    """Get a single message by ID"""
    doc = db["messages"].find_one({"_id": message_id})
    if not doc:
        raise HTTPException(status_code=404, detail="Message not found")
    return _message_doc_to_response(doc)


@admin_router.post("", response_model=schemas.MessageResponse)
def send_message(
    payload: schemas.MessageCreate,
    db: Database = Depends(get_db)
):
    """Send a new message to users/groups"""
    message_id = uuid.uuid4().hex
    now = datetime.utcnow()
    
    # Resolve target user IDs based on target_type
    resolved_user_ids = []
    
    if payload.target_type == schemas.MessageTargetType.ALL:
        # Will be resolved at query time for subscribers
        pass
    elif payload.target_type == schemas.MessageTargetType.GROUPS:
        # Fetch all users from the specified groups
        if payload.target_ids:
            groups = db["user_groups"].find({"_id": {"$in": payload.target_ids}})
            for group in groups:
                resolved_user_ids.extend(group.get("user_ids", []))
            resolved_user_ids = list(set(resolved_user_ids))  # Deduplicate
    elif payload.target_type == schemas.MessageTargetType.USERS:
        resolved_user_ids = payload.target_ids
    
    doc = {
        "_id": message_id,
        "title": payload.title,
        "body": payload.body,
        "url": payload.url,
        "target_type": payload.target_type.value,
        "target_ids": payload.target_ids,  # Original IDs (group or user)
        "resolved_user_ids": resolved_user_ids,  # Resolved user IDs for quick lookup
        "is_active": True,
        "created_at": now,
        "read_by": [],
    }
    
    db["messages"].insert_one(doc)
    return _message_doc_to_response(doc)


@admin_router.delete("/{message_id}")
def delete_message(
    message_id: str,
    hard_delete: bool = False,
    db: Database = Depends(get_db)
):
    """Delete or deactivate a message"""
    existing = db["messages"].find_one({"_id": message_id})
    if not existing:
        raise HTTPException(status_code=404, detail="Message not found")

    if hard_delete:
        db["messages"].delete_one({"_id": message_id})
        return {"status": "ok", "message": "Message permanently deleted"}
    else:
        db["messages"].update_one({"_id": message_id}, {"$set": {"is_active": False}})
        return {"status": "ok", "message": "Message deactivated"}


# ============ Subscriber Endpoints ============

@public_router.get("", response_model=schemas.SubscriberMessagesListResponse)
def get_subscriber_messages(
    skip: int = 0,
    limit: int = 50,
    db: Database = Depends(get_db),
    current_user: dict = Depends(get_current_subscriber)
):
    """Get messages for the current subscriber"""
    subscriber_id = current_user["_id"]
    
    # Get user's groups
    user_groups = db["user_groups"].find({"user_ids": subscriber_id})
    user_group_ids = [g["_id"] for g in user_groups]
    
    # Build query: messages that target this user
    # 1. target_type == "all" (broadcast to everyone)
    # 2. target_type == "users" and subscriber_id in resolved_user_ids
    # 3. target_type == "groups" and any of user's groups in target_ids
    query = {
        "is_active": True,
        "$or": [
            {"target_type": "all"},
            {"target_type": "users", "resolved_user_ids": subscriber_id},
            {"target_type": "groups", "target_ids": {"$in": user_group_ids}},
        ]
    }
    
    total = db["messages"].count_documents(query)
    cursor = db["messages"].find(query).skip(skip).limit(limit).sort("created_at", -1)
    
    items = []
    unread_count = 0
    for doc in cursor:
        msg = _message_doc_to_subscriber_response(doc, subscriber_id)
        items.append(msg)
        if not msg.is_read:
            unread_count += 1
    
    return {"items": items, "total": total, "unread_count": unread_count}


@public_router.get("/broadcast", response_model=schemas.SubscriberMessagesListResponse)
def get_broadcast_messages(
    skip: int = 0,
    limit: int = 50,
    db: Database = Depends(get_db)
):
    """Get broadcast messages (target_type=all) - no auth required for TV apps"""
    query = {
        "is_active": True,
        "target_type": "all"
    }
    
    total = db["messages"].count_documents(query)
    cursor = db["messages"].find(query).skip(skip).limit(limit).sort("created_at", -1)
    
    items = []
    for doc in cursor:
        items.append(schemas.SubscriberMessageResponse(
            id=doc["_id"],
            title=doc["title"],
            body=doc["body"],
            url=doc.get("url"),
            created_at=doc.get("created_at"),
            is_read=False
        ))
    
    return {"items": items, "total": total, "unread_count": total}


@public_router.post("/{message_id}/read")
def mark_message_read(
    message_id: str,
    db: Database = Depends(get_db),
    current_user: dict = Depends(get_current_subscriber)
):
    """Mark a message as read for the current subscriber"""
    subscriber_id = current_user["_id"]
    
    result = db["messages"].update_one(
        {"_id": message_id},
        {"$addToSet": {"read_by": subscriber_id}}
    )
    
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="Message not found")
    
    return {"status": "ok", "message": "Message marked as read"}

