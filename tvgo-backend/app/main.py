from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from mangum import Mangum
from pathlib import Path

from .routers import auth as auth_router
from .routers import public as public_router
from .routers import admin_channels, admin_movies, admin_rails, admin_config, upload, ingest, streamers, packages, admin_users, epg, admin_games
from .routers import user_groups, messages
from .routers import super_admin

app = FastAPI(title="tvGO Middleware API")

# CORS for dev and production
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://localhost:3001",
        "http://localhost:5173",
        "http://localhost:5174",
        "http://localhost:8080",
        "http://localhost:8081",
        "http://localhost:8082",
        "http://localhost:8083",
        "http://localhost:8084",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:3001",
        "http://127.0.0.1:5173",
        "http://127.0.0.1:5174",
        "http://127.0.0.1:8080",
        "http://127.0.0.1:8081",
        "http://127.0.0.1:8082",
        "http://127.0.0.1:8083",
        "http://127.0.0.1:8084",
        # AWS Lambda URLs
        "https://utrvecx6vxpn73ysosqal2swsi0wvwxu.lambda-url.eu-central-1.on.aws",
        "https://ncgvj6zcklzk6l3on7orwhuyju0kmhxe.lambda-url.eu-central-1.on.aws",
        "https://hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Static file serving for local uploads
UPLOAD_DIR = Path("/tmp/tvgo-uploads")
UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=str(UPLOAD_DIR)), name="uploads")


@app.get("/")
def root():
    return {"status": "ok", "service": "tvGO middleware"}


app.include_router(auth_router.router)
app.include_router(public_router.router)
app.include_router(admin_channels.router)
app.include_router(admin_movies.router)
app.include_router(admin_rails.router)
app.include_router(admin_config.router)
app.include_router(upload.router)
app.include_router(ingest.router)
app.include_router(streamers.router)
app.include_router(packages.router)
app.include_router(admin_users.router)
app.include_router(epg.router)
app.include_router(user_groups.router)
app.include_router(messages.admin_router)
app.include_router(messages.public_router)
app.include_router(admin_games.router)
app.include_router(super_admin.router)

handler = Mangum(app)


