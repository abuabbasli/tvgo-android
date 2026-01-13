from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database
from typing import List, Optional
import time

from .. import schemas
from ..auth import get_current_active_admin
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/games",
    tags=["admin-games"],
    dependencies=[Depends(get_current_active_admin)],
)

# Game categories
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

# Simple in-memory cache for games
_games_cache: Optional[List[schemas.Game]] = None
_cache_timestamp: float = 0
CACHE_TTL = 60  # Cache for 60 seconds


def _invalidate_cache():
    global _games_cache, _cache_timestamp
    _games_cache = None
    _cache_timestamp = 0


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
def admin_list_games(db: Database = Depends(get_db)):
    global _games_cache, _cache_timestamp
    
    # Return cached result if still valid
    if _games_cache is not None and (time.time() - _cache_timestamp) < CACHE_TTL:
        categories = list(set(g.category for g in _games_cache if g.category))
        return schemas.GamesListResponse(
            total=len(_games_cache),
            items=_games_cache,
            categories=sorted(categories)
        )
    
    # Fetch from database and cache
    games = list(db["games"].find().sort([("order", 1), ("name", 1)]))
    _games_cache = [_game_document_to_schema(g) for g in games]
    _cache_timestamp = time.time()
    
    categories = list(set(g.category for g in _games_cache if g.category))
    return schemas.GamesListResponse(
        total=len(_games_cache),
        items=_games_cache,
        categories=sorted(categories)
    )


@router.post("", response_model=schemas.Game)
def admin_create_game(payload: schemas.GameCreate, db: Database = Depends(get_db)):
    existing = db["games"].find_one({"_id": payload.id})
    if existing:
        raise HTTPException(status_code=400, detail="Game with this id already exists")

    document = {
        "_id": payload.id,
        "id": payload.id,
        "name": payload.name,
        "description": payload.description,
        "image_url": payload.image_url,
        "game_url": payload.game_url,
        "category": payload.category,
        "is_active": payload.is_active,
        "order": payload.order,
    }
    db["games"].insert_one(document)
    _invalidate_cache()
    return _game_document_to_schema(document)


@router.put("/reorder")
def admin_reorder_games(payload: schemas.GameReorderRequest, db: Database = Depends(get_db)):
    """Batch reorder games"""
    from pymongo import UpdateOne
    
    operations = []
    for item in payload.items:
        operations.append(UpdateOne({"_id": item.id}, {"$set": {"order": item.order}}))
    
    if operations:
        db["games"].bulk_write(operations)
        
    _invalidate_cache()
    return {"status": "ok"}


@router.get("/{game_id}", response_model=schemas.Game)
def admin_get_game(game_id: str, db: Database = Depends(get_db)):
    document = db["games"].find_one({"_id": game_id})
    if not document:
        raise HTTPException(status_code=404, detail="Game not found")
    return _game_document_to_schema(document)


@router.put("/{game_id}", response_model=schemas.Game)
def admin_update_game(game_id: str, payload: schemas.GameUpdate, db: Database = Depends(get_db)):
    update_data = payload.dict(exclude_unset=True)
    if update_data:
        result = db["games"].update_one({"_id": game_id}, {"$set": update_data})
        if result.matched_count == 0:
            raise HTTPException(status_code=404, detail="Game not found")
    else:
        if not db["games"].find_one({"_id": game_id}):
            raise HTTPException(status_code=404, detail="Game not found")

    document = db["games"].find_one({"_id": game_id})
    _invalidate_cache()
    return _game_document_to_schema(document)


@router.delete("/{game_id}")
def admin_delete_game(game_id: str, db: Database = Depends(get_db)):
    result = db["games"].delete_one({"_id": game_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Game not found")
    _invalidate_cache()
    return {"status": "ok"}
