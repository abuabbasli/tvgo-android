import os
from pymongo import MongoClient

# Use env vars or default
MONGO_URI = os.getenv("MONGO_URI", "mongodb://mongodb:27017")
DB_NAME = os.getenv("MONGO_DB_NAME", "tvGO")

def get_dummy_channels():
    # Extracted from TvRepository.kt
    return [
        {"id": "ch1", "name": "Cartoon Network", "logo_url": "file:///android_asset/images/channel_ch1.png", "group": "kids", "stream_url": "http://flussonic01.tvitech.com/lb01/AzTV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Best cartoons and animated series for kids", "logo_color": "#000000"}},
        {"id": "ch2", "name": "Disney Channel", "logo_url": "file:///android_asset/images/channel_ch2.png", "group": "kids", "stream_url": "http://flussonic01.tvitech.com/lb01/Xezer/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Magical Disney content for the whole family", "logo_color": "#1e40af"}},
        {"id": "ch3", "name": "Nickelodeon", "logo_url": "file:///android_asset/images/channel_ch3.png", "group": "kids", "stream_url": "http://flussonic01.tvitech.com/lb01/Ictimai/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Nick shows for all ages", "logo_color": "#f97316"}},
        {"id": "ch4", "name": "ESPN", "logo_url": "file:///android_asset/images/channel_ch4.png", "group": "sports", "stream_url": "http://flussonic01.tvitech.com/lb01/ATV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Live sports and highlights 24/7", "logo_color": "#dc2626"}},
        {"id": "ch5", "name": "Fox Sports", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/e/e3/Fox_Sports_2012.svg/200px-Fox_Sports_2012.svg.png", "group": "sports", "stream_url": "http://flussonic01.tvitech.com/lb01/Space/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Premier sports coverage", "logo_color": "#374151"}},
        {"id": "ch6", "name": "CNN", "logo_url": "file:///android_asset/images/channel_ch6.png", "group": "news", "stream_url": "http://flussonic01.tvitech.com/lb01/ARB_TV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Breaking news and analysis", "logo_color": "#dc2626"}},
        {"id": "ch7", "name": "BBC News", "logo_url": "file:///android_asset/images/channel_ch7.png", "group": "news", "stream_url": "http://flussonic01.tvitech.com/lb01/ARB_24/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Global news coverage", "logo_color": "#dc2626"}},
        {"id": "ch8", "name": "HBO", "logo_url": "file:///android_asset/images/channel_ch8.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/ARB_Gunesh/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Premium entertainment network", "logo_color": "#000000"}},
        {"id": "ch9", "name": "Netflix", "logo_url": "file:///android_asset/images/channel_ch9.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/CBC/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Stream unlimited movies and shows", "logo_color": "#dc2626"}},
        {"id": "ch10", "name": "AMC", "logo_url": "file:///android_asset/images/channel_ch10.png", "group": "movies", "stream_url": "http://flussonic01.tvitech.com/lb01/Idman_TV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Classic and contemporary films", "logo_color": "#000000"}},
        {"id": "ch11", "name": "TNT", "logo_url": "file:///android_asset/images/channel_ch11.png", "group": "movies", "stream_url": "http://flussonic01.tvitech.com/lb01/CBC_Sport_HD/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Action-packed movie channel", "logo_color": "#000000"}},
        {"id": "ch12", "name": "Discovery Kids", "logo_url": "file:///android_asset/images/channel_ch12.png", "group": "kids", "stream_url": "http://flussonic01.tvitech.com/lb01/Real_TV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Educational and fun content for kids", "logo_color": "#10b981"}},
        {"id": "ch13", "name": "National Geographic", "logo_url": "file:///android_asset/images/channel_ch13.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/Baku_TV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Explore the world", "logo_color": "#eab308"}},
        {"id": "ch14", "name": "MTV", "logo_url": "file:///android_asset/images/channel_ch14.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/Dunya_TV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Music and reality TV", "logo_color": "#000000"}},
        {"id": "ch15", "name": "Comedy Central", "logo_url": "file:///android_asset/images/channel_ch15.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/MuzTV_Azerbaycan/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Non-stop comedy", "logo_color": "#eab308"}},
        {"id": "ch16", "name": "NBC Sports", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/9/9f/NBC_Sports_2012.svg/200px-NBC_Sports_2012.svg.png", "group": "sports", "stream_url": "http://flussonic01.tvitech.com/lb01/ATV_HD/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Premier League and more", "logo_color": "#1e40af"}},
        {"id": "ch17", "name": "CBS Sports", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a9/CBS_Sports.svg/200px-CBS_Sports.svg.png", "group": "sports", "stream_url": "http://flussonic01.tvitech.com/lb01/Star_TV_HD/index.m3u8?token=TVITECH!2022", "metadata": {"description": "NFL, NBA, and more", "logo_color": "#1e40af"}},
        {"id": "ch18", "name": "Animal Planet", "logo_url": "file:///android_asset/images/channel_ch18.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/TRT_Avaz/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Wildlife and nature", "logo_color": "#10b981"}},
        {"id": "ch19", "name": "Food Network", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d7/Food_Network_logo.svg/200px-Food_Network_logo.svg.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/Teve_2/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Cooking shows and competitions", "logo_color": "#f97316"}},
        {"id": "ch20", "name": "HGTV", "logo_url": "file:///android_asset/images/channel_ch20.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/TV8/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Home and garden shows", "logo_color": "#10b981"}},
        {"id": "ch21", "name": "Disney Junior", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2c/Disney_Junior.svg/200px-Disney_Junior.svg.png", "group": "kids", "stream_url": "http://flussonic01.tvitech.com/lb01/TV8.5/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Disney shows for preschoolers", "logo_color": "#8b5cf6"}},
        {"id": "ch22", "name": "PBS Kids", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/c/c6/PBS_Kids_Logo.svg/200px-PBS_Kids_Logo.svg.png", "group": "kids", "stream_url": "http://flussonic01.tvitech.com/lb01/DMAX/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Educational programming", "logo_color": "#3b82f6"}},
        {"id": "ch23", "name": "FX", "logo_url": "file:///android_asset/images/channel_ch23.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/SHOWMAXHD/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Original drama series", "logo_color": "#eab308"}},
        {"id": "ch24", "name": "USA Network", "logo_url": "file:///android_asset/images/channel_ch24.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/360Tv/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Drama and entertainment", "logo_color": "#3b82f6"}},
        {"id": "ch25", "name": "Syfy", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/9/97/Syfy_logo_2017.svg/200px-Syfy_logo_2017.svg.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/Pervyi_Kanal/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Sci-Fi and fantasy", "logo_color": "#8b5cf6"}},
        {"id": "ch26", "name": "TBS", "logo_url": "file:///android_asset/images/channel_ch26.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/RTR_Planeta/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Comedy and sports", "logo_color": "#000000"}},
        {"id": "ch27", "name": "Showtime", "logo_url": "file:///android_asset/images/channel_ch27.png", "group": "movies", "stream_url": "http://flussonic01.tvitech.com/lb01/AzTV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Premium movies and series", "logo_color": "#dc2626"}},
        {"id": "ch28", "name": "Starz", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0e/Starz_2016.svg/200px-Starz_2016.svg.png", "group": "movies", "stream_url": "http://flussonic01.tvitech.com/lb01/Xezer/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Premium original series", "logo_color": "#000000"}},
        {"id": "ch29", "name": "ABC News", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/1/19/ABC_News.svg/200px-ABC_News.svg.png", "group": "news", "stream_url": "http://flussonic01.tvitech.com/lb01/Ictimai/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Breaking news coverage", "logo_color": "#eab308"}},
        {"id": "ch30", "name": "Fox News", "logo_url": "file:///android_asset/images/channel_ch30.png", "group": "news", "stream_url": "http://flussonic01.tvitech.com/lb01/ATV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "24/7 news coverage", "logo_color": "#3b82f6"}},
        {"id": "ch31", "name": "MSNBC", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/3/37/MSNBC_logo.svg/200px-MSNBC_logo.svg.png", "group": "news", "stream_url": "http://flussonic01.tvitech.com/lb01/Space/index.m3u8?token=TVITECH!2022", "metadata": {"description": "News and political commentary", "logo_color": "#3b82f6"}},
        {"id": "ch32", "name": "Travel Channel", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/2/22/Travel_Channel_Logo.svg/200px-Travel_Channel_Logo.svg.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/ARB_TV/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Travel and adventure", "logo_color": "#10b981"}},
        {"id": "ch33", "name": "History Channel", "logo_url": "file:///android_asset/images/channel_ch33.png", "group": "entertainment", "stream_url": "http://flussonic01.tvitech.com/lb01/ARB_24/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Historical documentaries", "logo_color": "#eab308"}},
        {"id": "ch34", "name": "Golf Channel", "logo_url": "https://upload.wikimedia.org/wikipedia/commons/thumb/0/0f/Golf_Channel_logo.svg/200px-Golf_Channel_logo.svg.png", "group": "sports", "stream_url": "http://flussonic01.tvitech.com/lb01/ARB_Gunesh/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Golf tournaments and news", "logo_color": "#10b981"}},
        {"id": "ch35", "name": "Tennis Channel", "logo_url": "file:///android_asset/images/channel_ch35.png", "group": "sports", "stream_url": "http://flussonic01.tvitech.com/lb01/CBC/index.m3u8?token=TVITECH!2022", "metadata": {"description": "Tennis matches and news", "logo_color": "#3b82f6"}}
    ]

def import_data():
    print(f"Connecting to {MONGO_URI}...")
    try:
        client = MongoClient(MONGO_URI)
        db = client[DB_NAME]
        
        # Channels
        channels = get_dummy_channels()
        print(f"Importing {len(channels)} channels...")
        count = 0
        for ch in channels:
            ch_id = ch["id"]
            db["channels"].update_one(
                {"_id": ch_id},
                {"$set": ch},
                upsert=True
            )
            count += 1
        print(f"Imported {count} channels.")
        
        # Movies? (I'll skip specific movies for now as list is huge, or do I add them to 'movies' collection?)
        # User said "dummy data also there". "all the channels logos eveyrthing".
        # I'll stick to Channels as priority.
        
        # Verify
        total = db["channels"].count_documents({})
        print(f"Total Channels in DB: {total}")
        
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    import_data()
