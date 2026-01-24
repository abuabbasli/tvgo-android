import re
from typing import List, Optional

import httpx
from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pymongo.database import Database

from .. import schemas
from ..auth import get_current_company_or_admin
from ..config import settings
from ..database import get_db

router = APIRouter(
    prefix=f"{settings.api_v1_prefix}/admin/ingest",
    tags=["admin-ingest"],
)


def extract_attribute(line: str, attr: str) -> Optional[str]:
    """Extract attribute value from EXTINF line."""
    pattern = rf'{attr}="([^"]*)"'
    match = re.search(pattern, line)
    return match.group(1) if match else None


def slugify(value: str) -> str:
    """Convert a string to a URL-safe slug."""
    value = value.strip().lower()
    value = re.sub(r"[^a-z0-9]+", "-", value)
    value = re.sub(r"-+", "-", value)
    return value.strip("-") or "channel"


def parse_m3u_content(content: str) -> List[schemas.M3UChannelPreview]:
    """Parse M3U content and return list of channel previews."""
    lines = [line.strip() for line in content.splitlines() if line.strip()]
    channels = []
    seen_ids = set()
    
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.startswith("#EXTINF"):
            # Extract attributes using flexible pattern matching
            tvg_id = extract_attribute(line, "tvg-id")
            tvg_name = extract_attribute(line, "tvg-name")
            tvg_logo = extract_attribute(line, "tvg-logo")
            group = extract_attribute(line, "group-title")
            
            # Extract display name (after the last comma)
            comma_idx = line.rfind(",")
            display_name = line[comma_idx + 1:].strip() if comma_idx != -1 else ""
            display_name = display_name or tvg_name or tvg_id or "Channel"

            if i + 1 >= len(lines):
                break
            url = lines[i + 1]
            
            # Skip if URL is another EXTINF or comment
            if url.startswith("#"):
                i += 1
                continue

            base_id = tvg_id or slugify(display_name)
            channel_id = base_id
            counter = 1
            while channel_id in seen_ids:
                channel_id = f"{base_id}-{counter}"
                counter += 1
            seen_ids.add(channel_id)

            channels.append(schemas.M3UChannelPreview(
                id=channel_id,
                name=display_name,
                group=group,
                logo_url=tvg_logo or None,
                stream_url=url,
            ))
            i += 2
        else:
            i += 1
    
    return channels


async def fetch_m3u_from_url(url: str) -> str:
    """Fetch M3U content from a URL, following redirects."""
    async with httpx.AsyncClient(follow_redirects=True, timeout=30.0) as client:
        response = await client.get(url)
        response.raise_for_status()
        return response.text


@router.post("/m3u")
async def ingest_m3u(file: UploadFile = File(...), db: Database = Depends(get_db)):
    filename = file.filename or ""
    if not filename.endswith(".m3u") and not filename.endswith(".m3u8"):
        raise HTTPException(status_code=400, detail="File must be .m3u or .m3u8")

    content = (await file.read()).decode("utf-8", errors="ignore")
    channels = parse_m3u_content(content)
    
    created = 0
    for ch in channels:
        update = {
            "id": ch.id,
            "name": ch.name,
            "group": ch.group,
            "logo_url": ch.logo_url,
            "stream_url": ch.stream_url,
            "drm_type": None,
            "drm_license_url": None,
            "lang": None,
            "country": None,
            "badges": ["HD"],
            "metadata": {"source": "m3u"},
        }
        result = db["channels"].update_one(
            {"_id": ch.id},
            {"$set": update, "$setOnInsert": {"_id": ch.id}},
            upsert=True,
        )
        if result.upserted_id is not None:
            created += 1

    try:
        from . import admin_channels
        admin_channels._invalidate_cache()
    except ImportError:
        pass

    return {"status": "ok", "created": created}


@router.post("/m3u-url/preview", response_model=schemas.M3UParseResponse)
async def preview_m3u_from_url(
    request: schemas.M3UUrlIngestRequest,
    company: dict = Depends(get_current_company_or_admin),
):
    """
    Fetch M3U from URL and return parsed channels for preview.
    This allows the frontend to show channels before importing.
    """
    try:
        content = await fetch_m3u_from_url(request.url)
    except httpx.HTTPStatusError as e:
        raise HTTPException(status_code=400, detail=f"Failed to fetch M3U: HTTP {e.response.status_code}")
    except httpx.RequestError as e:
        raise HTTPException(status_code=400, detail=f"Failed to fetch M3U: {str(e)}")
    
    channels = parse_m3u_content(content)
    return schemas.M3UParseResponse(channels=channels, total=len(channels))


@router.post("/m3u/preview", response_model=schemas.M3UParseResponse)
async def preview_m3u_file(file: UploadFile = File(...), db: Database = Depends(get_db)):
    filename = file.filename or ""
    if not filename.endswith(".m3u") and not filename.endswith(".m3u8"):
        raise HTTPException(status_code=400, detail="File must be .m3u or .m3u8")

    content = (await file.read()).decode("utf-8", errors="ignore")
    channels = parse_m3u_content(content)
    return schemas.M3UParseResponse(channels=channels, total=len(channels))


@router.post("/m3u-url")
async def ingest_m3u_from_url(
    request: schemas.M3UIngestRequest,
    company: dict = Depends(get_current_company_or_admin),
    db: Database = Depends(get_db),
):
    """
    Fetch M3U from URL and ingest channels into the database.
    Optionally filter by channel_ids to only import selected channels.
    """
    company_id = company["_id"]
    
    try:
        content = await fetch_m3u_from_url(request.url)
    except httpx.HTTPStatusError as e:
        raise HTTPException(status_code=400, detail=f"Failed to fetch M3U: HTTP {e.response.status_code}")
    except httpx.RequestError as e:
        raise HTTPException(status_code=400, detail=f"Failed to fetch M3U: {str(e)}")
    
    channels = parse_m3u_content(content)
    
    # Filter channels if specific IDs are provided
    if request.channel_ids:
        channels = [ch for ch in channels if ch.id in request.channel_ids]
    
    created = 0
    updated = 0
    for order_index, ch in enumerate(channels):
        # Create company-scoped channel ID
        channel_id = f"{company_id}_{ch.id}"
        update = {
            "id": ch.id,
            "company_id": company_id,  # Multi-tenant support
            "name": ch.name,
            "group": ch.group,
            "logo_url": ch.logo_url,
            "stream_url": ch.stream_url,
            "drm_type": None,
            "drm_license_url": None,
            "lang": None,
            "country": None,
            "badges": ["HD"],
            "streamer_name": request.streamer_name,
            "order": order_index,  # Preserve M3U order
            "metadata": {
                "source": "m3u",
                "streamer_name": request.streamer_name,
            },
        }
        result = db["channels"].update_one(
            {"_id": channel_id, "company_id": company_id},
            {"$set": update, "$setOnInsert": {"_id": channel_id}},
            upsert=True,
        )
        if result.upserted_id is not None:
            created += 1
        elif result.modified_count > 0:
            updated += 1

    try:
        from . import admin_channels
        admin_channels._invalidate_cache(str(company_id))
    except ImportError:
        pass

    return {
        "status": "ok",
        "created": created,
        "updated": updated,
        "total": len(channels),
    }

