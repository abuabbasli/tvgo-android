"""
TMDB API Client for fetching movie metadata.
https://developer.themoviedb.org/docs
"""
import httpx
from typing import Optional
from pydantic import BaseModel
import os

# TMDB Configuration
TMDB_API_KEY = os.environ.get("TMDB_API_KEY", "")
TMDB_BASE_URL = "https://api.themoviedb.org/3"
TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p"


class TMDBMovieResult(BaseModel):
    id: int
    title: str
    original_title: Optional[str] = None
    overview: Optional[str] = None
    release_date: Optional[str] = None
    vote_average: Optional[float] = None
    runtime: Optional[int] = None
    poster_path: Optional[str] = None
    backdrop_path: Optional[str] = None
    genres: list[dict] = []
    
    
class TMDBCredits(BaseModel):
    cast: list[dict] = []
    crew: list[dict] = []


class TMDBVideo(BaseModel):
    key: str
    site: str
    type: str
    name: str


class TMDBEnrichedData(BaseModel):
    """Enriched movie data from TMDB"""
    tmdb_id: int
    title: str
    original_title: Optional[str] = None
    year: Optional[int] = None
    runtime_minutes: Optional[int] = None
    rating: Optional[float] = None
    synopsis: Optional[str] = None
    genres: list[str] = []
    poster_url: Optional[str] = None
    landscape_url: Optional[str] = None  # backdrop
    hero_url: Optional[str] = None       # high-res backdrop
    trailer_url: Optional[str] = None
    directors: list[str] = []
    cast: list[str] = []


async def search_movie(query: str, year: Optional[int] = None) -> Optional[dict]:
    """Search for a movie by title"""
    if not TMDB_API_KEY:
        raise ValueError("TMDB_API_KEY not configured")
    
    params = {
        "api_key": TMDB_API_KEY,
        "query": query,
        "include_adult": "false"
    }
    if year:
        params["year"] = str(year)
    
    async with httpx.AsyncClient() as client:
        response = await client.get(f"{TMDB_BASE_URL}/search/movie", params=params)
        response.raise_for_status()
        data = response.json()
        
        if data.get("results"):
            return data["results"][0]
        return None


async def get_movie_details(tmdb_id: int) -> Optional[dict]:
    """Get detailed movie info"""
    if not TMDB_API_KEY:
        raise ValueError("TMDB_API_KEY not configured")
    
    async with httpx.AsyncClient() as client:
        response = await client.get(
            f"{TMDB_BASE_URL}/movie/{tmdb_id}",
            params={"api_key": TMDB_API_KEY}
        )
        response.raise_for_status()
        return response.json()


async def get_movie_credits(tmdb_id: int) -> dict:
    """Get cast and crew"""
    if not TMDB_API_KEY:
        raise ValueError("TMDB_API_KEY not configured")
    
    async with httpx.AsyncClient() as client:
        response = await client.get(
            f"{TMDB_BASE_URL}/movie/{tmdb_id}/credits",
            params={"api_key": TMDB_API_KEY}
        )
        response.raise_for_status()
        return response.json()


async def get_movie_videos(tmdb_id: int) -> list[dict]:
    """Get trailers and videos"""
    if not TMDB_API_KEY:
        raise ValueError("TMDB_API_KEY not configured")
    
    async with httpx.AsyncClient() as client:
        response = await client.get(
            f"{TMDB_BASE_URL}/movie/{tmdb_id}/videos",
            params={"api_key": TMDB_API_KEY}
        )
        response.raise_for_status()
        data = response.json()
        return data.get("results", [])


def build_image_url(path: Optional[str], size: str = "w500") -> Optional[str]:
    """Build full image URL from TMDB path"""
    if not path:
        return None
    return f"{TMDB_IMAGE_BASE}/{size}{path}"


def build_youtube_url(key: str) -> str:
    """Build YouTube URL from video key"""
    return f"https://www.youtube.com/watch?v={key}"


async def enrich_movie(title: str, year: Optional[int] = None) -> Optional[TMDBEnrichedData]:
    """
    Search and fetch all metadata for a movie.
    Returns enriched data ready to save to database.
    """
    # Search for movie
    search_result = await search_movie(title, year)
    if not search_result:
        return None
    
    tmdb_id = search_result["id"]
    
    # Fetch detailed info
    details = await get_movie_details(tmdb_id)
    if not details:
        return None
    
    # Fetch credits
    credits = await get_movie_credits(tmdb_id)
    
    # Fetch videos for trailer
    videos = await get_movie_videos(tmdb_id)
    
    # Extract trailer (prefer official YouTube trailers)
    trailer_url = None
    for video in videos:
        if video.get("site") == "YouTube" and video.get("type") in ["Trailer", "Teaser"]:
            trailer_url = build_youtube_url(video["key"])
            break
    
    # Extract directors
    directors = [
        crew["name"] for crew in credits.get("crew", [])
        if crew.get("job") == "Director"
    ][:3]  # Limit to 3
    
    # Extract top cast
    cast = [
        actor["name"] for actor in credits.get("cast", [])
    ][:10]  # Top 10 actors
    
    # Extract year from release date
    release_year = None
    if details.get("release_date"):
        try:
            release_year = int(details["release_date"][:4])
        except (ValueError, IndexError):
            pass
    
    # Extract genres
    genres = [g["name"] for g in details.get("genres", [])]
    
    return TMDBEnrichedData(
        tmdb_id=tmdb_id,
        title=details.get("title", title),
        original_title=details.get("original_title"),
        year=release_year or year,
        runtime_minutes=details.get("runtime"),
        rating=details.get("vote_average"),
        synopsis=details.get("overview"),
        genres=genres,
        poster_url=build_image_url(details.get("poster_path"), "w500"),
        landscape_url=build_image_url(details.get("backdrop_path"), "w780"),
        hero_url=build_image_url(details.get("backdrop_path"), "original"),
        trailer_url=trailer_url,
        directors=directors,
        cast=cast
    )
