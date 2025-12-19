from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles
from mangum import Mangum
from pathlib import Path

from .routers import auth as auth_router
from .routers import public as public_router
from .routers import admin_channels, admin_movies, admin_rails, admin_config, upload, ingest, streamers, packages, admin_users

app = FastAPI(title="tvGO Middleware API")

# CORS for dev (adjust in production)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://localhost:8080",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:8080",
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

handler = Mangum(app)


