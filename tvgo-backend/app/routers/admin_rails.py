from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_active_admin
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/rails",
    tags=["admin-rails"],
    dependencies=[Depends(get_current_active_admin)],
)


def _rail_document_to_schema(document: dict) -> schemas.RailPublic:
    rail_id = document.get("id") or document.get("_id")
    return schemas.RailPublic(
        id=rail_id,
        title=document.get("title"),
        type=document.get("type"),
        query=document.get("query") or {},
    )


@router.get("", response_model=list[schemas.RailPublic])
def admin_list_rails(db: Database = Depends(get_db)):
    rails = list(db["rails"].find().sort("sort_order", 1))
    return [_rail_document_to_schema(r) for r in rails]


@router.post("", response_model=schemas.RailPublic)
def admin_create_rail(payload: schemas.RailAdminCreate, db: Database = Depends(get_db)):
    existing = db["rails"].find_one({"_id": payload.id})
    if existing:
        raise HTTPException(status_code=400, detail="Rail with this id already exists")

    document = {
        "_id": payload.id,
        "id": payload.id,
        "title": payload.title,
        "type": payload.type,
        "query": payload.query,
        "sort_order": payload.sort_order,
    }
    db["rails"].insert_one(document)
    return _rail_document_to_schema(document)


@router.put("/{rail_id}", response_model=schemas.RailPublic)
def admin_update_rail(rail_id: str, payload: schemas.RailAdminUpdate, db: Database = Depends(get_db)):
    update_data = payload.dict(exclude_unset=True)
    if update_data:
        result = db["rails"].update_one({"_id": rail_id}, {"$set": update_data})
        if result.matched_count == 0:
            raise HTTPException(status_code=404, detail="Rail not found")
    else:
        if not db["rails"].find_one({"_id": rail_id}):
            raise HTTPException(status_code=404, detail="Rail not found")

    document = db["rails"].find_one({"_id": rail_id})
    return _rail_document_to_schema(document)


@router.delete("/{rail_id}")
def admin_delete_rail(rail_id: str, db: Database = Depends(get_db)):
    result = db["rails"].delete_one({"_id": rail_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Rail not found")
    return {"status": "ok"}
