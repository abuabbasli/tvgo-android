"""Super Admin router for company (tenant) management."""
import re
from datetime import datetime
from typing import Optional
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database

from .. import schemas
from ..auth import get_super_admin, get_password_hash
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/super-admin",
    tags=["super-admin"],
    dependencies=[Depends(get_super_admin)],
)


def _company_document_to_response(doc: dict, db: Database) -> schemas.CompanyResponse:
    """Convert MongoDB document to CompanyResponse schema."""
    company_id = doc.get("_id") or doc.get("id")
    
    # Get counts for this company
    user_count = db["subscribers"].count_documents({"company_id": company_id})
    channel_count = db["channels"].count_documents({"company_id": company_id})
    movie_count = db["movies"].count_documents({"company_id": company_id})
    
    # Parse services
    services_data = doc.get("services", {})
    services = schemas.CompanyServices(
        enable_vod=services_data.get("enable_vod", True),
        enable_channels=services_data.get("enable_channels", True),
        enable_games=services_data.get("enable_games", False),
        enable_messaging=services_data.get("enable_messaging", False),
    )
    
    return schemas.CompanyResponse(
        id=str(company_id),
        name=doc.get("name", ""),
        slug=doc.get("slug", ""),
        username=doc.get("username", ""),
        is_active=doc.get("is_active", True),
        services=services,
        created_at=doc.get("created_at"),
        user_count=user_count,
        channel_count=channel_count,
        movie_count=movie_count,
    )


@router.get("/companies", response_model=schemas.CompanyListResponse)
def list_companies(
    skip: int = 0,
    limit: int = 50,
    search: Optional[str] = None,
    db: Database = Depends(get_db),
):
    """List all companies (tenants)."""
    query = {}
    if search:
        query["$or"] = [
            {"name": {"$regex": search, "$options": "i"}},
            {"username": {"$regex": search, "$options": "i"}},
        ]
    
    total = db["companies"].count_documents(query)
    cursor = db["companies"].find(query).skip(skip).limit(limit).sort("created_at", -1)
    
    items = [_company_document_to_response(doc, db) for doc in cursor]
    return schemas.CompanyListResponse(items=items, total=total)


@router.get("/companies/{company_id}", response_model=schemas.CompanyResponse)
def get_company(company_id: str, db: Database = Depends(get_db)):
    """Get a specific company by ID."""
    doc = db["companies"].find_one({"_id": company_id})
    if not doc:
        raise HTTPException(status_code=404, detail="Company not found")
    return _company_document_to_response(doc, db)


@router.post("/companies", response_model=schemas.CompanyResponse)
def create_company(payload: schemas.CompanyCreate, db: Database = Depends(get_db)):
    """Create a new company (tenant)."""
    # Generate slug from name if not provided or validate existing
    slug = re.sub(r"[^a-z0-9]+", "-", payload.slug.lower()).strip("-")
    
    # Check for duplicate username or slug
    existing = db["companies"].find_one({
        "$or": [
            {"username": payload.username},
            {"slug": slug},
        ]
    })
    if existing:
        if existing.get("username") == payload.username:
            raise HTTPException(status_code=400, detail="Username already exists")
        raise HTTPException(status_code=400, detail="Slug already exists")
    
    company_id = str(uuid4())
    document = {
        "_id": company_id,
        "name": payload.name,
        "slug": slug,
        "username": payload.username,
        "password_hash": get_password_hash(payload.password),
        "is_active": True,
        "services": payload.services.model_dump(),
        "created_at": datetime.utcnow(),
    }
    
    db["companies"].insert_one(document)
    return _company_document_to_response(document, db)


@router.put("/companies/{company_id}", response_model=schemas.CompanyResponse)
def update_company(
    company_id: str,
    payload: schemas.CompanyUpdate,
    db: Database = Depends(get_db),
):
    """Update an existing company."""
    doc = db["companies"].find_one({"_id": company_id})
    if not doc:
        raise HTTPException(status_code=404, detail="Company not found")
    
    update_data = {}
    if payload.name is not None:
        update_data["name"] = payload.name
    if payload.is_active is not None:
        update_data["is_active"] = payload.is_active
    if payload.services is not None:
        update_data["services"] = payload.services.model_dump()
    
    if update_data:
        db["companies"].update_one({"_id": company_id}, {"$set": update_data})
    
    updated_doc = db["companies"].find_one({"_id": company_id})
    return _company_document_to_response(updated_doc, db)


@router.delete("/companies/{company_id}")
def delete_company(company_id: str, db: Database = Depends(get_db)):
    """Delete a company and all its associated data."""
    doc = db["companies"].find_one({"_id": company_id})
    if not doc:
        raise HTTPException(status_code=404, detail="Company not found")
    
    # Delete associated data
    db["subscribers"].delete_many({"company_id": company_id})
    db["channels"].delete_many({"company_id": company_id})
    db["movies"].delete_many({"company_id": company_id})
    db["games"].delete_many({"company_id": company_id})
    db["messages"].delete_many({"company_id": company_id})
    db["streamers"].delete_many({"company_id": company_id})
    db["packages"].delete_many({"company_id": company_id})
    db["company_refresh_tokens"].delete_many({"company_id": company_id})
    
    # Delete the company itself
    db["companies"].delete_one({"_id": company_id})
    
    return {"status": "ok", "message": f"Company {doc.get('name')} deleted"}


@router.post("/companies/{company_id}/reset-password", response_model=schemas.CompanyResponse)
def reset_company_password(
    company_id: str,
    new_password: str,
    db: Database = Depends(get_db),
):
    """Reset a company's password."""
    doc = db["companies"].find_one({"_id": company_id})
    if not doc:
        raise HTTPException(status_code=404, detail="Company not found")
    
    db["companies"].update_one(
        {"_id": company_id},
        {"$set": {"password_hash": get_password_hash(new_password)}}
    )
    
    # Also revoke all refresh tokens for this company
    db["company_refresh_tokens"].delete_many({"company_id": company_id})
    
    updated_doc = db["companies"].find_one({"_id": company_id})
    return _company_document_to_response(updated_doc, db)


@router.get("/stats")
def get_super_admin_stats(db: Database = Depends(get_db)):
    """Get overall statistics for super admin dashboard."""
    return {
        "total_companies": db["companies"].count_documents({}),
        "active_companies": db["companies"].count_documents({"is_active": True}),
        "total_users": db["subscribers"].count_documents({}),
        "total_channels": db["channels"].count_documents({}),
        "total_movies": db["movies"].count_documents({}),
    }
