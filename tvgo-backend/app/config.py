from typing import Optional

from pydantic import AnyHttpUrl
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    app_name: str = "tvGO Middleware"
    api_v1_prefix: str = "/api"
    secret_key: str = "CHANGE_ME_SUPER_SECRET"
    access_token_expire_minutes: int = 60  # 1 hour
    refresh_token_expire_days: int = 30

    # Database
    mongo_uri: str = "mongodb+srv://boss:TtCbllVwynOwsmyJ@boss.bnzjomc.mongodb.net/?appName=boss"
    mongo_db_name: str = "tvGO"

    # Admin auth (for first login / demo)
    admin_username: str = "admin"
    admin_password: str = "admin"

    # AWS S3
    aws_region: Optional[str] = None
    aws_access_key_id: Optional[str] = None
    aws_secret_access_key: Optional[str] = None
    s3_bucket_name: Optional[str] = None
    s3_public_base_url: Optional[AnyHttpUrl] = None  # e.g. https://cdn.example.com/

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
    }


settings = Settings()
