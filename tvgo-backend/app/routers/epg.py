"""
EPG (Electronic Program Guide) Management Router
Handles downloading, parsing, and mapping EPG data from external XML sources
"""

from fastapi import APIRouter, HTTPException, BackgroundTasks, UploadFile, File, Depends
from pydantic import BaseModel
from typing import Optional, List, Dict, Any
from datetime import datetime
import urllib.request
import xml.etree.ElementTree as ET
import os
import re
import shutil
from pymongo.database import Database

# Fix: Import proper auth dependency and alias it
from ..auth import get_current_active_admin as require_admin
from ..database import get_db
from ..aws_s3 import upload_image_from_url

router = APIRouter(prefix="/api/admin/epg", tags=["EPG Management"])


# --- Models ---

class EPGSource(BaseModel):
    id: Optional[str] = None
    name: str
    url: str
    enabled: bool = True
    priority: int = 1
    description: Optional[str] = None
    last_sync: Optional[datetime] = None
    channel_count: Optional[int] = 0


class EPGSourceCreate(BaseModel):
    name: str
    url: str
    enabled: bool = True
    priority: int = 1
    description: Optional[str] = None


class EPGChannel(BaseModel):
    id: str
    display_name: str
    icon_url: Optional[str] = None
    lang: str = "ru"


class EPGProgram(BaseModel):
    channel_id: str
    title: str
    start: datetime
    stop: datetime
    description: Optional[str] = None
    category: Optional[str] = None


class ChannelMapping(BaseModel):
    channel_id: str
    epg_channel_id: str


class EPGParseResponse(BaseModel):
    channels: List[EPGChannel]
    programs_count: int
    source_url: Optional[str] = None


class EPGSyncResult(BaseModel):
    status: str
    channels_parsed: int
    programs_parsed: int
    mappings_applied: int
    errors: List[str] = []


# --- EPG Cache Directory ---
EPG_CACHE_DIR = "/tmp/epg_cache"
os.makedirs(EPG_CACHE_DIR, exist_ok=True)


# --- Helper Functions ---

def parse_xmltv_date(date_str: str) -> datetime:
    """Convert XMLTV date format to datetime object using standard library"""
    try:
        # XMLTV format: YYYYMMDDhhmmss +0000
        return datetime.strptime(date_str, "%Y%m%d%H%M%S %z")
    except Exception:
        try:
            # Try parsing just the date part if format is slightly diff or no space
            # normalize "20080715003000+0200" -> "20080715003000 +0200"
            if '+' in date_str and ' ' not in date_str:
                # Insert space before +
                idx = date_str.find('+')
                date_str = date_str[:idx] + ' ' + date_str[idx:]
                return datetime.strptime(date_str, "%Y%m%d%H%M%S %z")
            
            # Use split to ignore timezone or garbage
            clean_date = date_str.split()[0]
            if '+' in clean_date: clean_date = clean_date.split('+')[0]
            if '-' in clean_date: clean_date = clean_date.split('-')[0] # risky if YYYY-MM
             
            return datetime.strptime(clean_date, "%Y%m%d%H%M%S")
        except:
            return datetime.utcnow()


def download_epg(url: str, force: bool = False) -> str:
    """Download EPG XML from URL with caching"""
    cache_file = os.path.join(
        EPG_CACHE_DIR,
        re.sub(r'[^\w]', '_', url)[-50:] + ".xml"
    )
    
    if not force and os.path.exists(cache_file):
        file_age = datetime.now().timestamp() - os.path.getmtime(cache_file)
        if file_age < 86400:
            return cache_file
    
    try:
        req = urllib.request.Request(
            url,
            headers={'User-Agent': 'Mozilla/5.0 (compatible; tvGO-EPG/1.0)'}
        )
        with urllib.request.urlopen(req, timeout=120) as response:
            content = response.read()
        
        with open(cache_file, 'wb') as f:
            f.write(content)
        return cache_file
    except Exception as e:
        if os.path.exists(cache_file):
            return cache_file
        raise HTTPException(status_code=500, detail=f"Failed to download EPG: {str(e)}")


def parse_epg_xml(xml_path: str) -> tuple[List[EPGChannel], List[EPGProgram]]:
    """Parse EPG XML file and return channels and programs"""
    try:
        context = ET.iterparse(xml_path, events=('end',))
        channels = []
        programs = []
        
        for event, elem in context:
            if elem.tag == 'channel':
                channel_id = elem.get('id')
                display_name_elem = elem.find('display-name')
                icon_elem = elem.find('icon')
                if channel_id and display_name_elem is not None:
                    channels.append(EPGChannel(
                        id=channel_id,
                        display_name=display_name_elem.text or "",
                        icon_url=icon_elem.get('src') if icon_elem is not None else None,
                        lang=display_name_elem.get('lang', 'ru')
                    ))
                elem.clear()
            elif elem.tag == 'programme':
                channel_id = elem.get('channel')
                start = elem.get('start')
                stop = elem.get('stop')
                title_elem = elem.find('title')
                desc_elem = elem.find('desc')
                category_elem = elem.find('category')
                
                if channel_id and start and title_elem is not None:
                    programs.append(EPGProgram(
                        channel_id=channel_id,
                        title=title_elem.text or "",
                        start=parse_xmltv_date(start),
                        stop=parse_xmltv_date(stop) if stop else parse_xmltv_date(start),
                        description=desc_elem.text if desc_elem is not None else None,
                        category=category_elem.text if category_elem is not None else None
                    ))
                elem.clear()
        return channels, programs
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Failed to parse XML: {str(e)}")


def calculate_similarity(s1: str, s2: str) -> float:
    s1 = s1.lower().strip()
    s2 = s2.lower().strip()
    if s1 == s2: return 1.0
    if s1 in s2 or s2 in s1: return 0.8
    words1, words2 = set(s1.split()), set(s2.split())
    if words1 and words2:
        return len(words1 & words2) / len(words1 | words2)
    return 0.0


# --- Endpoints ---

@router.get("/sources", dependencies=[Depends(require_admin)])
async def list_epg_sources(db: Database = Depends(get_db)):
    sources = list(db["epg_sources"].find())
    for s in sources:
        s["id"] = s.pop("_id")
    return {"sources": sources, "total": len(sources)}


@router.post("/sources", dependencies=[Depends(require_admin)])
async def create_epg_source(source: EPGSourceCreate, db: Database = Depends(get_db)):
    source_id = re.sub(r'[^\w]', '_', source.name.lower())[:20]
    item = source.dict()
    item["_id"] = source_id
    item["created_at"] = datetime.utcnow()
    item["channel_count"] = 0
    item["last_sync"] = None
    
    try:
        db["epg_sources"].update_one(
            {"_id": source_id}, 
            {"$set": item}, 
            upsert=True
        )
        item["id"] = source_id
        del item["_id"]
        return item
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to create source: {str(e)}")


@router.delete("/sources/{source_id}", dependencies=[Depends(require_admin)])
async def delete_epg_source(source_id: str, db: Database = Depends(get_db)):
    result = db["epg_sources"].delete_one({"_id": source_id})
    if result.deleted_count == 0:
        raise HTTPException(status_code=404, detail="Source not found")
    return {"status": "deleted", "id": source_id}


@router.post("/preview", dependencies=[Depends(require_admin)])
async def preview_epg_url(url: str, force: bool = False):
    xml_path = download_epg(url, force)
    channels, programs = parse_epg_xml(xml_path)
    return EPGParseResponse(
        channels=channels[:100],
        programs_count=len(programs),
        source_url=url
    )


def save_programs_to_mongo(programs: List[EPGProgram], db: Database):
    """Save programs to MongoDB"""
    docs = []
    for p in programs:
        docs.append({
            "channel_id": p.channel_id,
            "program_id": f"{p.channel_id}_{p.start.timestamp()}",
            "title": p.title,
            "start": p.start,
            "end": p.stop,
            "description": p.description,
            "category": p.category,
            "is_live": False
        })
    
    if docs:
        try:
            db["epg_programs"].insert_many(docs, ordered=False)
        except Exception:
            pass 


@router.post("/sync", dependencies=[Depends(require_admin)])
async def sync_epg(
    source_id: Optional[str] = None,
    url: Optional[str] = None,
    force: bool = False,
    background_tasks: BackgroundTasks = None,
    db: Database = Depends(get_db)
):
    if not source_id and not url:
        raise HTTPException(status_code=400, detail="Provide either source_id or url")
    
    errors = []
    if source_id:
        source = db["epg_sources"].find_one({"_id": source_id})
        if not source: raise HTTPException(status_code=404, detail="Source not found")
        url = source.get('url')
    
    try:
        xml_path = download_epg(url, force)
        epg_channels, programs = parse_epg_xml(xml_path)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to parse EPG: {str(e)}")

    mappings_applied = 0
    our_channels = list(db["channels"].find())
    
    # Auto Map
    for channel in our_channels:
        channel_name = channel.get('name', '').lower()
        if not channel_name: continue
        
        best_match = None
        best_score = 0
        for epg_ch in epg_channels:
            score = calculate_similarity(channel_name, epg_ch.display_name)
            if score > best_score:
                best_score = score
                best_match = epg_ch
        
        if best_match and best_score >= 0.8:
            update_fields = {"epg_id": best_match.id}
            
            # Download logo from EPG and upload to S3 if channel doesn't have one
            if not channel.get('logo') and best_match.icon_url:
                try:
                    s3_logo_url = await upload_image_from_url(best_match.icon_url, prefix="channel-logos")
                    if s3_logo_url:
                        update_fields["logo"] = s3_logo_url
                        print(f"Uploaded logo for {channel.get('name')}: {s3_logo_url}")
                except Exception as e:
                    print(f"Failed to upload logo for {channel.get('name')}: {e}")
                    # Fallback to original URL
                    update_fields["logo"] = best_match.icon_url
            
            db["channels"].update_one(
                {"_id": channel["_id"]},
                {"$set": update_fields}
            )
            mappings_applied += 1

    # Save Programs
    if background_tasks:
        background_tasks.add_task(save_programs_to_mongo, programs, db)
    else:
        save_programs_to_mongo(programs, db)

    if source_id:
        db["epg_sources"].update_one(
            {"_id": source_id},
            {"$set": {"last_sync": datetime.utcnow(), "channel_count": len(epg_channels)}}
        )

    return EPGSyncResult(
        status="completed",
        channels_parsed=len(epg_channels),
        programs_parsed=len(programs),
        mappings_applied=mappings_applied,
        errors=errors
    )


@router.post("/upload", dependencies=[Depends(require_admin)])
async def upload_epg_file(
    file: UploadFile = File(...), 
    background_tasks: BackgroundTasks = None,
    db: Database = Depends(get_db)
):
    file_path = os.path.join(EPG_CACHE_DIR, f"upload_{datetime.now().strftime('%Y%m%d%H%M%S')}.xml")
    try:
        with open(file_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
            
        epg_channels, programs = parse_epg_xml(file_path)
        
        if background_tasks:
            background_tasks.add_task(save_programs_to_mongo, programs, db)
        else:
            save_programs_to_mongo(programs, db)
            
        # Also perform mapping
        mappings_applied = 0
        our_channels = list(db["channels"].find())
        for channel in our_channels:
            channel_name = channel.get('name', '').lower()
            if not channel_name: continue
            best_match = None
            best_score = 0
            for epg_ch in epg_channels:
                score = calculate_similarity(channel_name, epg_ch.display_name)
                if score > best_score:
                    best_score = score
                    best_match = epg_ch
            if best_match and best_score >= 0.8:
                update_fields = {"epg_id": best_match.id}
                
                # Download logo from EPG and upload to S3 if channel doesn't have one
                if not channel.get('logo') and best_match.icon_url:
                    try:
                        s3_logo_url = await upload_image_from_url(best_match.icon_url, prefix="channel-logos")
                        if s3_logo_url:
                            update_fields["logo"] = s3_logo_url
                            print(f"Uploaded logo for {channel.get('name')}: {s3_logo_url}")
                    except Exception as e:
                        print(f"Failed to upload logo for {channel.get('name')}: {e}")
                        # Fallback to original URL
                        update_fields["logo"] = best_match.icon_url
                
                db["channels"].update_one({"_id": channel["_id"]}, {"$set": update_fields})
                mappings_applied += 1
        
        return {
            "status": "uploaded", 
            "channels": len(epg_channels), 
            "programs": len(programs),
            "mapped": mappings_applied,
            "message": "File processed."
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to upload: {str(e)}")


@router.get("/channels", dependencies=[Depends(require_admin)])
async def list_epg_channels(url: Optional[str] = None):
    if url:
        xml_path = download_epg(url, force=False)
        channels, _ = parse_epg_xml(xml_path)
        return {"channels": channels, "total": len(channels)}
    return {"channels": [], "total": 0}


@router.get("/mappings", dependencies=[Depends(require_admin)])
async def list_channel_mappings(db: Database = Depends(get_db)):
    channels = list(db["channels"].find({}, {"name": 1, "epg_id": 1}))
    mappings = [
        {
            "channel_id": c["_id"],
            "channel_name": c.get("name"),
            "epg_id": c.get("epg_id"),
            "has_mapping": bool(c.get("epg_id"))
        }
        for c in channels
    ]
    return {
        "mappings": mappings, 
        "total": len(channels), 
        "mapped_count": sum(1 for m in mappings if m['has_mapping'])
    }


@router.post("/mappings", dependencies=[Depends(require_admin)])
async def set_channel_mapping(mapping: ChannelMapping, db: Database = Depends(get_db)):
    db["channels"].update_one(
        {"_id": mapping.channel_id},
        {"$set": {"epg_id": mapping.epg_channel_id}}
    )
    return {"status": "mapped", "channel_id": mapping.channel_id, "epg_id": mapping.epg_channel_id}
