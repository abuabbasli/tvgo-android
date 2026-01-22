"""Channel Packages management router with multi-tenant support."""
from datetime import datetime
from typing import List, Optional

from bson import ObjectId
from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database

from ..auth import get_current_company_or_admin as get_current_company
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/packages",
    tags=["admin-packages"],
)


from pydantic import BaseModel


class PackageCreate(BaseModel):
    name: str
    description: Optional[str] = None
    price: Optional[str] = None
    channel_ids: List[str] = []


class PackageUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    price: Optional[str] = None
    channel_ids: Optional[List[str]] = None


class PackageResponse(BaseModel):
    id: str
    name: str
    description: Optional[str] = None
    price: Optional[str] = None
    channel_ids: List[str] = []
    created_at: datetime
    updated_at: Optional[datetime] = None


class PackageListResponse(BaseModel):
    packages: List[PackageResponse]
    total: int


def package_doc_to_response(doc: dict) -> PackageResponse:
    return PackageResponse(
        id=str(doc["_id"]),
        name=doc.get("name", ""),
        description=doc.get("description"),
        price=doc.get("price"),
        channel_ids=doc.get("channel_ids", []),
        created_at=doc.get("created_at", datetime.utcnow()),
        updated_at=doc.get("updated_at"),
    )


@router.get("", response_model=PackageListResponse)
def list_packages(
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """List all channel packages for this company."""
    cursor = db["packages"].find({"company_id": company["_id"]}).sort("created_at", -1)
    packages = [package_doc_to_response(doc) for doc in cursor]
    return PackageListResponse(packages=packages, total=len(packages))


@router.get("/{package_id}", response_model=PackageResponse)
def get_package(
    package_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Get a single package by ID."""
    try:
        doc = db["packages"].find_one({"_id": ObjectId(package_id), "company_id": company["_id"]})
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid package ID")
    
    if not doc:
        raise HTTPException(status_code=404, detail="Package not found")
    
    return package_doc_to_response(doc)


@router.post("", response_model=PackageResponse, status_code=201)
def create_package(
    payload: PackageCreate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Create a new channel package."""
    now = datetime.utcnow()
    document = {
        "company_id": company["_id"],  # Link to company
        "name": payload.name,
        "description": payload.description,
        "price": payload.price,
        "channel_ids": payload.channel_ids,
        "created_at": now,
        "updated_at": now,
    }
    result = db["packages"].insert_one(document)
    document["_id"] = result.inserted_id
    return package_doc_to_response(document)


@router.put("/{package_id}", response_model=PackageResponse)
def update_package(
    package_id: str,
    payload: PackageUpdate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db),
):
    """Update an existing package."""
    try:
        oid = ObjectId(package_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid package ID")
    
    existing = db["packages"].find_one({"_id": oid, "company_id": company["_id"]})
    if not existing:
        raise HTTPException(status_code=404, detail="Package not found")
    
    update_data = {"updated_at": datetime.utcnow()}
    if payload.name is not None:
        update_data["name"] = payload.name
    if payload.description is not None:
        update_data["description"] = payload.description
    if payload.price is not None:
        update_data["price"] = payload.price
    if payload.channel_ids is not None:
        update_data["channel_ids"] = payload.channel_ids
    
    db["packages"].update_one({"_id": oid, "company_id": company["_id"]}, {"$set": update_data})
    updated_doc = db["packages"].find_one({"_id": oid, "company_id": company["_id"]})
    return package_doc_to_response(updated_doc)


@router.delete("/{package_id}", status_code=204)
def delete_package(
    package_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Delete a package."""
    try:
        oid = ObjectId(package_id)
    except Exception:
        raise HTTPException(status_code=400, detail="Invalid package ID")
    
    result = db["packages"].delete_one({"_id": oid, "company_id": company["_id"]})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Package not found")
    
    return None
