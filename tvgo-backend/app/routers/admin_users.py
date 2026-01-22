from datetime import datetime
import secrets
import string
import uuid
import csv
import io
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_company_or_admin as get_current_company, get_password_hash
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/users",
    tags=["admin-users"],
)


def _generate_credentials(length=8):
    """Generate a random alphanumeric string"""
    alphabet = string.ascii_letters + string.digits
    return ''.join(secrets.choice(alphabet) for i in range(length))


def _subscriber_document_to_schema(doc: dict) -> schemas.SubscriberResponse:
    raw_status = doc.get("status")
    status = raw_status
    if not status:
        if doc.get("is_active") is False:
            status = schemas.UserStatus.INACTIVE
        else:
            status = schemas.UserStatus.ACTIVE

    is_active_status = status in [
        schemas.UserStatus.ACTIVE, 
        schemas.UserStatus.BONUS, 
        schemas.UserStatus.TEST
    ]

    return schemas.SubscriberResponse(
        id=doc["_id"],
        username=doc.get("username"),
        password=doc.get("password_plain"),
        mac_address=doc.get("mac_address"),
        display_name=doc.get("display_name"),
        surname=doc.get("surname"),
        building=doc.get("building"),
        address=doc.get("address"),
        client_no=doc.get("client_no"),
        status=status,
        is_active=is_active_status,
        package_ids=doc.get("package_ids", []),
        max_devices=doc.get("max_devices", 1),
        devices=doc.get("devices", []),
        created_at=doc.get("created_at"),
        last_login=doc.get("last_login"),
    )


@router.get("", response_model=schemas.SubscriberListResponse)
def list_subscribers(
    skip: int = 0,
    limit: int = 50,
    search: Optional[str] = None,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    # Base query filters by company
    query = {"company_id": company["_id"]}
    
    if search:
        query["$or"] = [
            {"username": {"$regex": search, "$options": "i"}},
            {"display_name": {"$regex": search, "$options": "i"}},
            {"surname": {"$regex": search, "$options": "i"}},
            {"mac_address": {"$regex": search, "$options": "i"}},
            {"client_no": {"$regex": search, "$options": "i"}},
        ]

    total = db["subscribers"].count_documents(query)
    cursor = db["subscribers"].find(query).skip(skip).limit(limit).sort("created_at", -1)
    
    items = [_subscriber_document_to_schema(doc) for doc in cursor]
    return {"items": items, "total": total}


@router.post("/mac", response_model=schemas.SubscriberResponse)
def create_subscriber_by_mac(
    payload: schemas.SubscriberCreate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    # Check if MAC already exists in this company
    if db["subscribers"].find_one({"mac_address": payload.mac_address, "company_id": company["_id"]}):
        raise HTTPException(status_code=400, detail="Subscriber with this MAC address already exists")
    
    if payload.client_no and db["subscribers"].find_one({"client_no": payload.client_no, "company_id": company["_id"]}):
        raise HTTPException(status_code=400, detail="Subscriber with this Client ID already exists")

    subscriber_id = uuid.uuid4().hex
    now = datetime.utcnow()
    
    doc = {
        "_id": subscriber_id,
        "company_id": company["_id"],  # Link to company
        "mac_address": payload.mac_address,
        "display_name": payload.display_name or f"User-{payload.mac_address[-6:]}",
        "surname": payload.surname,
        "building": payload.building,
        "address": payload.address,
        "client_no": payload.client_no,
        "package_ids": payload.package_ids or [],
        "max_devices": payload.max_devices,
        "status": payload.status.value if payload.status else schemas.UserStatus.ACTIVE.value,
        "is_active": True, 
        "devices": [],
        "created_at": now,
        "last_login": None,
        "creation_method": "mac",
    }
    
    db["subscribers"].insert_one(doc)
    return _subscriber_document_to_schema(doc)


@router.post("/generate", response_model=schemas.SubscriberCreateResponse)
def create_subscriber_generated(
    payload: schemas.SubscriberCreateCredentials,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    if payload.client_no and db["subscribers"].find_one({"client_no": payload.client_no, "company_id": company["_id"]}):
        raise HTTPException(status_code=400, detail="Subscriber with this Client ID already exists")

    subscriber_id = uuid.uuid4().hex
    now = datetime.utcnow()
    
    # Generate unique username (globally unique for login purposes)
    while True:
        username = _generate_credentials(8)
        if not db["subscribers"].find_one({"username": username}):
            break
            
    password = _generate_credentials(8)
    password_hash = get_password_hash(password)
    
    doc = {
        "_id": subscriber_id,
        "company_id": company["_id"],  # Link to company
        "username": username,
        "password_hash": password_hash,
        "password_plain": password,
        "display_name": payload.display_name or f"User-{username}",
        "surname": payload.surname,
        "building": payload.building,
        "address": payload.address,
        "client_no": payload.client_no,
        "package_ids": payload.package_ids or [],
        "max_devices": payload.max_devices,
        "status": payload.status.value if payload.status else schemas.UserStatus.ACTIVE.value,
        "is_active": True,
        "devices": [],
        "created_at": now,
        "last_login": None,
        "creation_method": "generated",
    }
    
    db["subscribers"].insert_one(doc)
    
    return _subscriber_document_to_schema(doc)


@router.post("/import-mac", response_model=dict)
async def import_mac_users(
    file: UploadFile = File(...),
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Import users from CSV file for this company."""
    if not file.filename.endswith('.csv'):
        raise HTTPException(status_code=400, detail="Only CSV files are supported")

    content = await file.read()
    decoded_content = content.decode('utf-8')
    csv_reader = csv.DictReader(io.StringIO(decoded_content))

    results = {
        "imported": 0,
        "skipped": 0,
        "errors": []
    }

    now = datetime.utcnow()

    for row in csv_reader:
        row_lower = {k.lower().strip(): v for k, v in row.items() if k}
        
        mac = row_lower.get("mac") or row_lower.get("mac_address") or row_lower.get("mac address")
        if not mac:
            continue

        # Check existing in this company
        if db["subscribers"].find_one({"mac_address": mac, "company_id": company["_id"]}):
            results["skipped"] += 1
            continue

        subscriber_id = uuid.uuid4().hex
        
        doc = {
            "_id": subscriber_id,
            "company_id": company["_id"],  # Link to company
            "mac_address": mac,
            "display_name": row_lower.get("name") or row_lower.get("display_name") or f"User-{mac[-6:]}",
            "surname": row_lower.get("surname"),
            "building": row_lower.get("building"),
            "address": row_lower.get("address"),
            "client_no": row_lower.get("client_no") or row_lower.get("client no"),
            "package_ids": [],
            "max_devices": 1,
            "is_active": True,
            "devices": [],
            "created_at": now,
            "last_login": None,
            "creation_method": "import",
        }
        
        try:
            db["subscribers"].insert_one(doc)
            results["imported"] += 1
        except Exception as e:
            results["errors"].append(f"Error importing {mac}: {str(e)}")

    return results


@router.put("/{user_id}", response_model=schemas.SubscriberResponse)
def update_subscriber(
    user_id: str,
    payload: schemas.SubscriberUpdate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    existing = db["subscribers"].find_one({"_id": user_id, "company_id": company["_id"]})
    if not existing:
        raise HTTPException(status_code=404, detail="Subscriber not found")

    update_data = payload.dict(exclude_unset=True)
    if update_data:
        if "client_no" in update_data and update_data["client_no"]:
            conflict = db["subscribers"].find_one({"client_no": update_data["client_no"], "company_id": company["_id"]})
            if conflict and conflict["_id"] != user_id:
                raise HTTPException(status_code=400, detail="Subscriber with this Client ID already exists")
        
        if "status" in update_data:
            update_data["status"] = update_data["status"].value if hasattr(update_data["status"], 'value') else update_data["status"]
            is_active_map = {
                schemas.UserStatus.ACTIVE.value: True,
                schemas.UserStatus.BONUS.value: True,
                schemas.UserStatus.TEST.value: True,
                schemas.UserStatus.INACTIVE.value: False,
                schemas.UserStatus.EXPIRED.value: False
            }
            if update_data["status"] in is_active_map:
                update_data["is_active"] = is_active_map[update_data["status"]]

        db["subscribers"].update_one({"_id": user_id, "company_id": company["_id"]}, {"$set": update_data})
        
    updated = db["subscribers"].find_one({"_id": user_id, "company_id": company["_id"]})
    return _subscriber_document_to_schema(updated)


@router.delete("/{user_id}")
def delete_subscriber(
    user_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    result = db["subscribers"].delete_one({"_id": user_id, "company_id": company["_id"]})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Subscriber not found")
    return {"status": "ok"}


@router.delete("/{user_id}/devices/{mac_address}")
def remove_device(
    user_id: str,
    mac_address: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Remove a specific device from a user"""
    result = db["subscribers"].update_one(
        {"_id": user_id, "company_id": company["_id"]},
        {"$pull": {"devices": {"mac_address": mac_address}}}
    )
    if result.matched_count == 0:
        raise HTTPException(status_code=404, detail="Subscriber not found")
        
    return {"status": "ok"}
