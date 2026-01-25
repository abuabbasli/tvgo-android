from typing import Iterator, Optional
import certifi

from pymongo import MongoClient
from pymongo.database import Database

from .config import settings

# Lazy initialization - connect on first use
_client: Optional[MongoClient] = None
_database: Optional[Database] = None


def get_database() -> Database:
    global _client, _database
    if _database is None:
        _client = MongoClient(settings.mongo_uri, tlsCAFile=certifi.where())
        _database = _client[settings.mongo_db_name]
    return _database


def get_db() -> Iterator[Database]:
    db = get_database()
    try:
        yield db
    finally:
        # MongoClient is managed globally; no explicit close per request
        pass
