from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_active_admin
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/movies",
    tags=["admin-movies"],
    dependencies=[Depends(get_current_active_admin)],
)


def _movie_document_to_schema(document: dict) -> schemas.Movie:
    movie_id = document.get("id") or document.get("_id")
    images = {
        "poster": document.get("poster_url"),
        "landscape": document.get("landscape_url"),
        "hero": document.get("hero_url"),
    }
    drm = None
    if document.get("drm_type") and document.get("drm_license_url"):
        drm = {"type": document["drm_type"], "licenseUrl": document["drm_license_url"]}
    media = {
        "streamUrl": document.get("stream_url"),
        "trailerUrl": document.get("trailer_url"),
        "drm": drm,
    }
    credits = {
        "directors": document.get("directors") or [],
        "cast": document.get("cast") or [],
    }
    availability = {
        "start": document.get("availability_start"),
        "end": document.get("availability_end"),
    }
    return schemas.Movie(
        id=movie_id,
        title=document.get("title"),
        year=document.get("year"),
        genres=document.get("genres"),
        rating=document.get("rating"),
        runtimeMinutes=document.get("runtime_minutes"),
        synopsis=document.get("synopsis"),
        images=images,
        media=media,
        badges=document.get("badges"),
        credits=credits,
        availability=availability,
    )


@router.get("", response_model=list[schemas.Movie])
def admin_list_movies(db: Database = Depends(get_db)):
    movies = list(db["movies"].find())
    return [_movie_document_to_schema(m) for m in movies]


@router.post("", response_model=schemas.Movie)
def admin_create_movie(payload: schemas.MovieCreate, db: Database = Depends(get_db)):
    existing = db["movies"].find_one({"_id": payload.id})
    if existing:
        raise HTTPException(status_code=400, detail="Movie with this id already exists")

    document = {
        "_id": payload.id,
        "id": payload.id,
        "title": payload.title,
        "year": payload.year,
        "genres": payload.genres,
        "rating": payload.rating,
        "runtime_minutes": payload.runtime_minutes,
        "synopsis": payload.synopsis,
        "poster_url": str(payload.poster_url) if payload.poster_url else None,
        "landscape_url": str(payload.landscape_url) if payload.landscape_url else None,
        "hero_url": str(payload.hero_url) if payload.hero_url else None,
        "stream_url": str(payload.stream_url) if payload.stream_url else None,
        "trailer_url": str(payload.trailer_url) if payload.trailer_url else None,
        "drm_type": payload.drm_type,
        "drm_license_url": str(payload.drm_license_url) if payload.drm_license_url else None,
        "badges": payload.badges,
        "directors": payload.directors,
        "cast": payload.cast,
        "availability_start": payload.availability_start,
        "availability_end": payload.availability_end,
    }
    db["movies"].insert_one(document)
    return _movie_document_to_schema(document)


@router.put("/{movie_id}", response_model=schemas.Movie)
def admin_update_movie(movie_id: str, payload: schemas.MovieUpdate, db: Database = Depends(get_db)):
    update_data = payload.dict(exclude_unset=True)
    if update_data:
        update_fields: dict[str, object] = {}
        for field, value in update_data.items():
            if field.endswith("_url") and value is not None:
                update_fields[field] = str(value)
            else:
                update_fields[field] = value

        result = db["movies"].update_one({"_id": movie_id}, {"$set": update_fields})
        if result.matched_count == 0:
            raise HTTPException(status_code=404, detail="Movie not found")
    else:
        if not db["movies"].find_one({"_id": movie_id}):
            raise HTTPException(status_code=404, detail="Movie not found")

    document = db["movies"].find_one({"_id": movie_id})
    return _movie_document_to_schema(document)


@router.delete("/{movie_id}")
def admin_delete_movie(movie_id: str, db: Database = Depends(get_db)):
    result = db["movies"].delete_one({"_id": movie_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Movie not found")
    return {"status": "ok"}
