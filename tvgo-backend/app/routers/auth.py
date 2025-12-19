from datetime import datetime, timedelta

from fastapi import APIRouter, Depends
from pymongo.database import Database

from .. import schemas
from ..auth import (
    authenticate_user,
    create_access_token,
    create_refresh_token,
    get_current_user,
    get_password_hash,
    get_refresh_token,
    verify_password,
)
from ..config import settings
from ..database import get_db
from ..errors import unauthorized, forbidden

router = APIRouter(prefix=f"{settings.api_v1_prefix}/auth", tags=["auth"])


def _subscriber_document_to_schema(doc: dict) -> schemas.SubscriberResponse:
    # Importing here to avoid circular imports if any, or just helper
    return schemas.SubscriberResponse(
        id=doc["_id"],
        username=doc.get("username"),
        mac_address=doc.get("mac_address"),
        display_name=doc.get("display_name"),
        is_active=doc.get("is_active", True),
        package_ids=doc.get("package_ids", []),
        max_devices=doc.get("max_devices", 1),
        devices=doc.get("devices", []),
        created_at=doc.get("created_at"),
        last_login=doc.get("last_login"),
    )


@router.post("/subscriber/login", response_model=schemas.SubscriberLoginResponse)
def login_subscriber(
    payload: schemas.SubscriberLogin,
    db: Database = Depends(get_db),
):
    # 1. Authenticate
    subscriber = None
    if payload.username and payload.password:
        subscriber = db["subscribers"].find_one({"username": payload.username})
        if not subscriber or not verify_password(payload.password, subscriber.get("password_hash", "")):
             raise unauthorized("Invalid username or password", code="INVALID_CREDENTIALS")
    elif payload.mac_address:
        # Login by MAC only (if supported/for initial MAC creation users)
        subscriber = db["subscribers"].find_one({"mac_address": payload.mac_address})
        if not subscriber:
             raise unauthorized("Device not registered", code="INVALID_DEVICE")
    else:
        raise unauthorized("Username/Password or MAC required", code="INVALID_REQUEST")

    # Status Check
    status = subscriber.get("status")
    # Allowed statuses: active, bonus, test
    allowed_statuses = ["active", "bonus", "test"]
    
    if status and status not in allowed_statuses:
         raise forbidden(f"Account status is {status}", code="ACCOUNT_INACTIVE")
    elif not status and not subscriber.get("is_active", True):
         # Legacy fallback
         raise forbidden("Account is inactive", code="ACCOUNT_INACTIVE")

    # 2. Check Device / MAC
    current_mac = payload.mac_address
    if not current_mac:
        # If logging in with username/pass but no MAC sent, we can't track device
        # The prompt says "we should receive also user mac address"
        raise unauthorized("MAC address required for device tracking", code="MAC_REQUIRED")

    devices = subscriber.get("devices", [])
    device_entry = next((d for d in devices if d["mac_address"] == current_mac), None)

    now = datetime.utcnow()

    if device_entry:
        # Known device, update last seen
        db["subscribers"].update_one(
            {"_id": subscriber["_id"], "devices.mac_address": current_mac},
            {
                "$set": {
                    "devices.$.last_seen": now,
                    "last_login": now,
                    "devices.$.device_name": payload.device_name or device_entry.get("device_name")
                }
            }
        )
    else:
        # New device
        if len(devices) >= subscriber.get("max_devices", 1):
            raise forbidden("Device limit reached", code="DEVICE_LIMIT_REACHED")
        
        new_device = {
            "mac_address": current_mac,
            "device_name": payload.device_name or "Unknown Device",
            "first_seen": now,
            "last_seen": now
        }
        db["subscribers"].update_one(
            {"_id": subscriber["_id"]},
            {
                "$push": {"devices": new_device},
                "$set": {"last_login": now}
            }
        )
        # Update local subscriber object for response
        devices.append(new_device)
        subscriber["devices"] = devices

    # 3. Issue Token
    # We use a special prefix or claim to distinguish subscribers from admins?
    # Or just use "sub": username.
    # Users have generated usernames, Admins have "admin".
    # Conflict risk: low if admins are specialized.
    # Ideally use "role": "subscriber" or "scope".
    
    access_token_expires = timedelta(minutes=settings.access_token_expire_minutes * 24 * 30) # Long expiration for TV
    access_token = create_access_token(
        data={"sub": subscriber.get("username") or subscriber["_id"], "role": "subscriber", "id": subscriber["_id"]},
        expires_delta=access_token_expires
    )
    refresh_token, _ = create_refresh_token(db, subscriber.get("username") or subscriber["_id"])
    
    # Fetch Config for App (Logo, Colors, etc.)
    brand_doc = db["brand_config"].find_one({"_id": "brand"}) or {}
    
    brand = schemas.Brand(
        appName=brand_doc.get("app_name", "tvGO"),
        logoUrl=brand_doc.get("logo_url", "https://cdn.tvgo.cloud/brand/tvgo-logo.png"),
        accentColor=brand_doc.get("accent_color", "#1EA7FD"),
        backgroundColor=brand_doc.get("background_color", "#050607"),
    )
    
    features = schemas.Features(
         enableFavorites=brand_doc.get("enable_favorites", True),
         enableSearch=brand_doc.get("enable_search", True),
         autoplayPreview=brand_doc.get("autoplay_preview", True),
    )
    
    config_response = schemas.ConfigResponse(
        brand=brand,
        features=features,
        channelGroups=brand_doc.get("channel_groups", ["All", "Favorites", "Kids", "Sports", "News", "Entertainment", "Movies"]),
        movieGenres=brand_doc.get("movie_genres", ["All", "Action", "Comedy", "Drama", "Kids & Family", "Sci-Fi", "Thriller", "Horror", "Romance"]),
    )

    return schemas.SubscriberLoginResponse(
        accessToken=access_token,
        refreshToken=refresh_token,
        expiresIn=int(access_token_expires.total_seconds()),
        subscriber=_subscriber_document_to_schema(subscriber),
        config=config_response
    )


@router.post("/login", response_model=schemas.LoginResponse)
def login_for_access_token(
    payload: schemas.UserLogin,
    db: Database = Depends(get_db),
):
    user = authenticate_user(db, payload.username, payload.password)
    if not user:
        raise unauthorized("Invalid username or password", code="INVALID_CREDENTIALS")

    access_token_expires = timedelta(minutes=settings.access_token_expire_minutes)
    access_token = create_access_token(data={"sub": user.username}, expires_delta=access_token_expires)
    refresh_token, _ = create_refresh_token(db, user.username)
    profile = schemas.UserProfile(
        id=user.username,
        username=user.username,
        displayName=user.display_name or user.username,
        avatarUrl=user.avatar_url,
    )
    return schemas.LoginResponse(
        accessToken=access_token,
        refreshToken=refresh_token,
        expiresIn=int(access_token_expires.total_seconds()),
        user=profile,
    )


@router.post("/bootstrap-admin", response_model=schemas.UserBase)
def bootstrap_admin(db: Database = Depends(get_db)):
    """Create default admin user from env if it does not exist (one-time helper)."""
    existing = db["users"].find_one({"_id": settings.admin_username})
    if existing:
        return schemas.UserBase(
            username=existing.get("username", settings.admin_username),
            is_active=existing.get("is_active", True),
            is_admin=existing.get("is_admin", True),
            display_name=existing.get("display_name"),
            avatar_url=existing.get("avatar_url"),
        )

    document = {
        "_id": settings.admin_username,
        "username": settings.admin_username,
        "password_hash": get_password_hash(settings.admin_password),
        "is_active": True,
        "is_admin": True,
        "display_name": "Administrator",
        "avatar_url": None,
    }
    db["users"].insert_one(document)
    return schemas.UserBase(username=document["username"], is_active=True, is_admin=True)


@router.post("/refresh", response_model=schemas.RefreshResponse)
def refresh_access_token(request: schemas.RefreshRequest, db: Database = Depends(get_db)):
    stored = get_refresh_token(db, request.refreshToken)
    if not stored:
        raise unauthorized("Invalid refresh token", code="INVALID_REFRESH_TOKEN")
    if stored.get("expires_at") and stored["expires_at"] < datetime.utcnow():
        raise unauthorized("Refresh token expired", code="INVALID_REFRESH_TOKEN")
    access_token_expires = timedelta(minutes=settings.access_token_expire_minutes)
    access_token = create_access_token(
        data={"sub": stored["username"]}, expires_delta=access_token_expires
    )
    return schemas.RefreshResponse(
        accessToken=access_token,
        expiresIn=int(access_token_expires.total_seconds()),
    )


@router.get("/me", response_model=schemas.UserProfile)
async def get_me(current_user=Depends(get_current_user)):
    return schemas.UserProfile(
        id=current_user.username,
        username=current_user.username,
        displayName=current_user.display_name or current_user.username,
        avatarUrl=current_user.avatar_url,
    )
