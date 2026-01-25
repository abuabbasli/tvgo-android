from datetime import datetime, timedelta, date
from typing import List, Optional, Set

from bson import ObjectId
from fastapi import APIRouter, Depends, Query
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_subscriber, get_optional_subscriber
from ..config import settings
from ..database import get_db
from ..errors import not_found, unauthorized

router = APIRouter(prefix=settings.api_v1_prefix, tags=["public"])


DEFAULT_BRAND_DOCUMENT = {
    "_id": "brand",
    "app_name": "tvGO",
    "logo_url": "https://cdn.tvgo.cloud/brand/tvgo-logo.png",
    "accent_color": "#1EA7FD",
    "background_color": "#050607",
    "enable_favorites": True,
    "enable_search": True,
    "autoplay_preview": True,
    "enable_live_tv": True,
    "enable_vod": True,
    "channel_groups": [
        "All",
        "Favorites",
        "Kids",
        "Sports",
        "News",
        "Entertainment",
        "Movies",
    ],
    "movie_genres": [
        "All",
        "Action",
        "Comedy",
        "Drama",
        "Kids & Family",
        "Sci-Fi",
        "Thriller",
        "Horror",
        "Romance",
    ],
}


def _get_user_allowed_channels(db: Database, user: dict) -> Optional[Set[str]]:
    """
    Get the set of channel IDs that a user is allowed to access based on their packages.
    Returns None if user has no packages (meaning all channels are allowed).
    Returns empty set if user has packages but no channels in them.
    """
    user_package_ids = user.get("package_ids") or []
    print(f"🔍 DEBUG: User package_ids: {user_package_ids}")
    if not user_package_ids:
        return None  # No package restriction

    package_oids = []
    for pid in user_package_ids:
        try:
            package_oids.append(ObjectId(pid))
        except Exception as e:
            print(f"❌ DEBUG: Failed to convert package ID {pid}: {e}")
            pass

    if not package_oids:
        return None

    packages = list(db["packages"].find({"_id": {"$in": package_oids}}))
    print(f"🔍 DEBUG: Found {len(packages)} packages")
    allowed_channel_ids: Set[str] = set()
    for pkg in packages:
        channel_ids = pkg.get("channel_ids") or []
        print(f"🔍 DEBUG: Package '{pkg.get('name')}' has {len(channel_ids)} channel IDs: {channel_ids[:5] if len(channel_ids) > 5 else channel_ids}")
        allowed_channel_ids.update(channel_ids)

    print(f"🔍 DEBUG: Total allowed channel IDs: {len(allowed_channel_ids)}")
    return allowed_channel_ids



def _default_programs_window() -> List[schemas.ProgramScheduleItem]:
    now = datetime.utcnow().replace(minute=0, second=0, microsecond=0)
    items = []
    items.append(
        schemas.ProgramScheduleItem(
            id="p1",
            title="Action Movie",
            category="Movie",
            start=now,
            end=now + timedelta(hours=1),
            isLive=True,
        )
    )
    items.append(
        schemas.ProgramScheduleItem(
            id="p2",
            title="News",
            category="News",
            start=now + timedelta(hours=1),
            end=now + timedelta(hours=1, minutes=30),
            isLive=True,
        )
    )
    items.append(
        schemas.ProgramScheduleItem(
            id="p3",
            title="Weather Program",
            category="Weather",
            start=now + timedelta(hours=1, minutes=30),
            end=now + timedelta(hours=2),
            isLive=True,
        )
    )
    return items


def _ensure_brand_document(db: Database, company_id=None) -> dict:
    """Get brand config. If company_id provided, look for company-specific config first."""
    if company_id:
        # Check for company-specific brand config
        document = db["brand_config"].find_one({"company_id": company_id})
        if document:
            return document
    
    # Fallback to global brand config
    document = db["brand_config"].find_one({"_id": "brand"})
    if document:
        return document
    db["brand_config"].insert_one(DEFAULT_BRAND_DOCUMENT.copy())
    return DEFAULT_BRAND_DOCUMENT.copy()


def _channel_document_to_schema(document: dict, schedule: Optional[List[schemas.ProgramScheduleItem]] = None) -> schemas.Channel:
    # Use _id as the channel identifier (unique document key with company prefix)
    # This ensures consistency with schedule/EPG lookups
    channel_id = document.get("_id")
    drm = None
    if document.get("drm_type") and document.get("drm_license_url"):
        drm = {"type": document["drm_type"], "licenseUrl": document["drm_license_url"]}
    program_schedule = schedule
    if program_schedule is None:
        raw_schedule = document.get("program_schedule") or []
        if raw_schedule:
            program_schedule = [
                schemas.ProgramScheduleItem(**item)
                if not isinstance(item, schemas.ProgramScheduleItem)
                else item
                for item in raw_schedule
            ]
    if program_schedule is None:
        program_schedule = _default_programs_window()
    metadata = document.get("metadata") or {}
    return schemas.Channel(
        id=channel_id,
        name=document.get("name"),
        group=document.get("group"),
        category=document.get("group"),
        description=metadata.get("description", ""),
        logoColor=metadata.get("logo_color", "#000000"),
        logo=document.get("logo_url"),
        streamUrl=document.get("stream_url"),
        drm=drm,
        lang=document.get("lang"),
        country=document.get("country"),
        programSchedule=program_schedule,
        badges=document.get("badges"),
        metadata=metadata,
        streamerName=document.get("streamer_name"),
        order=document.get("order"),
    )


def _movie_document_to_schema(document: dict) -> schemas.Movie:
    movie_id = document.get("id") or document.get("_id")
    poster = document.get("poster_url") or document.get("thumbnail")
    
    images = {
        "poster": poster,
        "landscape": document.get("landscape_url"),
        "hero": document.get("hero_url"),
    }
    drm = None
    if document.get("drm_type") and document.get("drm_license_url"):
        drm = {"type": document["drm_type"], "licenseUrl": document["drm_license_url"]}
    
    stream_url = document.get("stream_url") or document.get("videoUrl")
    
    media = {
        "streamUrl": stream_url,
        "trailerUrl": document.get("trailer_url"),
        "drm": drm,
    }
    credits = {
        "directors": document.get("directors") or ([document.get("director")] if document.get("director") else []),
        "cast": document.get("cast") or [],
    }
    availability = {
        "start": document.get("availability_start"),
        "end": document.get("availability_end"),
    }
    
    genres = document.get("genres") or document.get("genre")
    synopsis = document.get("synopsis") or document.get("description")
    
    return schemas.Movie(
        id=movie_id,
        title=document.get("title"),
        year=document.get("year"),
        genres=genres,
        rating=document.get("rating"),
        runtimeMinutes=document.get("runtime_minutes"),
        synopsis=synopsis,
        images=images,
        media=media,
        badges=document.get("badges"),
        credits=credits,
        availability=availability,
        order=document.get("order"),
    )


def build_config_response(db: Database, company_id=None) -> schemas.ConfigResponse:
    brand_doc = _ensure_brand_document(db, company_id)

    brand = schemas.Brand(
        appName=brand_doc.get("app_name", DEFAULT_BRAND_DOCUMENT["app_name"]),
        logoUrl=brand_doc.get("logo_url"),
        accentColor=brand_doc.get("accent_color", DEFAULT_BRAND_DOCUMENT["accent_color"]),
        backgroundColor=brand_doc.get("background_color", DEFAULT_BRAND_DOCUMENT["background_color"]),
    )
    features = schemas.Features(
        enableFavorites=brand_doc.get("enable_favorites", DEFAULT_BRAND_DOCUMENT["enable_favorites"]),
        enableSearch=brand_doc.get("enable_search", DEFAULT_BRAND_DOCUMENT["enable_search"]),
        autoplayPreview=brand_doc.get("autoplay_preview", DEFAULT_BRAND_DOCUMENT["autoplay_preview"]),
        enableLiveTv=brand_doc.get("enable_live_tv", DEFAULT_BRAND_DOCUMENT["enable_live_tv"]),
        enableVod=brand_doc.get("enable_vod", DEFAULT_BRAND_DOCUMENT["enable_vod"]),
    )
    channel_groups = brand_doc.get("channel_groups", DEFAULT_BRAND_DOCUMENT["channel_groups"])
    movie_genres = brand_doc.get("movie_genres", DEFAULT_BRAND_DOCUMENT["movie_genres"])
    return schemas.ConfigResponse(
        brand=brand,
        features=features,
        channelGroups=channel_groups,
        movieGenres=movie_genres,
    )


@router.get("/config", response_model=schemas.ConfigResponse)
def get_config(
    db: Database = Depends(get_db),
    current_user=Depends(get_optional_subscriber)
):
    company_id = current_user.get("company_id") if current_user else None
    return build_config_response(db, company_id)


@router.get("/channels", response_model=schemas.ChannelsListResponse)
def list_channels(
    db: Database = Depends(get_db),
    group: Optional[str] = None,
    search: Optional[str] = Query(None, alias="search"),
    limit: int = Query(50, ge=1, le=1000),
    offset: int = Query(0, ge=0),
    favorite: Optional[bool] = Query(None, alias="favorite"),
    current_user=Depends(get_current_subscriber),
):
    """List channels for the subscriber's company, filtered by user's packages"""
    company_id = current_user.get("company_id")

    filters: dict[str, object] = {"company_id": company_id}

    # Package-based channel filtering
    allowed_channels = _get_user_allowed_channels(db, current_user)
    print(f"🔍 DEBUG list_channels: allowed_channels = {allowed_channels}")
    if allowed_channels is not None:
        if not allowed_channels:
            # User has packages but no channels in them - return empty
            print("⚠️ DEBUG: User has packages but no channels - returning empty")
            return schemas.ChannelsListResponse(total=0, items=[], nextOffset=None)
        
        # Build regex patterns to match channel IDs
        # Channel IDs in DB are like "uuid_channelname" but package has just "channelname"
        # So we match channels where _id ends with the package channel ID
        # Important: Escape regex special characters to prevent over-matching (e.g., "1" matching "11", "21", etc.)
        import re
        escaped_ids = [re.escape(ch_id) for ch_id in allowed_channels]
        regex_patterns = [f".*_{esc_id}$" for esc_id in escaped_ids]
        filters["$or"] = [
            {"_id": {"$in": list(allowed_channels)}},  # Exact match
            {"_id": {"$regex": "|".join(regex_patterns)}}  # Suffix match
        ]
        print(f"🔍 DEBUG: Added filter with {len(allowed_channels)} channel IDs")

    if group:
        filters["group"] = group
    if search:
        filters["name"] = {"$regex": search, "$options": "i"}

    if favorite:
        favorites_doc = db["favorites"].find_one({"_id": current_user["_id"]}) or {}
        favorite_channels = favorites_doc.get("channels") or []
        # Combine with package filter if exists
        if "_id" in filters and "$in" in filters["_id"]:
            # Intersect allowed channels with favorites
            allowed = set(filters["_id"]["$in"])
            favorite_set = set(favorite_channels) if favorite_channels else set()
            intersection = allowed & favorite_set
            filters["_id"] = {"$in": list(intersection) or ["__none__"]}
        else:
            filters["_id"] = {"$in": favorite_channels or ["__none__"]}

    print(f"🔍 DEBUG: MongoDB filter: {filters}")
    total = db["channels"].count_documents(filters)
    print(f"🔍 DEBUG: Total matching: {total}")
    cursor = db["channels"].find(filters).sort("order", 1).skip(offset).limit(limit)
    items = list(cursor)
    print(f"🔍 DEBUG: Retrieved {len(items)} items")

    channels_schema = []
    for ch in items:
        channels_schema.append(_channel_document_to_schema(ch))

    next_offset = offset + limit if offset + limit < total else None
    return schemas.ChannelsListResponse(total=total, items=channels_schema, nextOffset=next_offset)


@router.get("/channels/{channel_id}", response_model=schemas.Channel)
def get_channel(
    channel_id: str,
    db: Database = Depends(get_db),
    current_user=Depends(get_current_subscriber)
):
    company_id = current_user.get("company_id")
    # Try to find channel by _id first, then by id field (for M3U imported channels)
    document = db["channels"].find_one({"_id": channel_id, "company_id": company_id})
    if not document:
        # Fallback: try finding by the 'id' field (M3U ID without company prefix)
        document = db["channels"].find_one({"id": channel_id, "company_id": company_id})
    if not document:
        raise not_found("Channel not found")

    # Package-based access control
    allowed_channels = _get_user_allowed_channels(db, current_user)
    if allowed_channels is not None:
        actual_channel_id = document.get("_id")
        if actual_channel_id not in allowed_channels:
            raise not_found("Channel not found")

    return _channel_document_to_schema(document)


@router.get("/epg/now")
def get_current_programs(
    db: Database = Depends(get_db),
    current_user=Depends(get_current_subscriber)
):
    """Get current program for all channels based on current time"""
    company_id = current_user.get("company_id")
    now = datetime.utcnow()

    # Build channel filter with package restrictions
    channel_filter: dict[str, object] = {
        "company_id": company_id,
        "epg_id": {"$exists": True, "$ne": None}
    }
    allowed_channels = _get_user_allowed_channels(db, current_user)
    if allowed_channels is not None:
        if not allowed_channels:
            return {"programs": {}}
        channel_filter["_id"] = {"$in": list(allowed_channels)}

    channels = list(db["channels"].find(channel_filter, {"_id": 1, "epg_id": 1}))
    
    epg_ids = [ch.get("epg_id") for ch in channels if ch.get("epg_id")]
    channel_epg_map = {ch.get("epg_id"): ch["_id"] for ch in channels if ch.get("epg_id")}
    
    if not epg_ids:
        return {"programs": {}}
    
    pipeline = [
        {
            "$match": {
                "channel_id": {"$in": epg_ids},
                "start": {"$lte": now},
                "end": {"$gt": now}
            }
        },
        {"$sort": {"start": 1}},
        {"$group": {"_id": "$channel_id", "program": {"$first": "$$ROOT"}}}
    ]
    
    results = list(db["epg_programs"].aggregate(pipeline))
    
    programs = {}
    for r in results:
        epg_ch_id = r["_id"]
        program = r["program"]
        our_channel_id = channel_epg_map.get(epg_ch_id)
        if our_channel_id:
            programs[our_channel_id] = {
                "title": program.get("title"),
                "start": program.get("start").isoformat() if program.get("start") else None,
                "end": program.get("end").isoformat() if program.get("end") else None,
                "description": program.get("description"),
                "category": program.get("category")
            }
    
    return {"programs": programs}


@router.get("/epg/schedule/{channel_id}")
def get_channel_schedule(
    channel_id: str,
    hours: int = Query(12, ge=1, le=48),
    db: Database = Depends(get_db),
    current_user=Depends(get_current_subscriber)
):
    """Get upcoming programs for a channel for the next N hours"""
    company_id = current_user.get("company_id")
    now = datetime.utcnow()
    end_time = now + timedelta(hours=hours)

    # Try to find channel by _id first, then by id field (for M3U imported channels)
    channel = db["channels"].find_one({"_id": channel_id, "company_id": company_id}, {"_id": 1, "epg_id": 1})
    if not channel:
        # Fallback: try finding by the 'id' field (M3U ID without company prefix)
        channel = db["channels"].find_one({"id": channel_id, "company_id": company_id}, {"_id": 1, "epg_id": 1})
    if not channel:
        raise not_found("Channel not found")

    # Package-based access control
    allowed_channels = _get_user_allowed_channels(db, current_user)
    if allowed_channels is not None:
        if channel.get("_id") not in allowed_channels:
            raise not_found("Channel not found")
    
    epg_id = channel.get("epg_id")
    if not epg_id:
        return {"channel_id": channel_id, "programs": []}
    
    programs = list(db["epg_programs"].find({
        "channel_id": epg_id,
        "$or": [
            {"start": {"$gte": now, "$lt": end_time}},
            {"start": {"$lt": now}, "end": {"$gt": now}}
        ]
    }).sort("start", 1).limit(50))
    
    items = []
    for p in programs:
        start = p.get("start")
        end = p.get("end")
        is_live = start and end and start <= now < end
        items.append({
            "id": p.get("program_id"),
            "title": p.get("title"),
            "start": start.isoformat() if start else None,
            "end": end.isoformat() if end else None,
            "description": p.get("description"),
            "category": p.get("category"),
            "isLive": is_live
        })
    
    return {"channel_id": channel_id, "programs": items}


@router.get("/channels/{channel_id}/epg", response_model=schemas.EpgResponse)
def get_epg(
    channel_id: str,
    db: Database = Depends(get_db),
    date_param: Optional[date] = Query(None, alias="date"),
    from_param: Optional[datetime] = Query(None, alias="from"),
    to_param: Optional[datetime] = Query(None, alias="to"),
    limit: int = Query(200, ge=1, le=500),
    offset: int = Query(0, ge=0),
    current_user=Depends(get_current_subscriber)
):
    company_id = current_user.get("company_id")
    # Try to find channel by _id first, then by id field (for M3U imported channels)
    channel = db["channels"].find_one({"_id": channel_id, "company_id": company_id}, {"_id": 1, "epg_id": 1})
    if not channel:
        # Fallback: try finding by the 'id' field (M3U ID without company prefix)
        channel = db["channels"].find_one({"id": channel_id, "company_id": company_id}, {"_id": 1, "epg_id": 1})
    if not channel:
        raise not_found("Channel not found")

    # Package-based access control
    allowed_channels = _get_user_allowed_channels(db, current_user)
    if allowed_channels is not None:
        if channel.get("_id") not in allowed_channels:
            raise not_found("Channel not found")

    # Use epg_id to query programs, fall back to channel_id if no mapping
    epg_id = channel.get("epg_id") or channel_id
    filters: dict[str, object] = {"channel_id": epg_id}
    if date_param:
        start_day = datetime.combine(date_param, datetime.min.time())
        end_day = datetime.combine(date_param, datetime.max.time())
        filters["start"] = {"$gte": start_day}
        filters["end"] = {"$lte": end_day}
    elif from_param and to_param:
        filters["start"] = {"$gte": from_param}
        filters["end"] = {"$lte": to_param}

    total = db["epg_programs"].count_documents(filters)
    cursor = db["epg_programs"].find(filters).sort("start", 1).skip(offset).limit(limit)
    programs = list(cursor)

    items = [
        schemas.EpgProgramItem(
            id=p.get("program_id"),
            title=p.get("title"),
            category=p.get("category"),
            description=p.get("description"),
            season=p.get("season"),
            episode=p.get("episode"),
            start=p.get("start"),
            end=p.get("end"),
            isLive=p.get("is_live", False),
        )
        for p in programs
    ]

    if not items:
        default_items = _default_programs_window()
        items = [
            schemas.EpgProgramItem(
                id=i.id,
                title=i.title,
                category=i.category,
                description=None,
                season=None,
                episode=None,
                start=i.start,
                end=i.end,
                isLive=i.isLive,
            )
            for i in default_items
        ]
        total = len(items)

    next_offset = offset + limit if offset + limit < total else None
    return schemas.EpgResponse(channelId=channel_id, date=date_param, items=items, nextOffset=next_offset)


@router.get("/movies", response_model=schemas.MoviesListResponse)
def list_movies(
    db: Database = Depends(get_db),
    genre: Optional[str] = None,
    search: Optional[str] = Query(None, alias="search"),
    sort: Optional[str] = None,
    limit: int = Query(50, ge=1, le=1000),
    offset: int = Query(0, ge=0),
    current_user=Depends(get_current_subscriber)
):
    """List movies for the subscriber's company"""
    company_id = current_user.get("company_id")
    
    filters: dict[str, object] = {"company_id": company_id}
    if genre and genre.lower() != "all":
        filters["genres"] = genre
    if search:
        filters["title"] = {"$regex": search, "$options": "i"}

    cursor = db["movies"].find(filters)
    if sort == "year":
        cursor = cursor.sort("year", -1)
    elif sort == "rating":
        cursor = cursor.sort("rating", -1)
    else:
        cursor = cursor.sort("order", 1)

    total = db["movies"].count_documents(filters)
    items = list(cursor.skip(offset).limit(limit))

    movies_schema = [_movie_document_to_schema(m) for m in items]
    next_offset = offset + limit if offset + limit < total else None
    return schemas.MoviesListResponse(total=total, items=movies_schema, nextOffset=next_offset)


@router.get("/movies/{movie_id}", response_model=schemas.Movie)
def get_movie(
    movie_id: str,
    db: Database = Depends(get_db),
    current_user=Depends(get_current_subscriber)
):
    company_id = current_user.get("company_id")
    document = db["movies"].find_one({"_id": movie_id, "company_id": company_id})
    if not document:
        raise not_found("Movie not found")
    return _movie_document_to_schema(document)


@router.get("/rails", response_model=list[schemas.RailPublic])
def get_rails(
    db: Database = Depends(get_db),
    current_user=Depends(get_current_subscriber)
):
    company_id = current_user.get("company_id")
    rails = list(db["rails"].find({"company_id": company_id}).sort("sort_order", 1))
    return [
        schemas.RailPublic(
            id=r.get("id") or r.get("_id"),
            title=r.get("title"),
            type=r.get("type"),
            query=r.get("query") or {},
        )
        for r in rails
    ]


@router.get("/profile/favorites", response_model=schemas.FavoritesResponse)
async def get_favorites(
    current_user=Depends(get_current_subscriber), 
    db: Database = Depends(get_db)
):
    subscriber_id = current_user["_id"]
    document = db["favorites"].find_one({"_id": subscriber_id}) or {}
    return schemas.FavoritesResponse(
        channels=document.get("channels", []),
        movies=document.get("movies", []),
    )


@router.put("/profile/favorites", response_model=schemas.FavoritesResponse)
async def update_favorites(
    payload: schemas.FavoritesResponse,
    current_user=Depends(get_current_subscriber),
    db: Database = Depends(get_db),
):
    subscriber_id = current_user["_id"]
    db["favorites"].update_one(
        {"_id": subscriber_id},
        {
            "$set": {
                "channels": payload.channels,
                "movies": payload.movies,
                "updated_at": datetime.utcnow(),
            }
        },
        upsert=True,
    )
    return payload


# ---- Games ----

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


@router.get("/games", response_model=schemas.GamesListResponse)
def list_games(
    db: Database = Depends(get_db),
    category: Optional[str] = None,
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
    current_user=Depends(get_current_subscriber)
):
    """Public games list for Android app - filtered by company"""
    company_id = current_user.get("company_id")
    
    filters: dict[str, object] = {"is_active": True, "company_id": company_id}
    if category and category.lower() != "all":
        filters["category"] = category

    total = db["games"].count_documents(filters)
    cursor = db["games"].find(filters).sort([("order", 1), ("name", 1)]).skip(offset).limit(limit)
    items = list(cursor)
    
    games_schema = [_game_document_to_schema(g) for g in items]
    
    all_games = list(db["games"].find({"is_active": True, "company_id": company_id}))
    categories = list(set(g.get("category") for g in all_games if g.get("category")))
    
    return schemas.GamesListResponse(
        total=total,
        items=games_schema,
        categories=sorted(categories)
    )


@router.get("/games/{game_id}", response_model=schemas.Game)
def get_game(
    game_id: str,
    db: Database = Depends(get_db),
    current_user=Depends(get_current_subscriber)
):
    company_id = current_user.get("company_id")
    document = db["games"].find_one({"_id": game_id, "is_active": True, "company_id": company_id})
    if not document:
        raise not_found("Game not found")
    return _game_document_to_schema(document)
