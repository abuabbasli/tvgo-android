from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database
from typing import List, Optional
import time

from .. import schemas
from ..auth import get_current_company_or_admin as get_current_company
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/games",
    tags=["admin-games"],
)

GAME_CATEGORIES = [
    "Action",
    "Adventure", 
    "Arcade",
    "Puzzle",
    "Racing",
    "Sports",
    "2 Player",
    ".io Games",
    "Dress Up",
    "Simulation",
    "Kids",
    "Other"
]

# Per-company cache
_games_cache: dict[str, tuple[List[schemas.Game], float]] = {}
CACHE_TTL = 60


def _invalidate_cache(company_id: str):
    global _games_cache
    if company_id in _games_cache:
        del _games_cache[company_id]


def _game_document_to_schema(document: dict) -> schemas.Game:
    game_id = document.get("id") or document.get("_id")
    return schemas.Game(
        id=game_id,
        name=document.get("name"),
        description=document.get("description"),
        imageUrl=document.get("image_url"),
        gameUrl=document.get("game_url"),
        category=document.get("category"),
        isActive=document.get("is_active", True),
        order=document.get("order"),
    )


@router.get("/categories", response_model=List[str])
def get_game_categories():
    """Get available game categories"""
    return GAME_CATEGORIES


@router.get("", response_model=schemas.GamesListResponse)
def admin_list_games(
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    global _games_cache
    company_id = str(company["_id"])
    
    if company_id in _games_cache:
        cached, timestamp = _games_cache[company_id]
        if (time.time() - timestamp) < CACHE_TTL:
            categories = list(set(g.category for g in cached if g.category))
            return schemas.GamesListResponse(
                total=len(cached),
                items=cached,
                categories=sorted(categories)
            )
    
    games = list(db["games"].find({"company_id": company["_id"]}).sort([("order", 1), ("name", 1)]))
    games_list = [_game_document_to_schema(g) for g in games]
    _games_cache[company_id] = (games_list, time.time())
    
    categories = list(set(g.category for g in games_list if g.category))
    return schemas.GamesListResponse(
        total=len(games_list),
        items=games_list,
        categories=sorted(categories)
    )


@router.post("", response_model=schemas.Game)
def admin_create_game(
    payload: schemas.GameCreate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    existing = db["games"].find_one({"_id": payload.id, "company_id": company["_id"]})
    if existing:
        raise HTTPException(status_code=400, detail="Game with this id already exists")

    document = {
        "_id": payload.id,
        "id": payload.id,
        "company_id": company["_id"],  # Link to company
        "name": payload.name,
        "description": payload.description,
        "image_url": payload.image_url,
        "game_url": payload.game_url,
        "category": payload.category,
        "is_active": payload.is_active,
        "order": payload.order,
    }
    db["games"].insert_one(document)
    _invalidate_cache(str(company["_id"]))
    return _game_document_to_schema(document)


@router.put("/reorder")
def admin_reorder_games(
    payload: schemas.GameReorderRequest,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Batch reorder games"""
    from pymongo import UpdateOne
    
    operations = []
    for item in payload.items:
        operations.append(UpdateOne(
            {"_id": item.id, "company_id": company["_id"]},
            {"$set": {"order": item.order}}
        ))
    
    if operations:
        db["games"].bulk_write(operations)
        
    _invalidate_cache(str(company["_id"]))
    return {"status": "ok"}


@router.get("/{game_id}", response_model=schemas.Game)
def admin_get_game(
    game_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    document = db["games"].find_one({"_id": game_id, "company_id": company["_id"]})
    if not document:
        raise HTTPException(status_code=404, detail="Game not found")
    return _game_document_to_schema(document)


@router.put("/{game_id}", response_model=schemas.Game)
def admin_update_game(
    game_id: str,
    payload: schemas.GameUpdate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    update_data = payload.dict(exclude_unset=True)
    if update_data:
        result = db["games"].update_one(
            {"_id": game_id, "company_id": company["_id"]},
            {"$set": update_data}
        )
        if result.matched_count == 0:
            raise HTTPException(status_code=404, detail="Game not found")
    else:
        if not db["games"].find_one({"_id": game_id, "company_id": company["_id"]}):
            raise HTTPException(status_code=404, detail="Game not found")

    document = db["games"].find_one({"_id": game_id, "company_id": company["_id"]})
    _invalidate_cache(str(company["_id"]))
    return _game_document_to_schema(document)


@router.delete("/{game_id}")
def admin_delete_game(
    game_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    result = db["games"].delete_one({"_id": game_id, "company_id": company["_id"]})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Game not found")
    _invalidate_cache(str(company["_id"]))
    return {"status": "ok"}
