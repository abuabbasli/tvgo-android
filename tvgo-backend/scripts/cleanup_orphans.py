import os
from pymongo import MongoClient

# Default to internal docker DNS name, override with env if running locally
MONGO_URI = os.getenv("MONGO_URI", "mongodb://mongodb:27017")
DB_NAME = os.getenv("MONGO_DB_NAME", "tvGO")

def cleanup():
    print(f"Connecting to {MONGO_URI}...")
    try:
        client = MongoClient(MONGO_URI)
        db = client[DB_NAME]
        # Ping
        client.admin.command('ping')
        print("Connected.")
    except Exception as e:
        print(f"Connection failed: {e}")
        # Try localhost if default failed (running outside docker?)
        if "mongodb:27017" in MONGO_URI:
            print("Retrying with localhost:27017...")
            client = MongoClient("mongodb://localhost:27017")
            db = client[DB_NAME]
        else:
            return

    streamers = list(db["streamers"].find())
    valid_names = {s.get("name") for s in streamers if s.get("name")}
    
    print(f"Valid Streamers: {valid_names}")
    
    # Find orphans: channels with a streamer_name that is not in valid_names
    # We ignore channels with no streamer_name (manual channels)
    query = {
        "streamer_name": {"$nin": list(valid_names), "$ne": None}
    }
    
    count = db["channels"].count_documents(query)
    print(f"Found {count} orphaned channels.")
    
    if count > 0:
        result = db["channels"].delete_many(query)
        print(f"Deleted {result.deleted_count} channels.")
    else:
        print("No orphans found.")

if __name__ == "__main__":
    cleanup()
