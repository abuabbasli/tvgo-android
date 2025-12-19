import os
import boto3
import uuid
import glob
from pymongo import MongoClient
from PIL import Image, ImageDraw, ImageFont
import io
import random

# Configuration
MONGO_URI = os.getenv("MONGO_URI", "mongodb+srv://boss:TtCbllVwynOwsmyJ@boss.bnzjomc.mongodb.net/?appName=boss")
MONGO_DB_NAME = os.getenv("MONGO_DB_NAME", "tvGO")
S3_BUCKET_NAME = os.getenv("S3_BUCKET_NAME", "tvgo-images")
AWS_REGION = os.getenv("AWS_REGION", "eu-central-1")

client = MongoClient(MONGO_URI)
db = client[MONGO_DB_NAME]
s3 = boto3.client('s3')

MOVIES_DATA = {
    11: {"title": "Midnight Echo", "genre": ["Thriller", "Mystery"], "year": 2024, "rating": 7.8, "desc": "A detective discovers that his recurring nightmares are actually clues to a cold case."},
    12: {"title": "Solar Winds", "genre": ["Sci-Fi", "Space"], "year": 2025, "rating": 8.9, "desc": "Humanity's last hope sails on solar winds to a distant star system."},
    13: {"title": "The Lost Kingdom", "genre": ["Adventure", "Action"], "year": 2023, "rating": 7.5, "desc": "Explorers find an ancient civilization hidden deep within the Amazon."},
    14: {"title": "Urban Legends", "genre": ["Horror", "Mystery"], "year": 2024, "rating": 6.9, "desc": "Teenagers realize the scary stories they tell are coming true."},
    15: {"title": "Love in Tokyo", "genre": ["Romance", "Drama"], "year": 2024, "rating": 8.2, "desc": "Two strangers find connection amidst the neon lights of Tokyo."},
    16: {"title": "Speed Demon", "genre": ["Action", "Racing"], "year": 2023, "rating": 7.1, "desc": "A street racer must win the ultimate illegal race to save his brother."},
    17: {"title": "Cyber City", "genre": ["Sci-Fi", "Cyberpunk"], "year": 2049, "rating": 8.5, "desc": "In a future where memories can be hacked, one woman fights for the truth."},
    18: {"title": "The Great Heist", "genre": ["Crime", "Action"], "year": 2024, "rating": 8.0, "desc": "A team of elite thieves targets the most secure vault in Europe."},
    19: {"title": "Forest Guardian", "genre": ["Fantasy", "Family"], "year": 2023, "rating": 7.9, "desc": "A young girl befriends a magical creature that protects the forest."},
    20: {"title": "Laugh Track", "genre": ["Comedy"], "year": 2024, "rating": 6.8, "desc": "Behind the scenes of a failing sitcom, the drama is funnier than the show."}
}

COLORS = [
    (220, 53, 69), (40, 167, 69), (0, 123, 255), (255, 193, 7), 
    (23, 162, 184), (102, 16, 242), (253, 126, 20), (32, 201, 151),
    (111, 66, 193), (232, 62, 140)
]

def upload_to_s3(file_obj, key, content_type="image/png"):
    try:
        s3.upload_fileobj(
            Fileobj=file_obj,
            Bucket=S3_BUCKET_NAME,
            Key=key,
            ExtraArgs={"ContentType": content_type}
        )
        url = f"https://{S3_BUCKET_NAME}.s3.{AWS_REGION}.amazonaws.com/{key}"
        print(f"Uploaded: {url}")
        return url
    except Exception as e:
        print(f"Error uploading {key}: {e}")
        return None

def generate_logo(text):
    width, height = 512, 512
    color = random.choice(COLORS)
    img = Image.new('RGB', (width, height), color=color)
    d = ImageDraw.Draw(img)
    
    # Simple gradient effect
    for y in range(height):
        alpha = int((y / height) * 100)
        d.line([(0, y), (width, y)], fill=(0, 0, 0, alpha))

    font_size = 80
    try:
        # Try to find a font in the container
        font_paths = [
            "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
            "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
            "/usr/share/fonts/truetype/freefont/FreeSansBold.ttf"
        ]
        font = None
        for path in font_paths:
            if os.path.exists(path):
                font = ImageFont.truetype(path, font_size)
                break
        if not font:
             font = ImageFont.load_default()
    except:
        font = ImageFont.load_default()

    # Draw Text Centered
    display_text = text[:10] # Truncate if too long
    try:
        left, top, right, bottom = d.textbbox((0, 0), display_text, font=font)
        text_w = right - left
        text_h = bottom - top
    except AttributeError:
        # Fallback for older Pillow
        text_w, text_h = d.textsize(display_text, font=font)
    
    x = (width - text_w) / 2
    y = (height - text_h) / 2
    
    d.text((x, y), display_text, fill=(255, 255, 255), font=font)
    
    out = io.BytesIO()
    img.save(out, format='PNG')
    out.seek(0)
    return out

def process_movies():
    print("Processing Movies...")
    movies_coll = db["movies"]
    
    # Check if we have the new_movies directory
    if not os.path.exists("./new_movies"):
        print("Error: ./new_movies directory not found!")
        return

    for i in range(11, 21):
        pattern = f"./new_movies/movie_poster_{i}_*.png"
        files = glob.glob(pattern)
        if not files:
            print(f"No image found for movie {i} with pattern {pattern}")
            continue
            
        file_path = files[0]
        data = MOVIES_DATA[i]
        
        print(f"Processing movie: {data['title']}")
        with open(file_path, "rb") as f:
            unique_id = uuid.uuid4().hex
            key = f"movies/{unique_id}.png"
            url = upload_to_s3(f, key)
            
        if url:
             movie_doc = {
                "_id": f"movie-{unique_id}",
                "title": data["title"],
                "year": data["year"],
                "genres": data["genre"],
                "genre": data["genre"], # Support both field names
                "rating": data["rating"],
                "synopsis": data["desc"],
                "description": data["desc"],
                "poster_url": url,
                "thumbnail": url,
                "stream_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "videoUrl": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "directors": ["AI Director"],
                "cast": ["AI Actor 1", "AI Actor 2"],
                "availability_start": None,
                "availability_end": None
            }
             # Use replace_one with upsert to avoid duplicates or replace existing
             movies_coll.replace_one({"title": data["title"]}, movie_doc, upsert=True)
             print(f"Saved Metadata for {data['title']}")

def process_channels():
    print("Processing Channels...")
    channels_coll = db["channels"]
    
    # Get all channels
    channels = list(channels_coll.find())
    print(f"Found {len(channels)} total channels.")
    
    count = 0
    for ch in channels:
        name = ch.get('name', 'TV')
        existing_logo = ch.get('logo')
        
        # Skip if already has our S3 logo
        if existing_logo and str(existing_logo).startswith(f"https://{S3_BUCKET_NAME}.s3"):
            continue
            
        print(f"Generating logo for {name} ({count+1})...")
        
        img_data = generate_logo(name)
        unique_id = uuid.uuid4().hex
        key = f"channels/{unique_id}.png"
        
        url = upload_to_s3(img_data, key)
        if url:
            channels_coll.update_one({"_id": ch["_id"]}, {"$set": {"logo": url}})
            print(f"Updated {name}")
            count += 1

if __name__ == "__main__":
    # process_movies() # Skip movies as they are done
    process_channels()
    print("All tasks completed.")
