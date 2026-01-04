from datetime import datetime, date
from typing import List, Optional, Dict, Any, Literal
from pydantic import BaseModel, HttpUrl, ConfigDict


# ---- Auth ----

class TokenData(BaseModel):
    username: Optional[str] = None


class UserBase(BaseModel):
    username: str
    is_active: bool = True
    is_admin: bool = True
    display_name: Optional[str] = None
    avatar_url: Optional[HttpUrl] = None


class UserInDB(UserBase):
    password_hash: str


class UserCreate(BaseModel):
    username: str
    password: str


class UserLogin(BaseModel):
    username: str
    password: str


class UserProfile(BaseModel):
    id: str
    username: str
    displayName: Optional[str] = None
    avatarUrl: Optional[HttpUrl] = None


class LoginResponse(BaseModel):
    accessToken: str
    refreshToken: str
    tokenType: str = "Bearer"
    expiresIn: int
    user: UserProfile


class RefreshRequest(BaseModel):
    refreshToken: str


class RefreshResponse(BaseModel):
    accessToken: str
    tokenType: str = "Bearer"
    expiresIn: int


class ErrorResponse(BaseModel):
    error: Dict[str, str]


# ---- Config / Branding ----

class Brand(BaseModel):
    appName: str
    logoUrl: Optional[str] = None  # Changed from HttpUrl to allow relative URLs
    accentColor: str
    backgroundColor: str


class Features(BaseModel):
    enableFavorites: bool
    enableSearch: bool
    autoplayPreview: bool
    enableLiveTv: bool = True
    enableVod: bool = True


class RailQuery(BaseModel):
    group: Optional[str] = None
    genre: Optional[str] = None
    sort: Optional[str] = None
    limit: Optional[int] = None
    favorite: Optional[bool] = None


class RailConfig(BaseModel):
    id: str
    title: str
    type: Literal["movie_hero", "channel_hero", "movies_row", "channels_row"]
    query: Dict[str, Any]


class ImagePolicy(BaseModel):
    posterAspect: str
    landscapeAspect: str
    heroMinWidth: int


class ConfigResponse(BaseModel):
    brand: Brand
    features: Features
    channelGroups: List[str]
    movieGenres: List[str]


# ---- Channels / EPG ----

class ProgramScheduleItem(BaseModel):
    id: Optional[str] = None
    title: str
    category: Optional[str] = None
    start: datetime
    end: datetime
    isLive: bool


class Channel(BaseModel):
    id: str
    name: str
    group: Optional[str] = None
    category: Optional[str] = None # Alias for group for Android App
    description: Optional[str] = None # From metadata
    logoColor: Optional[str] = "#000000" # Default for App
    logo: Optional[str] = None  # Changed from HttpUrl to allow relative URLs
    streamUrl: HttpUrl
    drm: Optional[Dict[str, Any]] = None
    lang: Optional[List[str]] = None
    country: Optional[str] = None
    programSchedule: Optional[List[ProgramScheduleItem]] = None
    badges: Optional[List[str]] = None
    metadata: Optional[Dict[str, Any]] = None
    streamerName: Optional[str] = None
    order: Optional[int] = None

    model_config = ConfigDict(from_attributes=True)


class ChannelsListResponse(BaseModel):
    total: int
    items: List[Channel]
    nextOffset: Optional[int] = None


class EpgProgramItem(BaseModel):
    id: Optional[str] = None
    title: str
    category: Optional[str] = None
    description: Optional[str] = None
    season: Optional[int] = None
    episode: Optional[int] = None
    start: datetime
    end: datetime
    isLive: bool


class EpgResponse(BaseModel):
    channelId: str
    date: Optional[date] = None
    items: List[EpgProgramItem]
    nextOffset: Optional[int] = None


# ---- Favorites ----


class FavoritesResponse(BaseModel):
    channels: List[str]
    movies: List[str]


# ---- Movies ----

class Movie(BaseModel):
    id: str
    title: str
    year: Optional[int] = None
    genres: Optional[List[str]] = None
    rating: Optional[float] = None
    runtimeMinutes: Optional[int] = None
    synopsis: Optional[str] = None
    order: Optional[int] = None

    images: Optional[Dict[str, Optional[HttpUrl]]] = None
    media: Optional[Dict[str, Any]] = None
    badges: Optional[List[str]] = None
    credits: Optional[Dict[str, List[str]]] = None
    availability: Optional[Dict[str, Optional[datetime]]] = None

    model_config = ConfigDict(from_attributes=True)


class MoviesListResponse(BaseModel):
    total: int
    items: List[Movie]
    nextOffset: Optional[int] = None


# ---- Rails public ----

class RailPublic(BaseModel):
    id: str
    title: str
    type: str
    query: Dict[str, Any]

    model_config = ConfigDict(from_attributes=True)


# ---- Admin schemas ----

class ChannelCreate(BaseModel):
    id: str
    name: str
    group: Optional[str] = None
    logo_url: Optional[HttpUrl] = None
    stream_url: HttpUrl
    drm_type: Optional[str] = None
    drm_license_url: Optional[HttpUrl] = None
    lang: Optional[List[str]] = None
    country: Optional[str] = None
    badges: Optional[List[str]] = None
    metadata: Optional[Dict[str, Any]] = None
    streamer_name: Optional[str] = None
    order: Optional[int] = None


class ChannelReorderItem(BaseModel):
    id: str
    order: int


class ChannelReorderRequest(BaseModel):
    items: List[ChannelReorderItem]


class ChannelUpdate(BaseModel):
    name: Optional[str] = None
    group: Optional[str] = None
    logo_url: Optional[str] = None  # Changed from HttpUrl to allow relative URLs
    stream_url: Optional[HttpUrl] = None
    drm_type: Optional[str] = None
    drm_license_url: Optional[HttpUrl] = None
    lang: Optional[List[str]] = None
    country: Optional[str] = None
    badges: Optional[List[str]] = None
    metadata: Optional[Dict[str, Any]] = None
    streamer_name: Optional[str] = None
    order: Optional[int] = None


class MovieCreate(BaseModel):
    id: str
    title: str
    year: Optional[int] = None
    genres: Optional[List[str]] = None
    rating: Optional[float] = None
    runtime_minutes: Optional[int] = None
    synopsis: Optional[str] = None
    poster_url: Optional[HttpUrl] = None
    landscape_url: Optional[HttpUrl] = None
    hero_url: Optional[HttpUrl] = None
    stream_url: Optional[str] = None  # Accept any URL format (http, rtsp, hls, etc.)
    trailer_url: Optional[HttpUrl] = None
    drm_type: Optional[str] = None
    drm_license_url: Optional[HttpUrl] = None
    badges: Optional[List[str]] = None
    directors: Optional[List[str]] = None
    cast: Optional[List[str]] = None
    availability_start: Optional[datetime] = None
    availability_end: Optional[datetime] = None
    order: Optional[int] = None


class MovieUpdate(BaseModel):
    title: Optional[str] = None
    year: Optional[int] = None
    genres: Optional[List[str]] = None
    rating: Optional[float] = None
    runtime_minutes: Optional[int] = None
    synopsis: Optional[str] = None
    poster_url: Optional[HttpUrl] = None
    landscape_url: Optional[HttpUrl] = None
    hero_url: Optional[HttpUrl] = None
    stream_url: Optional[str] = None  # Accept any URL format
    trailer_url: Optional[HttpUrl] = None
    drm_type: Optional[str] = None
    drm_license_url: Optional[HttpUrl] = None
    badges: Optional[List[str]] = None
    directors: Optional[List[str]] = None
    cast: Optional[List[str]] = None
    availability_start: Optional[datetime] = None
    availability_end: Optional[datetime] = None
    order: Optional[int] = None

class MovieReorderItem(BaseModel):
    id: str
    order: int

class MovieReorderRequest(BaseModel):
    items: List[MovieReorderItem]


class RailAdminCreate(BaseModel):
    id: str
    title: str
    type: str
    query: Dict[str, Any]
    sort_order: int = 0


class RailAdminUpdate(BaseModel):
    title: Optional[str] = None
    type: Optional[str] = None
    query: Optional[Dict[str, Any]] = None
    sort_order: Optional[int] = None


# ---- Streamers ----

class StreamerBase(BaseModel):
    name: str
    url: str


class StreamerCreate(StreamerBase):
    pass


class StreamerResponse(BaseModel):
    id: str
    name: str
    url: str
    status: str
    last_sync: Optional[datetime] = None
    channel_count: int = 0

    model_config = ConfigDict(from_attributes=True)


class StreamerListResponse(BaseModel):
    items: List[StreamerResponse]
    total: int


# ---- M3U URL Ingestion ----

class M3UUrlIngestRequest(BaseModel):
    url: str
    streamer_name: Optional[str] = None


class M3UChannelPreview(BaseModel):
    id: str
    name: str
    group: Optional[str] = None
    logo_url: Optional[str] = None
    stream_url: str


class M3UParseResponse(BaseModel):
    channels: List[M3UChannelPreview]
    total: int


class M3UIngestRequest(BaseModel):
    url: str
    streamer_name: Optional[str] = None
    channel_ids: Optional[List[str]] = None  # If None, ingest all


# ---- Subscriber Users ----

class DeviceInfo(BaseModel):
    mac_address: str
    device_name: Optional[str] = None
    last_seen: Optional[datetime] = None
    first_seen: Optional[datetime] = None


from enum import Enum

class UserStatus(str, Enum):
    ACTIVE = "active"
    INACTIVE = "inactive"
    EXPIRED = "expired"
    BONUS = "bonus"
    TEST = "test"

# ... (keep existing imports if needed, but this is inside the file)

class SubscriberBase(BaseModel):
    display_name: Optional[str] = None
    surname: Optional[str] = None
    building: Optional[str] = None
    address: Optional[str] = None
    client_no: Optional[str] = None
    package_ids: Optional[List[str]] = []
    
    # Replaces simple boolean, but we keep is_active for backward compatibility in response
    status: UserStatus = UserStatus.ACTIVE 
    is_active: Optional[bool] = True # Deprecated input, calculated in output except for updates
    max_devices: int = 1


class SubscriberCreate(SubscriberBase):
    """Create subscriber by MAC address"""
    mac_address: str


class SubscriberCreateCredentials(SubscriberBase):
    """Create subscriber with auto-generated username/password"""
    pass


class SubscriberUpdate(SubscriberBase):
    status: Optional[UserStatus] = None
    is_active: Optional[bool] = None # Allow updating legacy field to map to status if needed
    max_devices: Optional[int] = None
    display_name: Optional[str] = None
    surname: Optional[str] = None
    building: Optional[str] = None
    address: Optional[str] = None
    client_no: Optional[str] = None
    package_ids: Optional[List[str]] = None
    # All base fields are optional/updateable


class SubscriberResponse(SubscriberBase):
    id: str
    username: Optional[str] = None
    password: Optional[str] = None  # Visible plain password for admins
    mac_address: Optional[str] = None
    
    status: UserStatus = UserStatus.ACTIVE
    is_active: bool = True # Computed property
    
    package_ids: List[str] = []
    max_devices: int = 1
    devices: List[DeviceInfo] = []
    created_at: Optional[datetime] = None
    last_login: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


class SubscriberCreateResponse(SubscriberResponse):
    """Response when creating subscriber with credentials"""
    pass


class SubscriberListResponse(BaseModel):
    items: List[SubscriberResponse]
    total: int


class SubscriberLogin(BaseModel):
    """Login can be by username/password or MAC address"""
    username: Optional[str] = None
    password: Optional[str] = None
    mac_address: Optional[str] = None  # Current device MAC
    device_name: Optional[str] = None


class SubscriberLoginResponse(BaseModel):
    accessToken: str
    refreshToken: str
    tokenType: str = "Bearer"
    expiresIn: int
    subscriber: SubscriberResponse
    config: Optional[ConfigResponse] = None


# ---- User Groups ----

class UserGroupBase(BaseModel):
    name: str
    description: Optional[str] = None


class UserGroupCreate(UserGroupBase):
    user_ids: List[str] = []


class UserGroupUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    user_ids: Optional[List[str]] = None


class UserGroupResponse(UserGroupBase):
    id: str
    user_ids: List[str] = []
    user_count: int = 0
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    model_config = ConfigDict(from_attributes=True)


class UserGroupListResponse(BaseModel):
    items: List[UserGroupResponse]
    total: int


# ---- Messages ----

class MessageTargetType(str, Enum):
    ALL = "all"
    GROUPS = "groups"
    USERS = "users"


class MessageBase(BaseModel):
    title: str
    body: str
    url: Optional[str] = None  # If provided, Android app shows QR code


class MessageCreate(MessageBase):
    target_type: MessageTargetType = MessageTargetType.ALL
    target_ids: List[str] = []  # Group IDs or User IDs based on target_type


class MessageResponse(MessageBase):
    id: str
    target_type: MessageTargetType
    target_ids: List[str] = []
    is_active: bool = True
    created_at: Optional[datetime] = None
    read_by: List[str] = []  # User IDs who have read this message

    model_config = ConfigDict(from_attributes=True)


class MessageListResponse(BaseModel):
    items: List[MessageResponse]
    total: int


class SubscriberMessageResponse(BaseModel):
    """Message as seen by subscriber (without admin fields)"""
    id: str
    title: str
    body: str
    url: Optional[str] = None
    created_at: Optional[datetime] = None
    is_read: bool = False

    model_config = ConfigDict(from_attributes=True)


class SubscriberMessagesListResponse(BaseModel):
    items: List[SubscriberMessageResponse]
    total: int
    unread_count: int = 0


# ---- Games ----

class Game(BaseModel):
    id: str
    name: str
    description: Optional[str] = None
    imageUrl: Optional[str] = None
    gameUrl: str  # Fullscreen game URL (e.g., Poki embed URL)
    category: Optional[str] = None
    isActive: bool = True
    order: Optional[int] = None

    model_config = ConfigDict(from_attributes=True)


class GameCreate(BaseModel):
    id: str
    name: str
    description: Optional[str] = None
    image_url: Optional[str] = None
    game_url: str
    category: Optional[str] = None
    is_active: bool = True
    order: Optional[int] = None


class GameUpdate(BaseModel):
    name: Optional[str] = None
    description: Optional[str] = None
    image_url: Optional[str] = None
    game_url: Optional[str] = None
    category: Optional[str] = None
    is_active: Optional[bool] = None
    order: Optional[int] = None


class GameReorderItem(BaseModel):
    id: str
    order: int


class GameReorderRequest(BaseModel):
    items: List[GameReorderItem]


class GamesListResponse(BaseModel):
    total: int
    items: List[Game]
    categories: List[str] = []
