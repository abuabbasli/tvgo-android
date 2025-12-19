#!/usr/bin/env python3
"""
Upload movie posters to S3 and save movie data to MongoDB.
"""
import os
import uuid
import boto3
from botocore.client import Config
from pymongo import MongoClient

# AWS S3 Configuration
AWS_REGION = "eu-central-1"
AWS_ACCESS_KEY_ID = os.getenv("AWS_ACCESS_KEY_ID", "your_access_key")
AWS_SECRET_ACCESS_KEY = os.getenv("AWS_SECRET_ACCESS_KEY", "your_secret_key")
S3_BUCKET_NAME = "tvgo-images"

# MongoDB Configuration
MONGO_URI = "mongodb+srv://boss:TtCbllVwynOwsmyJ@boss.bnzjomc.mongodb.net/?appName=boss"
MONGO_DB_NAME = "tvGO"

# Image directory
IMAGE_DIR = "/Users/abu/.gemini/antigravity/brain/8e7f16fc-289a-44fc-9c6b-47c65a8cae3e"

# Movie data with matching image files
MOVIES = [
    {
        "id": "quantum-storm",
        "title": "Quantum Storm",
        "description": "In a world where quantum technology has reshaped reality, one scientist must prevent a catastrophic storm that threatens to unravel the fabric of space-time.",
        "category": "scifi",
        "genre": ["Sci-Fi", "Action", "Thriller"],
        "year": 2024,
        "rating": "8.7",
        "duration": "2h 18min",
        "director": "Alex Chen",
        "cast": ["Michael Nova", "Sarah Quantum", "James Reed"],
        "image_file": "movie_poster_1_1766099897345.png"
    },
    {
        "id": "shadow-protocol",
        "title": "Shadow Protocol",
        "description": "A rogue agent uncovers a conspiracy that reaches the highest levels of government. With nowhere to turn, he must fight to expose the truth before it's too late.",
        "category": "action",
        "genre": ["Action", "Thriller", "Spy"],
        "year": 2024,
        "rating": "8.4",
        "duration": "2h 05min",
        "director": "Marcus Stone",
        "cast": ["David Kane", "Elena Volkov", "Chen Wei"],
        "image_file": "movie_poster_2_1766099911764.png"
    },
    {
        "id": "dragons-legacy",
        "title": "Dragon's Legacy",
        "description": "When ancient dragons return to the realm, a young warrior must embrace her destiny and unite the kingdoms to face the greatest threat the world has ever known.",
        "category": "fantasy",
        "genre": ["Fantasy", "Adventure", "Epic"],
        "year": 2024,
        "rating": "8.9",
        "duration": "2h 42min",
        "director": "Emma Blackwood",
        "cast": ["Aria Storm", "Marcus Drake", "Luna Frost"],
        "image_file": "movie_poster_3_1766099926010.png"
    },
    {
        "id": "the-hollow",
        "title": "The Hollow",
        "description": "A family moves into a historic mansion, only to discover that the house holds dark secrets and an entity that feeds on fear.",
        "category": "horror",
        "genre": ["Horror", "Thriller", "Supernatural"],
        "year": 2024,
        "rating": "7.8",
        "duration": "1h 52min",
        "director": "Jordan Graves",
        "cast": ["Emily Stark", "Robert Dark", "Lily Shadow"],
        "image_file": "movie_poster_4_1766099940777.png"
    },
    {
        "id": "sky-explorers",
        "title": "Sky Explorers",
        "description": "Join a group of adventurous kids as they discover a magical world above the clouds, filled with floating islands, friendly creatures, and the adventure of a lifetime!",
        "category": "kids",
        "genre": ["Animation", "Adventure", "Family"],
        "year": 2024,
        "rating": "8.5",
        "duration": "1h 38min",
        "director": "Pixar Animation",
        "cast": ["Voice Cast Ensemble"],
        "image_file": "movie_poster_5_1766099956028.png"
    },
    {
        "id": "paris-in-spring",
        "title": "Paris in Spring",
        "description": "When a travel writer and a local baker cross paths in Paris during cherry blossom season, they discover that love can bloom in the most unexpected places.",
        "category": "romance",
        "genre": ["Romance", "Comedy", "Drama"],
        "year": 2024,
        "rating": "7.9",
        "duration": "1h 55min",
        "director": "Sophie Laurent",
        "cast": ["Claire Dubois", "Antoine Martin", "Marie Rose"],
        "image_file": "movie_poster_6_1766099971466.png"
    },
    {
        "id": "fast-lane",
        "title": "Fast Lane",
        "description": "Two street racers must team up to take down an international crime syndicate using the only thing they know best - speed and style.",
        "category": "action",
        "genre": ["Action", "Comedy", "Racing"],
        "year": 2024,
        "rating": "8.1",
        "duration": "2h 10min",
        "director": "Tony Rush",
        "cast": ["Jake Speed", "Nina Blaze", "Carlos Fury"],
        "image_file": "movie_poster_7_1766100002555.png"
    },
    {
        "id": "the-last-witness",
        "title": "The Last Witness",
        "description": "A veteran detective is pulled out of retirement when a series of murders mirrors a case from his past. The only witness? A ghost from his memory.",
        "category": "thriller",
        "genre": ["Mystery", "Thriller", "Noir"],
        "year": 2024,
        "rating": "8.3",
        "duration": "2h 01min",
        "director": "William Noir",
        "cast": ["Robert Stone", "Diana Sharp", "Thomas Grey"],
        "image_file": "movie_poster_8_1766100014172.png"
    },
    {
        "id": "beyond-the-stars",
        "title": "Beyond the Stars",
        "description": "An astronaut on a solo mission discovers a signal from deep space that will change humanity's understanding of its place in the universe forever.",
        "category": "scifi",
        "genre": ["Sci-Fi", "Space", "Drama"],
        "year": 2024,
        "rating": "9.1",
        "duration": "2h 28min",
        "director": "Neil Armstrong Jr.",
        "cast": ["Dr. Sarah Cosmos", "Mission Control Ensemble"],
        "image_file": "movie_poster_9_1766100028358.png"
    },
    {
        "id": "robot-friends",
        "title": "Robot Friends",
        "description": "In a future where robots and humans live side by side, a young girl and her robot companion embark on an adventure to save their city from a rogue AI.",
        "category": "kids",
        "genre": ["Animation", "Adventure", "Sci-Fi", "Family"],
        "year": 2024,
        "rating": "8.6",
        "duration": "1h 42min",
        "director": "Tech Animation Studios",
        "cast": ["Voice Cast Ensemble"],
        "image_file": "movie_poster_10_1766100041843.png"
    }
]

def upload_to_s3(file_path: str) -> str:
    """Upload file to S3 and return the public URL."""
    session = boto3.session.Session(
        aws_access_key_id=AWS_ACCESS_KEY_ID,
        aws_secret_access_key=AWS_SECRET_ACCESS_KEY,
        region_name=AWS_REGION,
    )
    s3 = session.client("s3", config=Config(signature_version="s3v4"))
    
    unique_id = uuid.uuid4().hex
    key = f"movies/{unique_id}.png"
    
    with open(file_path, "rb") as f:
        s3.upload_fileobj(
            Fileobj=f,
            Bucket=S3_BUCKET_NAME,
            Key=key,
            ExtraArgs={"ContentType": "image/png"}
        )
    
    return f"https://{S3_BUCKET_NAME}.s3.{AWS_REGION}.amazonaws.com/{key}"

def main():
    # Connect to MongoDB
    client = MongoClient(MONGO_URI)
    db = client[MONGO_DB_NAME]
    movies_collection = db["movies"]
    
    # Clear existing movies
    movies_collection.delete_many({})
    print("Cleared existing movies.")
    
    for movie in MOVIES:
        image_path = os.path.join(IMAGE_DIR, movie["image_file"])
        
        if not os.path.exists(image_path):
            print(f"Image not found: {image_path}")
            continue
        
        print(f"Uploading {movie['title']}...")
        thumbnail_url = upload_to_s3(image_path)
        print(f"  -> {thumbnail_url}")
        
        # Create movie document
        movie_doc = {
            "_id": movie["id"],
            "title": movie["title"],
            "description": movie["description"],
            "category": movie["category"],
            "genre": movie["genre"],
            "year": movie["year"],
            "rating": movie["rating"],
            "duration": movie["duration"],
            "director": movie["director"],
            "cast": movie["cast"],
            "thumbnail": thumbnail_url,
            "videoUrl": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",  # Sample video
            "active": True
        }
        
        movies_collection.insert_one(movie_doc)
        print(f"  -> Saved to database!")
    
    print(f"\nDone! Inserted {len(MOVIES)} movies.")
    client.close()

if __name__ == "__main__":
    main()
