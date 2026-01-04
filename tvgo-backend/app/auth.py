import secrets
from datetime import datetime, timedelta
from types import SimpleNamespace
from typing import Optional
from uuid import uuid4

from fastapi import Depends
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from passlib.context import CryptContext
from pymongo.database import Database

from . import schemas
from .config import settings
from .database import get_db
from .errors import forbidden, unauthorized

try:  # pragma: no cover - compatibility shim
    import bcrypt  # type: ignore
except ImportError:  # pragma: no cover - optional dependency
    bcrypt = None  # type: ignore
else:  # pragma: no cover - runtime patch for bcrypt>=4
    if not hasattr(bcrypt, "__about__"):
        version = getattr(bcrypt, "__version__", "")
        bcrypt.__about__ = SimpleNamespace(__version__=version or "unknown")  # type: ignore[attr-defined]

oauth2_scheme = OAuth2PasswordBearer(tokenUrl=f"{settings.api_v1_prefix}/auth/login")
optional_oauth2_scheme = OAuth2PasswordBearer(
    tokenUrl=f"{settings.api_v1_prefix}/auth/login", auto_error=False
)

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    return pwd_context.verify(plain_password, hashed_password)


def get_password_hash(password: str) -> str:
    return pwd_context.hash(password)


def create_access_token(data: dict, expires_delta: Optional[timedelta] = None) -> str:
    to_encode = data.copy()
    expire = datetime.utcnow() + (expires_delta or timedelta(minutes=settings.access_token_expire_minutes))
    to_encode.update({"exp": expire})
    encoded_jwt = jwt.encode(to_encode, settings.secret_key, algorithm="HS256")
    return encoded_jwt


def create_refresh_token(db: Database, username: str) -> tuple[str, datetime]:
    token = secrets.token_urlsafe(48)
    expires_at = datetime.utcnow() + timedelta(days=settings.refresh_token_expire_days)
    document = {
        "_id": str(uuid4()),
        "token": token,
        "username": username,
        "expires_at": expires_at,
        "created_at": datetime.utcnow(),
    }
    db["refresh_tokens"].insert_one(document)
    return token, expires_at


def get_refresh_token(db: Database, refresh_token: str) -> Optional[dict]:
    return db["refresh_tokens"].find_one({"token": refresh_token})


def revoke_refresh_token(db: Database, refresh_token: str) -> None:
    db["refresh_tokens"].delete_one({"token": refresh_token})


def get_user(db: Database, username: str) -> Optional[schemas.UserInDB]:
    document = db["users"].find_one({"_id": username})
    if not document:
        return None
    payload = document.copy()
    payload.pop("_id", None)
    return schemas.UserInDB(**payload)


def authenticate_user(db: Database, username: str, password: str) -> Optional[schemas.UserInDB]:
    user = get_user(db, username)
    if not user:
        return None
    if not verify_password(password, user.password_hash):
        return None
    return user


async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: Database = Depends(get_db),
) -> schemas.UserInDB:
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=["HS256"])
        username: str = payload.get("sub")
        if username is None:
            raise unauthorized("Could not validate credentials")
        token_data = schemas.TokenData(username=username)
    except JWTError:
        raise unauthorized("Could not validate credentials")

    user = get_user(db, username=token_data.username)
    if user is None:
        raise unauthorized("Could not validate credentials")
    return user


async def get_current_active_admin(
    current_user: schemas.UserInDB = Depends(get_current_user),
) -> schemas.UserInDB:
    if not current_user.is_active or not current_user.is_admin:
        raise forbidden("Not enough privileges")
    return current_user


async def get_optional_user(
    token: Optional[str] = Depends(optional_oauth2_scheme),
    db: Database = Depends(get_db),
) -> Optional[schemas.UserInDB]:
    if not token:
        return None
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=["HS256"])
    except JWTError:
        return None
    username = payload.get("sub")
    if not username:
        return None
    return get_user(db, username)


async def get_current_subscriber(
    token: str = Depends(oauth2_scheme),
    db: Database = Depends(get_db),
) -> dict:
    """Get the current subscriber user from JWT token.
    
    Returns the raw MongoDB document for the subscriber.
    """
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=["HS256"])
        
        # Check if this is a subscriber token
        role = payload.get("role")
        subscriber_id = payload.get("id")
        
        if role != "subscriber" or not subscriber_id:
            raise unauthorized("Not a valid subscriber token")
        
    except JWTError:
        raise unauthorized("Could not validate credentials")
    
    subscriber = db["subscribers"].find_one({"_id": subscriber_id})
    if not subscriber:
        raise unauthorized("Subscriber not found")
    
    # Check if subscriber is still active
    status = subscriber.get("status", "active")
    allowed_statuses = ["active", "bonus", "test"]
    if status not in allowed_statuses and not subscriber.get("is_active", True):
        raise forbidden("Account is inactive")
    
    return subscriber
