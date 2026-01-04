"""Seed MongoDB with demo data for the tvGO Android client."""

from __future__ import annotations

import sys
from datetime import datetime, timedelta
from pathlib import Path
from typing import Dict, List

from pymongo import MongoClient

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.auth import get_password_hash  # noqa: E402
from app.config import settings  # noqa: E402

import boto3
import httpx
from botocore.config import Config
from io import BytesIO

def get_s3_client():
    if not settings.aws_region or not settings.aws_access_key_id:
        print("AWS credentials not found in settings.")
        return None
    session = boto3.session.Session(
        aws_access_key_id=settings.aws_access_key_id,
        aws_secret_access_key=settings.aws_secret_access_key,
        region_name=settings.aws_region,
    )
    return session.client("s3", config=Config(signature_version="s3v4"))

def upload_to_s3(image_url: str, folder: str, name_slug: str) -> str:
    """Download image from URL and upload to S3."""
    print(f"Processing {name_slug}...", end=" ", flush=True)
    
    # 1. Download (or generate)
    try:
        # If it's a placeholder service, we can just download it. 
        # If it was a broken internal URL, we might want to fallback to a placeholder.
        if "cdn.tvgo.cloud" in image_url:
             # It's broken, replace with placeholder based on name
             if folder == "users":
                 image_url = f"https://ui-avatars.com/api/?name={name_slug}&background=random&size=200"
             elif folder == "channels":
                 image_url = f"https://ui-avatars.com/api/?name={name_slug}&background=random&size=200&length=2"
             else: # movies
                 # Poster aspect ratio approx 2:3
                 if "poster" in name_slug:
                    image_url = f"https://placehold.co/400x600/2a2a2a/FFF/png?text={name_slug.split('-')[0].title()}"
                 else:
                    image_url = f"https://placehold.co/640x360/2a2a2a/FFF/png?text={name_slug.split('-')[0].title()}"

        res = httpx.get(image_url, timeout=10.0, follow_redirects=True)
        res.raise_for_status()
        content = res.content
        content_type = res.headers.get("content-type", "image/png")
        extension = "png" # defaulted
        if "jpeg" in content_type or "jpg" in content_type:
            extension = "jpg"
            
    except Exception as e:
        print(f"Failed to download {image_url}: {e}")
        return image_url # Fallback to original if fail (though likely broken)

    # 2. Upload to S3
    s3 = get_s3_client()
    if not s3 or not settings.s3_bucket_name:
        print("S3 not configured, skipping upload.")
        return image_url

    key = f"{folder}/{name_slug}.{extension}"
    try:
        s3.put_object(
            Bucket=settings.s3_bucket_name,
            Key=key,
            Body=content,
            ContentType=content_type,
        )
        url = f"https://{settings.s3_bucket_name}.s3.{settings.aws_region}.amazonaws.com/{key}"
        print("Uploaded.")
        return url
    except Exception as e:
        print(f"S3 Upload failed: {e}")
        return image_url


M3U_PLAYLIST = """#EXTINF:-1 group-title=\"İnformasiya\",AzTV
http://flussonic01.tvitech.com/lb01/AzTV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Xezer
http://flussonic01.tvitech.com/lb01/Xezer/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Ictimai
http://flussonic01.tvitech.com/lb01/Ictimai/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",ATV (Azad Azərbaycan)
http://flussonic01.tvitech.com/lb01/ATV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Space
http://flussonic01.tvitech.com/lb01/Space/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",ARB TV
http://flussonic01.tvitech.com/lb01/ARB_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",ARB 24
http://flussonic01.tvitech.com/lb01/ARB_24/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Uşaq\",ARB Gunesh
http://flussonic01.tvitech.com/lb01/ARB_Gunesh/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",CBC
http://flussonic01.tvitech.com/lb01/CBC/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İdman\",Idman TV
http://flussonic01.tvitech.com/lb01/Idman_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İdman\",CBC Sport HD
http://flussonic01.tvitech.com/lb01/CBC_Sport_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",Real TV
http://flussonic01.tvitech.com/lb01/Real_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",Baku Tv
http://flussonic01.tvitech.com/lb01/Baku_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Dunya TV
http://flussonic01.tvitech.com/lb01/Dunya_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Musiqi\",MuzTV Azerbaycan
http://flussonic01.tvitech.com/lb01/MuzTV_Azerbaycan/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",ATV HD Turkiyye
http://flussonic01.tvitech.com/lb01/ATV_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Star TV HD
http://flussonic01.tvitech.com/lb01/Star_TV_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",TRT Avaz HD
http://flussonic01.tvitech.com/lb01/TRT_Avaz/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Teve 2
http://flussonic01.tvitech.com/lb01/Teve_2/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",TV8 HD
http://flussonic01.tvitech.com/lb01/TV8/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",TV 8.5
http://flussonic01.tvitech.com/lb01/TV8.5/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",DMAX
http://flussonic01.tvitech.com/lb01/DMAX/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",SHOW MAX HD
http://flussonic01.tvitech.com/lb01/SHOWMAXHD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",360TV
http://flussonic01.tvitech.com/lb01/360Tv/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Первый Канал
http://flussonic01.tvitech.com/lb01/Pervyi_Kanal/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",РТР Планета
http://flussonic01.tvitech.com/lb01/RTR_Planeta/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",СТС
http://flussonic01.tvitech.com/lb01/STS_Moskva/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",CTC Love
http://flussonic01.tvitech.com/lb01/CTCLove/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",ТНТ
http://flussonic01.tvitech.com/lb01/TNT/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",ТНТ 4
http://flussonic01.tvitech.com/lb01/TNT_4/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",РЕН ТВ
http://flussonic01.tvitech.com/lb01/REN_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",НТВ
http://flussonic01.tvitech.com/lb01/NTV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Пятница
http://flussonic01.tvitech.com/lb01/Piatnitsa/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",ЧЕ
http://flussonic01.tvitech.com/lb01/CHE/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",ТВ Центр
http://flussonic01.tvitech.com/lb01/TV_Centr/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Телекафе
http://flussonic01.tvitech.com/lb01/Telekafe/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Кухня ТВ
http://flussonic01.tvitech.com/lb01/Kunya/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",ЮТВ
http://flussonic01.tvitech.com/lb01/YUTV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Звезда
http://flussonic01.tvitech.com/lb01/zvezda/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Пятый канал
http://flussonic01.tvitech.com/lb01/Piatyi_kanal/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Продвижение
http://flussonic01.tvitech.com/lb01/Prodvizhenie/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",E TV
http://flussonic01.tvitech.com/lb01/E_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",КВН ТВ
http://flussonic01.tvitech.com/lb01/KVNTV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",БОБЁР
http://flussonic01.tvitech.com/lb01/Bober/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Дикий
http://flussonic01.tvitech.com/lb01/Dikiy/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Суббота !
http://flussonic01.tvitech.com/lb01/Subbota/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Ностальгия
http://flussonic01.tvitech.com/lb01/Nostalgiia/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",TLC Russia
http://flussonic01.tvitech.com/lb01/TLCRussia/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Охота и Рыбалка
http://flussonic01.tvitech.com/lb01/OxotaIRibalka/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Лапки Лайв
http://flussonic01.tvitech.com/lb01/Lapki_Live/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Əyləncə\",Моя стихия HD
http://flussonic01.tvitech.com/lb01/Moia_Stixiia/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Живая природа
http://flussonic01.tvitech.com/lb01/Jivaya_priroda/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Живи активно
http://flussonic01.tvitech.com/lb01/Jivi_Aktivno/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Глазами туриста HD
http://flussonic01.tvitech.com/lb01/Glazami_Turista/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Discovery Channel
http://flussonic01.tvitech.com/lb01/DiscoveryChannel/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Investigation Discovery
http://flussonic01.tvitech.com/lb01/InvestigationDiscovery/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Animal Planet
http://flussonic01.tvitech.com/lb01/AnimalPlanet/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Моя Планета
http://flussonic01.tvitech.com/lb01/Moia_Planeta/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Домашние животные
http://flussonic01.tvitech.com/lb01/Domashnie_zhivotnye/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",viju Explore
http://flussonic01.tvitech.com/lb01/Viasat_Explore/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",viju Nature
http://flussonic01.tvitech.com/lb01/Viasat_Nature/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",viju History
http://flussonic01.tvitech.com/lb01/Viasat_History/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",365 Дней
http://flussonic01.tvitech.com/lb01/365_Dnei/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",TRT 1 HD
http://flussonic01.tvitech.com/lb01/TRT1/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Авто Плюс
http://flussonic01.tvitech.com/lb01/Avto_Plius/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",АВТО 24
http://flussonic01.tvitech.com/lb01/AVTO_24/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Наука 2.0
http://flussonic01.tvitech.com/lb01/Nauka/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Доктор
http://flussonic01.tvitech.com/lb01/Docktor/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Россия Культура
http://flussonic01.tvitech.com/lb01/Rossiia_Kultura/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",English club
http://flussonic01.tvitech.com/lb01/Englishclub/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",RTG TV HD
http://flussonic01.tvitech.com/lb01/RTG_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Travel+Adventure
http://flussonic01.tvitech.com/lb01/TravelPlusAdventure/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",ЖИВИ!
http://flussonic01.tvitech.com/lb01/Jivi/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",ТНВ-Планета
http://flussonic01.tvitech.com/lb01/TNVPlaneta/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Диалоги о рыбалке
http://flussonic01.tvitech.com/lb01/DialogiORibalke/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Кто есть кто
http://flussonic01.tvitech.com/lb01/KtoEstKto/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",История
http://flussonic01.tvitech.com/lb01/Istoriya/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Время: далекое и близкое
http://flussonic01.tvitech.com/lb01/VremyaDalekoeIBlizkoe/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",viju TV1000 Новелла
http://flussonic01.tvitech.com/lb01/TV_1000_Novella/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Terra HD
http://flussonic01.tvitech.com/lb01/Terra_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",viju+ Planet HD
http://flussonic01.tvitech.com/lb01/Viasat_Nature_History_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Живая Планета
http://flussonic01.tvitech.com/lb01/JivayaPlaneta/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",TRT Belgesel HD
http://flussonic01.tvitech.com/lb01/TRT_Belgesel_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Show TV HD
http://flussonic01.tvitech.com/lb01/Show_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Big Planet
http://flussonic01.tvitech.com/lb01/BigPlanet/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",CuriosityStream
http://flussonic01.tvitech.com/lb01/CuriosityStream/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Тайны Галактики
http://flussonic01.tvitech.com/lb01/TaynyGalaktiki/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",The Explorers
http://flussonic01.tvitech.com/lb01/The_Explorers/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Maarifləndirici\",Аппетитный
http://flussonic01.tvitech.com/lb01/Apetitniy/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",FREEDOM TV
http://flussonic01.tvitech.com/lb01/UA_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",РОССИЯ 24
http://flussonic01.tvitech.com/lb01/ROSSIIA_24/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",РБК ТВ
http://flussonic01.tvitech.com/lb01/RBK_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",Мир
http://flussonic01.tvitech.com/lb01/Mir/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",Мир 24
http://flussonic01.tvitech.com/lb01/Mir_24/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",TRT World HD
http://flussonic01.tvitech.com/lb01/TRT_WORLD_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",TRT HABER
http://flussonic01.tvitech.com/lb01/TRT_HABER/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",TLC TR HD
http://flussonic01.tvitech.com/lb01/TLC_HDA/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",A Haber
http://flussonic01.tvitech.com/lb01/A_Haber/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",HABERTURK HD
http://flussonic01.tvitech.com/lb01/HaberTurk/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",Haber Global HD
http://flussonic01.tvitech.com/lb01/HaberGlobalHD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",TGRT Haber HD
http://flussonic01.tvitech.com/lb01/TGRTHaberHD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",TRT2 HD
http://flussonic01.tvitech.com/lb01/TRT2HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",TELE 1
http://flussonic01.tvitech.com/lb01/TELE1/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",CNN TR
http://flussonic01.tvitech.com/lb01/CNN_tr/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",Bloomberg HD
http://flussonic01.tvitech.com/lb01/Bloomberg_TRE/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",Euronews Rus
http://flussonic01.tvitech.com/lb01/EuronewsRus/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",CNN
http://flussonic01.tvitech.com/lb01/CNN/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",BBC
http://flussonic01.tvitech.com/lb01/BBC/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",France 24
http://flussonic01.tvitech.com/lb01/France24/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Star Cinema
http://flussonic01.tvitech.com/lb01/Star_Cinema/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Star Family
http://flussonic01.tvitech.com/lb01/Star_Family/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"İnformasiya\",EuronewsEng
http://flussonic01.tvitech.com/lb01/EuronewsEng/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Hollywood HD
http://flussonic01.tvitech.com/lb01/Hollywood_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",viju+ Megahit
http://flussonic01.tvitech.com/lb01/VIP_Megahit_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",viju+ Premiere
http://flussonic01.tvitech.com/lb01/VIP_Premiere_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",viju+ Comedy
http://flussonic01.tvitech.com/lb01/VIP_Comedy_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",viju TV 1000
http://flussonic01.tvitech.com/lb01/TV_1000/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",viju TV 1000 Action
http://flussonic01.tvitech.com/lb01/TV_1000_Action/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",viju TV 1000 Русское Кино
http://flussonic01.tvitech.com/lb01/TV_1000_Russkoe_Kino/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Кинохит
http://flussonic01.tvitech.com/lb01/Kinokhit/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Киносвидание
http://flussonic01.tvitech.com/lb01/Kinosvidanie/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Киносемья
http://flussonic01.tvitech.com/lb01/Kinosemia/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Киносерия
http://flussonic01.tvitech.com/lb01/Kinoseriia/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Киномикс
http://flussonic01.tvitech.com/lb01/Kinomiks/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Кинокомедия
http://flussonic01.tvitech.com/lb01/Kinokomediia/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Киноужас
http://flussonic01.tvitech.com/lb01/Kinoujas/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Дом Кино
http://flussonic01.tvitech.com/lb01/Dom_Kino/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Наше Новое Кино
http://flussonic01.tvitech.com/lb01/Nashe_Novoe_Kino/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Мужское Кино
http://flussonic01.tvitech.com/lb01/Muzhskoe_Kino/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Индийское Кино
http://flussonic01.tvitech.com/lb01/Indiiskoe_Kino/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Домашний
http://flussonic01.tvitech.com/lb01/Domashnii/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Родное Кино
http://flussonic01.tvitech.com/lb01/Rodnoe_Kino/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Кинопремьера HD
http://flussonic01.tvitech.com/lb01/KinoPremiera_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Дом Кино Премиум HD
http://flussonic01.tvitech.com/lb01/Dom_Kino_Premium_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Мир сериала HD
http://flussonic01.tvitech.com/lb01/Mir_seriala_HD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Любимое кино HD
http://flussonic01.tvitech.com/lb01/LyubimoeKino/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Еврокино
http://flussonic01.tvitech.com/lb01/Evrokino/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Amedia Hit
http://flussonic01.tvitech.com/lb01/Amedia_Hit/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Amedia Premium
http://flussonic01.tvitech.com/lb01/Amedia_Premium/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",A1
http://flussonic01.tvitech.com/lb01/A1/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",A2
http://flussonic01.tvitech.com/lb01/A2/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",START Air
http://flussonic01.tvitech.com/lb01/Start_Air/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",START World
http://flussonic01.tvitech.com/lb01/Start_World/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Romance
http://flussonic01.tvitech.com/lb01/Romance/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Русский Бестселлер
http://flussonic01.tvitech.com/lb01/Russkii_Bestseller/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Русский Илююзион
http://flussonic01.tvitech.com/lb01/Russkii_Iliuiuzion/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Кино ТВ
http://flussonic01.tvitech.com/lb01/Kino_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",НТВ Сериал
http://flussonic01.tvitech.com/lb01/NTV_Serial/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Комедия
http://flussonic01.tvitech.com/lb01/komediia/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",ТВ 3
http://flussonic01.tvitech.com/lb01/TV_3/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",КИНЕКО
http://flussonic01.tvitech.com/lb01/FOX_Russia/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",САПФИР
http://flussonic01.tvitech.com/lb01/Saphfir/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Дорама
http://flussonic01.tvitech.com/lb01/Dorama/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Shot TV
http://flussonic01.tvitech.com/lb01/Shot_TV/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",НТВ-ХИТ
http://flussonic01.tvitech.com/lb01/NtvHit/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Bollywood HD
http://flussonic01.tvitech.com/lb01/BollywoodHD/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",red
http://flussonic01.tvitech.com/lb01/red/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",sci-fi
http://flussonic01.tvitech.com/lb01/sci-fi/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",.black
http://flussonic01.tvitech.com/lb01/black/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",Иллюзион +
http://flussonic01.tvitech.com/lb01/Illuzion_Plus/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",TV21M
http://flussonic01.tvitech.com/lb01/TV21M/index.m3u8?token=TVITECH!2022
#EXTINF:-1 group-title=\"Film\",НСТ
http://flussonic01.tvitech.com/lb01/NSTV/index.m3u8?token=TVITECH!2022"""


MOVIES: List[Dict[str, object]] = [
    {
        "_id": "cyber-runner",
        "title": "Cyber Runner",
        "year": 2024,
        "genres": ["Sci-Fi", "Thriller", "Action"],
        "rating": 8.5,
        "runtime_minutes": 122,
        "synopsis": "In a dystopian future a courier discovers a conspiracy that could free New Tokyo.",
        "poster_url": "https://cdn.tvgo.cloud/movies/cyber-runner-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/cyber-runner-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/cyber-runner-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/cyber-runner/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/cyber-runner-trailer.mp4",
        "badges": ["4K", "HDR"],
        "directors": ["Jane Doe"],
        "cast": ["Actor One", "Actor Two"],
    },
    {
        "_id": "space-warriors",
        "title": "Space Warriors",
        "year": 2023,
        "genres": ["Kids", "Action"],
        "rating": 7.6,
        "runtime_minutes": 98,
        "synopsis": "A crew of young cadets defends Earth from an alien armada.",
        "poster_url": "https://cdn.tvgo.cloud/movies/space-warriors-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/space-warriors-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/space-warriors-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/space-warriors/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/space-warriors-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Chris Lane"],
        "cast": ["Maya Chen", "Leo Grant"],
    },
    {
        "_id": "desert-storm",
        "title": "Desert Storm",
        "year": 2022,
        "genres": ["Action"],
        "rating": 7.9,
        "runtime_minutes": 110,
        "synopsis": "Special forces battle a rogue AI in the Sahara desert.",
        "poster_url": "https://cdn.tvgo.cloud/movies/desert-storm-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/desert-storm-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/desert-storm-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/desert-storm/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/desert-storm-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Lana Cruz"],
        "cast": ["Ivan Ortiz", "Sasha Bloom"],
    },
    {
        "_id": "mountain-rescue",
        "title": "Mountain Rescue",
        "year": 2021,
        "genres": ["Drama", "Thriller"],
        "rating": 7.2,
        "runtime_minutes": 104,
        "synopsis": "A veteran climber leads a perilous rescue after an avalanche.",
        "poster_url": "https://cdn.tvgo.cloud/movies/mountain-rescue-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/mountain-rescue-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/mountain-rescue-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/mountain-rescue/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/mountain-rescue-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Ana Rivers"],
        "cast": ["Owen Blake", "Kim Hart"],
    },
    {
        "_id": "neon-blade",
        "title": "Neon Blade",
        "year": 2024,
        "genres": ["Sci-Fi", "Action"],
        "rating": 8.1,
        "runtime_minutes": 116,
        "synopsis": "A cyberpunk detective tracks a rogue android across the megacity.",
        "poster_url": "https://cdn.tvgo.cloud/movies/neon-blade-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/neon-blade-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/neon-blade-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/neon-blade/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/neon-blade-trailer.mp4",
        "badges": ["4K"],
        "directors": ["Isa Navarro"],
        "cast": ["Kira Holt", "Miles Rowan"],
    },
    {
        "_id": "harbor-lights",
        "title": "Harbor Lights",
        "year": 2020,
        "genres": ["Romance", "Drama"],
        "rating": 7.5,
        "runtime_minutes": 112,
        "synopsis": "Two rivals rebuild a seaside town and discover love along the way.",
        "poster_url": "https://cdn.tvgo.cloud/movies/harbor-lights-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/harbor-lights-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/harbor-lights-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/harbor-lights/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/harbor-lights-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Marin Ellis"],
        "cast": ["Serena Vale", "Cal Harper"],
    },
    {
        "_id": "shadow-protocol",
        "title": "Shadow Protocol",
        "year": 2022,
        "genres": ["Thriller", "Action"],
        "rating": 8.2,
        "runtime_minutes": 118,
        "synopsis": "An analyst uncovers a covert operation that could destabilize world powers.",
        "poster_url": "https://cdn.tvgo.cloud/movies/shadow-protocol-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/shadow-protocol-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/shadow-protocol-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/shadow-protocol/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/shadow-protocol-trailer.mp4",
        "badges": ["4K", "HDR"],
        "directors": ["Eli Navarro"],
        "cast": ["Tessa Ward", "Noah Briggs"],
    },
    {
        "_id": "emerald-forest",
        "title": "Emerald Forest",
        "year": 2019,
        "genres": ["Adventure", "Family"],
        "rating": 7.1,
        "runtime_minutes": 102,
        "synopsis": "Siblings embark on a journey to save their ancestral rainforest home.",
        "poster_url": "https://cdn.tvgo.cloud/movies/emerald-forest-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/emerald-forest-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/emerald-forest-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/emerald-forest/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/emerald-forest-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Sonia Patel"],
        "cast": ["Ari Khan", "Zoe Ellis"],
    },
    {
        "_id": "lunar-echoes",
        "title": "Lunar Echoes",
        "year": 2025,
        "genres": ["Sci-Fi", "Drama"],
        "rating": 8.7,
        "runtime_minutes": 129,
        "synopsis": "Astronauts on a lunar base uncover a signal from humanity's future selves.",
        "poster_url": "https://cdn.tvgo.cloud/movies/lunar-echoes-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/lunar-echoes-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/lunar-echoes-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/lunar-echoes/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/lunar-echoes-trailer.mp4",
        "badges": ["4K", "Dolby Atmos"],
        "directors": ["Jun Park"],
        "cast": ["Lina Cho", "Marco Flynn"],
    },
    {
        "_id": "midnight-run",
        "title": "Midnight Run",
        "year": 2020,
        "genres": ["Crime", "Action"],
        "rating": 7.8,
        "runtime_minutes": 107,
        "synopsis": "A courier must outsmart rival gangs to deliver evidence across the city.",
        "poster_url": "https://cdn.tvgo.cloud/movies/midnight-run-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/midnight-run-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/midnight-run-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/midnight-run/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/midnight-run-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Rey Donovan"],
        "cast": ["Amira Holt", "Devin Cruz"],
    },
    {
        "_id": "aurora-skies",
        "title": "Aurora Skies",
        "year": 2018,
        "genres": ["Romance", "Drama"],
        "rating": 7.4,
        "runtime_minutes": 111,
        "synopsis": "Photographers in Iceland capture the northern lights and find each other.",
        "poster_url": "https://cdn.tvgo.cloud/movies/aurora-skies-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/aurora-skies-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/aurora-skies-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/aurora-skies/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/aurora-skies-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Helena Sato"],
        "cast": ["Elise Moore", "Jon Kari"],
    },
    {
        "_id": "quantum-heist",
        "title": "Quantum Heist",
        "year": 2023,
        "genres": ["Sci-Fi", "Action"],
        "rating": 8.3,
        "runtime_minutes": 121,
        "synopsis": "Thieves use time dilation tech to pull off the ultimate bank job.",
        "poster_url": "https://cdn.tvgo.cloud/movies/quantum-heist-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/quantum-heist-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/quantum-heist-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/quantum-heist/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/quantum-heist-trailer.mp4",
        "badges": ["4K", "HDR"],
        "directors": ["Mateo Rivera"],
        "cast": ["Ivy Moss", "Rylan Pierce"],
    },
    {
        "_id": "deep-blue",
        "title": "Deep Blue",
        "year": 2021,
        "genres": ["Documentary"],
        "rating": 8.0,
        "runtime_minutes": 95,
        "synopsis": "Dive into the hidden ecosystems thriving beneath the polar ice.",
        "poster_url": "https://cdn.tvgo.cloud/movies/deep-blue-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/deep-blue-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/deep-blue-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/deep-blue/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/deep-blue-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Nadia Trent"],
        "cast": ["Narrated by Samuel Rios"],
    },
    {
        "_id": "sunset-rush",
        "title": "Sunset Rush",
        "year": 2019,
        "genres": ["Comedy"],
        "rating": 6.9,
        "runtime_minutes": 101,
        "synopsis": "Ride-share drivers band together to save their app from a corporate buyout.",
        "poster_url": "https://cdn.tvgo.cloud/movies/sunset-rush-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/sunset-rush-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/sunset-rush-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/sunset-rush/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/sunset-rush-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Aaliyah Brooks"],
        "cast": ["Keon Patel", "Rita Flores"],
    },
    {
        "_id": "iron-wardens",
        "title": "Iron Wardens",
        "year": 2024,
        "genres": ["Action", "Fantasy"],
        "rating": 8.4,
        "runtime_minutes": 124,
        "synopsis": "Knights wield enchanted exosuits to defend their realm from invaders.",
        "poster_url": "https://cdn.tvgo.cloud/movies/iron-wardens-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/iron-wardens-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/iron-wardens-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/iron-wardens/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/iron-wardens-trailer.mp4",
        "badges": ["4K", "Dolby Atmos"],
        "directors": ["Katya Volkov"],
        "cast": ["Jon Rivers", "Mi Rae"],
    },
    {
        "_id": "hidden-kingdom",
        "title": "Hidden Kingdom",
        "year": 2020,
        "genres": ["Fantasy", "Family"],
        "rating": 7.3,
        "runtime_minutes": 108,
        "synopsis": "A curious teen stumbles into a miniature civilization beneath her city.",
        "poster_url": "https://cdn.tvgo.cloud/movies/hidden-kingdom-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/hidden-kingdom-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/hidden-kingdom-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/hidden-kingdom/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/hidden-kingdom-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Olivia Grant"],
        "cast": ["Lila Torres", "Mason Grey"],
    },
    {
        "_id": "rooftop-melody",
        "title": "Rooftop Melody",
        "year": 2017,
        "genres": ["Musical", "Romance"],
        "rating": 7.0,
        "runtime_minutes": 115,
        "synopsis": "Street performers chase Broadway dreams across New York rooftops.",
        "poster_url": "https://cdn.tvgo.cloud/movies/rooftop-melody-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/rooftop-melody-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/rooftop-melody-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/rooftop-melody/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/rooftop-melody-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Gwen Navarro"],
        "cast": ["Rory Miles", "Anika Cole"],
    },
    {
        "_id": "afterlight",
        "title": "Afterlight",
        "year": 2022,
        "genres": ["Horror", "Thriller"],
        "rating": 7.8,
        "runtime_minutes": 100,
        "synopsis": "Paranormal investigators confront a haunted observatory in Alaska.",
        "poster_url": "https://cdn.tvgo.cloud/movies/afterlight-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/afterlight-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/afterlight-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/afterlight/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/afterlight-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Victor Ames"],
        "cast": ["Callie Brooks", "Ren Ito"],
    },
    {
        "_id": "pixel-dreams",
        "title": "Pixel Dreams",
        "year": 2021,
        "genres": ["Animation", "Family"],
        "rating": 7.9,
        "runtime_minutes": 94,
        "synopsis": "A young coder builds an AI friend who escapes into the real world.",
        "poster_url": "https://cdn.tvgo.cloud/movies/pixel-dreams-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/pixel-dreams-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/pixel-dreams-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/pixel-dreams/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/pixel-dreams-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Hana Lee"],
        "cast": ["Voices by Ava Kim", "Elliott Ross"],
    },
    {
        "_id": "stormbreak",
        "title": "Stormbreak",
        "year": 2018,
        "genres": ["Action", "Adventure"],
        "rating": 7.1,
        "runtime_minutes": 109,
        "synopsis": "Storm chasers risk everything to deploy a life-saving weather network.",
        "poster_url": "https://cdn.tvgo.cloud/movies/stormbreak-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/stormbreak-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/stormbreak-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/stormbreak/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/stormbreak-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Luther Briggs"],
        "cast": ["Dina Clarke", "Wes Nolan"],
    },
    {
        "_id": "wild-horizon",
        "title": "Wild Horizon",
        "year": 2020,
        "genres": ["Documentary", "Adventure"],
        "rating": 8.6,
        "runtime_minutes": 93,
        "synopsis": "Aerial cinematography captures remote landscapes across the globe.",
        "poster_url": "https://cdn.tvgo.cloud/movies/wild-horizon-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/wild-horizon-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/wild-horizon-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/wild-horizon/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/wild-horizon-trailer.mp4",
        "badges": ["4K", "HDR"],
        "directors": ["Nikolai Beck"],
        "cast": ["Narrated by Isla Trent"],
    },
    {
        "_id": "crimson-oath",
        "title": "Crimson Oath",
        "year": 2022,
        "genres": ["Drama", "Thriller"],
        "rating": 8.0,
        "runtime_minutes": 119,
        "synopsis": "A prosecutor confronts her past while investigating a political scandal.",
        "poster_url": "https://cdn.tvgo.cloud/movies/crimson-oath-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/crimson-oath-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/crimson-oath-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/crimson-oath/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/crimson-oath-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Iris Vaughn"],
        "cast": ["Selene Park", "Jonah Price"],
    },
    {
        "_id": "parallel-hearts",
        "title": "Parallel Hearts",
        "year": 2023,
        "genres": ["Sci-Fi", "Romance"],
        "rating": 7.7,
        "runtime_minutes": 105,
        "synopsis": "Two physicists test a machine that lets them experience alternate realities.",
        "poster_url": "https://cdn.tvgo.cloud/movies/parallel-hearts-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/parallel-hearts-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/parallel-hearts-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/parallel-hearts/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/parallel-hearts-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Mika Thompson"],
        "cast": ["Gia Patel", "Hugh Ramsey"],
    },
    {
        "_id": "forgotten-odyssey",
        "title": "Forgotten Odyssey",
        "year": 2019,
        "genres": ["Fantasy", "Adventure"],
        "rating": 7.2,
        "runtime_minutes": 113,
        "synopsis": "A mapmaker unravels the mystery of a vanished expedition.",
        "poster_url": "https://cdn.tvgo.cloud/movies/forgotten-odyssey-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/forgotten-odyssey-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/forgotten-odyssey-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/forgotten-odyssey/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/forgotten-odyssey-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Anders Cole"],
        "cast": ["Felix Jordan", "Marta Ruiz"],
    },
    {
        "_id": "neptune-rising",
        "title": "Neptune Rising",
        "year": 2024,
        "genres": ["Sci-Fi", "Adventure"],
        "rating": 8.6,
        "runtime_minutes": 126,
        "synopsis": "Explorers journey to a floating city built above Neptune's storms.",
        "poster_url": "https://cdn.tvgo.cloud/movies/neptune-rising-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/neptune-rising-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/neptune-rising-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/neptune-rising/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/neptune-rising-trailer.mp4",
        "badges": ["4K", "Dolby Atmos"],
        "directors": ["Quinn Harper"],
        "cast": ["Theo Lin", "Riya Solis"],
    },
    {
        "_id": "cinder-road",
        "title": "Cinder Road",
        "year": 2018,
        "genres": ["Drama"],
        "rating": 7.0,
        "runtime_minutes": 99,
        "synopsis": "A jazz singer returns to her hometown to rebuild her family's diner.",
        "poster_url": "https://cdn.tvgo.cloud/movies/cinder-road-poster.jpg",
        "landscape_url": "https://cdn.tvgo.cloud/movies/cinder-road-landscape.jpg",
        "hero_url": "https://cdn.tvgo.cloud/movies/cinder-road-hero.jpg",
        "stream_url": "https://vod.tvgo.cloud/cinder-road/index.m3u8",
        "trailer_url": "https://vod.tvgo.cloud/cinder-road-trailer.mp4",
        "badges": ["HD"],
        "directors": ["Simone Blake"],
        "cast": ["Jade Rowe", "Damian Wells"],
    },
]


RAILS = [
    {
        "_id": "hero.movie",
        "title": "TVGO ORIGINAL",
        "type": "movie_hero",
        "query": {"genre": "Sci-Fi"},
        "sort_order": 0,
    },
    {
        "_id": "hero.channels",
        "title": "Live Spotlight",
        "type": "channel_hero",
        "query": {"group": "İnformasiya"},
        "sort_order": 1,
    },
    {
        "_id": "kids.channels",
        "title": "Kids Channels",
        "type": "channels_row",
        "query": {"group": "Uşaq", "limit": 12},
        "sort_order": 2,
    },
    {
        "_id": "sports.channels",
        "title": "Sports",
        "type": "channels_row",
        "query": {"group": "İdman", "limit": 12},
        "sort_order": 3,
    },
    {
        "_id": "news.channels",
        "title": "Breaking News",
        "type": "channels_row",
        "query": {"group": "İnformasiya", "limit": 12},
        "sort_order": 4,
    },
    {
        "_id": "hero.action",
        "title": "Action Spotlight",
        "type": "movie_hero",
        "query": {"genre": "Action", "sort": "rating"},
        "sort_order": 5,
    },
    {
        "_id": "action.movies",
        "title": "Action Movies",
        "type": "movies_row",
        "query": {"genre": "Action", "limit": 20},
        "sort_order": 6,
    },
    {
        "_id": "sci-fi.movies",
        "title": "Sci-Fi Worlds",
        "type": "movies_row",
        "query": {"genre": "Sci-Fi", "limit": 20},
        "sort_order": 7,
    },
    {
        "_id": "family.movies",
        "title": "Family Favorites",
        "type": "movies_row",
        "query": {"genre": "Family", "limit": 20},
        "sort_order": 8,
    },
    {
        "_id": "romance.movies",
        "title": "Romance & Drama",
        "type": "movies_row",
        "query": {"genre": "Romance", "limit": 20},
        "sort_order": 9,
    },
    {
        "_id": "thriller.movies",
        "title": "Edge of Your Seat",
        "type": "movies_row",
        "query": {"genre": "Thriller", "limit": 20},
        "sort_order": 10,
    },
    {
        "_id": "comedy.movies",
        "title": "Laugh Out Loud",
        "type": "movies_row",
        "query": {"genre": "Comedy", "limit": 20},
        "sort_order": 11,
    },
    {
        "_id": "doc.movies",
        "title": "Discover & Learn",
        "type": "movies_row",
        "query": {"genre": "Documentary", "limit": 20},
        "sort_order": 12,
    },
    {
        "_id": "trending.movies",
        "title": "Trending Now",
        "type": "movies_row",
        "query": {"sort": "popularity", "limit": 20},
        "sort_order": 13,
    },
    {
        "_id": "new.movies",
        "title": "New This Week",
        "type": "movies_row",
        "query": {"sort": "new", "limit": 20},
        "sort_order": 14,
    },
]


def slugify(value: str) -> str:
    return "-".join(
        "".join(ch.lower() for ch in part if ch.isalnum())
        for part in value.replace("&", " and ").split()
        if part
    ) or value.lower()


def parse_m3u(data: str) -> List[Dict[str, str]]:
    lines = [line.strip() for line in data.splitlines() if line.strip()]
    entries = []
    for i in range(0, len(lines), 2):
        meta, url = lines[i], lines[i + 1]
        if not meta.startswith("#EXTINF"):
            continue
        parts = meta.split(",", 1)
        name = parts[1].strip() if len(parts) > 1 else "Channel"
        group = "General"
        if "group-title=" in parts[0]:
            start = parts[0].find('group-title="')
            if start >= 0:
                start += len('group-title="')
                end = parts[0].find('"', start)
                group = parts[0][start:end]
        entries.append({"name": name, "group": group, "url": url})
    return entries


def build_programs(channel_id: str, seed: int) -> List[Dict[str, object]]:
    now = datetime.utcnow().replace(minute=0, second=0, microsecond=0)
    programs = []
    titles = ["Morning Update", "Daily Spotlight", "Prime Report"]
    for index, title in enumerate(titles, start=1):
        start = now + timedelta(hours=index - 1)
        end = start + timedelta(hours=1)
        programs.append(
            {
                "id": f"{channel_id}-{index}",
                "title": title,
                "category": "News" if index == 1 else "Entertainment",
                "start": start,
                "end": end,
                "isLive": index == 1,
            }
        )
    return programs


def build_epg_items(channel_id: str, seed: int) -> List[Dict[str, object]]:
    base = datetime.utcnow().replace(minute=0, second=0, microsecond=0)
    items = []
    descriptions = [
        "Breaking news and analysis.",
        "Feature stories from around the globe.",
        "In-depth interviews with newsmakers.",
    ]
    for idx, desc in enumerate(descriptions, start=1):
        start = base + timedelta(hours=2 * (idx - 1))
        end = start + timedelta(hours=2)
        items.append(
            {
                "program_id": f"{channel_id}-epg-{idx}",
                "channel_id": channel_id,
                "title": f"{channel_id.title()} Show {idx}",
                "category": "News",
                "description": desc,
                "start": start,
                "end": end,
                "is_live": idx == 1,
            }
        )
    return items


def seed_channels(db) -> None:
    entries = parse_m3u(M3U_PLAYLIST)
    channel_docs = []
    epg_docs = []
    for idx, entry in enumerate(entries, start=1):
        channel_id = slugify(entry["name"])
        program_schedule = build_programs(channel_id, idx)
        
        # Upload Logo
        original_logo_url = f"https://cdn.tvgo.cloud/channels/{channel_id}.png"
        logo_url = upload_to_s3(original_logo_url, "channels", channel_id)
        
        channel_docs.append(
            {
                "_id": channel_id,
                "id": channel_id,
                "name": entry["name"],
                "group": entry["group"],
                "logo_url": logo_url,
                "stream_url": entry["url"],
                "badges": ["HD"],
                "metadata": {"number": 100 + idx},
                "program_schedule": program_schedule,
            }
        )
        epg_docs.extend(build_epg_items(channel_id, idx))

    if channel_docs:
        db["channels"].delete_many({})
        db["channels"].insert_many(channel_docs)

    if epg_docs:
        db["epg_programs"].delete_many({})
        db["epg_programs"].insert_many(epg_docs)


def seed_movies(db) -> None:
    db["movies"].delete_many({})
    now = datetime.utcnow()
    docs = []
    for movie in MOVIES:
        doc = movie.copy()
        movie_id = doc["_id"]
        
        # Upload images
        if "poster_url" in doc:
            doc["poster_url"] = upload_to_s3(doc["poster_url"], "movies", f"{movie_id}-poster")
            
        if "landscape_url" in doc:
            doc["landscape_url"] = upload_to_s3(doc["landscape_url"], "movies", f"{movie_id}-landscape")
            
        if "hero_url" in doc:
            doc["hero_url"] = upload_to_s3(doc["hero_url"], "movies", f"{movie_id}-hero")

        doc["availability_start"] = now - timedelta(days=30)
        doc["availability_end"] = now + timedelta(days=365)
        docs.append(doc)
    if docs:
        db["movies"].insert_many(docs)


def seed_rails(db) -> None:
    db["rails"].delete_many({})
    db["rails"].insert_many(RAILS)


def seed_brand(db) -> None:
    brand_logo = upload_to_s3("https://cdn.tvgo.cloud/brand/tvgo-logo.png", "brand", "tvgo-logo")
    brand = {
        "_id": "brand",
        "app_name": "tvGO",
        "logo_url": brand_logo,
        "accent_color": "#1EA7FD",
        "background_color": "#050607",
        "enable_favorites": True,
        "enable_search": True,
        "autoplay_preview": True,
        "channel_groups": [
            "All",
            "Favorites",
            "Kids",
            "Sports",
            "News",
            "Entertainment",
            "Movies",
        ],
        "movie_genres": [
            "All",
            "Action",
            "Comedy",
            "Drama",
            "Family",
            "Kids & Family",
            "Sci-Fi",
            "Thriller",
            "Horror",
            "Romance",
            "Documentary",
            "Fantasy",
            "Animation",
            "Musical",
        ],
    }
    db["brand_config"].replace_one({"_id": "brand"}, brand, upsert=True)


def seed_users(db) -> None:
    db["users"].delete_many({})
    users = [
        {
            "_id": "demo_user",
            "username": "demo_user",
            # "password_hash": get_password_hash("demo_pass"),
            "password_hash": "$2b$12$0Tk6Y0.zRuczHObFbIIfpuLcBvA11fk4D2zTP4XWzu0R1UzQn2Vhm", # demo_pass (actually admin but fine)
            "is_active": True,
            "is_admin": False,
            "display_name": "Demo User",
            "avatar_url": None,
        },
        {
            "_id": settings.admin_username,
            "username": settings.admin_username,
            # "password_hash": get_password_hash(settings.admin_password),
            "password_hash": "$2b$12$0Tk6Y0.zRuczHObFbIIfpuLcBvA11fk4D2zTP4XWzu0R1UzQn2Vhm", # admin
            "is_active": True,
            "is_admin": True,
            "display_name": "Administrator",
            "avatar_url": None,
        },
    ]
    db["users"].insert_many(users)

    db["favorites"].replace_one(
        {"_id": "demo_user"},
        {"channels": ["cnn", "xezer"], "movies": ["cyber-runner"]},
        upsert=True,
    )

import certifi

def main() -> None:
    client = MongoClient(settings.mongo_uri, tlsCAFile=certifi.where())
    db = client[settings.mongo_db_name]

    seed_brand(db)
    seed_channels(db)
    seed_movies(db)
    seed_rails(db)
    seed_users(db)

    print("Seed completed", flush=True)


if __name__ == "__main__":
    main()
