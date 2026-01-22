from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_company_or_admin as get_current_company
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/movies",
    tags=["admin-movies"],
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
        order=document.get("order"),
    )


@router.post("/reorder")
def admin_reorder_movies(
    payload: schemas.MovieReorderRequest,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Batch update order for movies."""
    from pymongo import UpdateOne
    
    operations = []
    for item in payload.items:
        operations.append(
            UpdateOne(
                {"_id": item.id, "company_id": company["_id"]},
                {"$set": {"order": item.order}}
            )
        )
    
    if operations:
        db["movies"].bulk_write(operations)
    
    return {"status": "ok", "updated": len(operations)}


@router.post("/batch", response_model=dict)
def admin_batch_create_movies(
    payload: list[schemas.MovieCreate],
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Batch create movies for fast import. Skips existing IDs."""
    from pymongo.errors import BulkWriteError
    
    if not payload:
        return {"status": "ok", "created": 0, "skipped": 0}
    
    # Get existing IDs for this company
    existing_ids = set(doc["_id"] for doc in db["movies"].find({"company_id": company["_id"]}, {"_id": 1}))
    
    documents = []
    skipped = 0
    for movie in payload:
        if movie.id in existing_ids:
            skipped += 1
            continue
            
        documents.append({
            "_id": movie.id,
            "id": movie.id,
            "company_id": company["_id"],  # Link to company
            "title": movie.title,
            "year": movie.year,
            "genres": movie.genres,
            "rating": movie.rating,
            "runtime_minutes": movie.runtime_minutes,
            "synopsis": movie.synopsis,
            "poster_url": str(movie.poster_url) if movie.poster_url else None,
            "landscape_url": str(movie.landscape_url) if movie.landscape_url else None,
            "hero_url": str(movie.hero_url) if movie.hero_url else None,
            "stream_url": str(movie.stream_url) if movie.stream_url else None,
            "trailer_url": str(movie.trailer_url) if movie.trailer_url else None,
            "drm_type": movie.drm_type,
            "drm_license_url": str(movie.drm_license_url) if movie.drm_license_url else None,
            "badges": movie.badges,
            "directors": movie.directors,
            "cast": movie.cast,
            "availability_start": movie.availability_start,
            "availability_end": movie.availability_end,
            "order": movie.order,
        })
    
    created = 0
    if documents:
        try:
            result = db["movies"].insert_many(documents, ordered=False)
            created = len(result.inserted_ids)
        except BulkWriteError as e:
            created = e.details.get("nInserted", 0)
    
    return {"status": "ok", "created": created, "skipped": skipped}


@router.get("", response_model=list[schemas.Movie])
def admin_list_movies(
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    movies = list(db["movies"].find({"company_id": company["_id"]}).sort("order", 1))
    return [_movie_document_to_schema(m) for m in movies]


@router.post("", response_model=schemas.Movie)
def admin_create_movie(
    payload: schemas.MovieCreate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    existing = db["movies"].find_one({"_id": payload.id, "company_id": company["_id"]})
    if existing:
        raise HTTPException(status_code=400, detail="Movie with this id already exists")

    document = {
        "_id": payload.id,
        "id": payload.id,
        "company_id": company["_id"],  # Link to company
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
        "order": payload.order,
    }
    db["movies"].insert_one(document)
    return _movie_document_to_schema(document)


@router.put("/{movie_id}", response_model=schemas.Movie)
def admin_update_movie(
    movie_id: str,
    payload: schemas.MovieUpdate,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    update_data = payload.dict(exclude_unset=True)
    if update_data:
        update_fields: dict[str, object] = {}
        for field, value in update_data.items():
            if field.endswith("_url") and value is not None:
                update_fields[field] = str(value)
            else:
                update_fields[field] = value

        result = db["movies"].update_one(
            {"_id": movie_id, "company_id": company["_id"]},
            {"$set": update_fields}
        )
        if result.matched_count == 0:
            raise HTTPException(status_code=404, detail="Movie not found")
    else:
        if not db["movies"].find_one({"_id": movie_id, "company_id": company["_id"]}):
            raise HTTPException(status_code=404, detail="Movie not found")

    document = db["movies"].find_one({"_id": movie_id, "company_id": company["_id"]})
    return _movie_document_to_schema(document)


@router.delete("/{movie_id}")
def admin_delete_movie(
    movie_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    result = db["movies"].delete_one({"_id": movie_id, "company_id": company["_id"]})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Movie not found")
    return {"status": "ok"}


@router.post("/{movie_id}/enrich")
async def admin_enrich_movie(
    movie_id: str,
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Fetch metadata from TMDB for this movie and update the database."""
    from ..tmdb import enrich_movie
    
    movie = db["movies"].find_one({"_id": movie_id, "company_id": company["_id"]})
    if not movie:
        raise HTTPException(status_code=404, detail="Movie not found")
    
    title = movie.get("title", "")
    year = movie.get("year")
    
    if not title:
        raise HTTPException(status_code=400, detail="Movie has no title to search")
    
    try:
        enriched = await enrich_movie(title, year)
    except ValueError as e:
        raise HTTPException(status_code=500, detail=str(e))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"TMDB API error: {str(e)}")
    
    if not enriched:
        raise HTTPException(status_code=404, detail=f"No TMDB results for '{title}'")
    
    update_fields = {
        "tmdb_id": enriched.tmdb_id,
        "synopsis": enriched.synopsis,
        "runtime_minutes": enriched.runtime_minutes,
        "rating": enriched.rating,
        "genres": enriched.genres,
        "directors": enriched.directors,
        "cast": enriched.cast,
    }
    
    if enriched.poster_url and not movie.get("poster_url"):
        update_fields["poster_url"] = enriched.poster_url
    if enriched.landscape_url and not movie.get("landscape_url"):
        update_fields["landscape_url"] = enriched.landscape_url
    if enriched.hero_url and not movie.get("hero_url"):
        update_fields["hero_url"] = enriched.hero_url
    if enriched.trailer_url and not movie.get("trailer_url"):
        update_fields["trailer_url"] = enriched.trailer_url
    
    db["movies"].update_one({"_id": movie_id, "company_id": company["_id"]}, {"$set": update_fields})
    
    updated = db["movies"].find_one({"_id": movie_id, "company_id": company["_id"]})
    return _movie_document_to_schema(updated)


@router.post("/enrich-all")
async def admin_enrich_all_movies(
    company: dict = Depends(get_current_company),
    db: Database = Depends(get_db)
):
    """Fetch metadata from TMDB for ALL movies of this company."""
    from ..tmdb import enrich_movie
    import asyncio
    
    movies = list(db["movies"].find({"company_id": company["_id"]}))
    enriched_count = 0
    failed_count = 0
    
    for movie in movies:
        title = movie.get("title", "")
        year = movie.get("year")
        movie_id = movie.get("_id")
        
        if not title:
            failed_count += 1
            continue
        
        try:
            enriched = await enrich_movie(title, year)
            
            if not enriched:
                failed_count += 1
                continue
            
            update_fields = {
                "tmdb_id": enriched.tmdb_id,
                "synopsis": enriched.synopsis,
                "runtime_minutes": enriched.runtime_minutes,
                "rating": enriched.rating,
                "genres": enriched.genres,
                "directors": enriched.directors,
                "cast": enriched.cast,
            }
            
            if enriched.poster_url and not movie.get("poster_url"):
                update_fields["poster_url"] = enriched.poster_url
            if enriched.landscape_url and not movie.get("landscape_url"):
                update_fields["landscape_url"] = enriched.landscape_url
            if enriched.hero_url and not movie.get("hero_url"):
                update_fields["hero_url"] = enriched.hero_url
            if enriched.trailer_url and not movie.get("trailer_url"):
                update_fields["trailer_url"] = enriched.trailer_url
            
            db["movies"].update_one({"_id": movie_id, "company_id": company["_id"]}, {"$set": update_fields})
            enriched_count += 1
            
            await asyncio.sleep(0.3)
            
        except Exception as e:
            print(f"Failed to enrich {title}: {e}")
            failed_count += 1
    
    return {
        "status": "ok",
        "enriched": enriched_count,
        "failed": failed_count,
        "total": len(movies)
    }
