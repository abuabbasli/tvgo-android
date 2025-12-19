from typing import Iterator

from pymongo import MongoClient
from pymongo.database import Database

from .config import settings

_client = MongoClient(settings.mongo_uri)
_database = _client[settings.mongo_db_name]


def get_database() -> Database:
    return _database


def get_db() -> Iterator[Database]:
    db = get_database()
    try:
        yield db
    finally:
        # MongoClient is managed globally; no explicit close per request
        pass
