#!/usr/bin/env python3
"""
EPG Manager - Download, Parse, and Map TV Channel EPG Data
This script handles:
1. Downloading EPG XML from configurable URLs
2. Parsing channel information (id, name, icon)
3. Mapping channels to your M3U playlist
4. Managing multiple EPG sources
"""

import xml.etree.ElementTree as ET
import urllib.request
import os
import json
import re
from datetime import datetime
from typing import Dict, List, Optional, Tuple
from dataclasses import dataclass, asdict


@dataclass
class Channel:
    """Represents a TV channel from EPG"""
    id: str
    display_name: str
    icon_url: Optional[str] = None
    lang: str = "ru"


@dataclass
class Program:
    """Represents a TV program from EPG"""
    channel_id: str
    title: str
    start: str
    stop: str
    description: Optional[str] = None
    category: Optional[str] = None


class EPGConfig:
    """Configuration for EPG sources"""
    
    def __init__(self, config_file: str = "epg_config.json"):
        self.config_file = config_file
        self.sources: List[Dict] = []
        self.channel_mappings: Dict[str, str] = {}  # m3u_name -> epg_channel_id
        self.load_config()
    
    def load_config(self):
        """Load configuration from file"""
        if os.path.exists(self.config_file):
            with open(self.config_file, 'r', encoding='utf-8') as f:
                data = json.load(f)
                self.sources = data.get('epg_sources', [])
                self.channel_mappings = data.get('channel_mappings', {})
        else:
            # Create default config
            self.sources = [
                {
                    "name": "EPG Service RU",
                    "url": "http://xml-epgservice.cdnvideo.ru/EPGService/hs/xmldata/6c038e50-4241-4dc9-ad30-ff1f24b96fd/tv_pack",
                    "enabled": True,
                    "priority": 1
                }
            ]
            self.save_config()
    
    def save_config(self):
        """Save configuration to file"""
        data = {
            'epg_sources': self.sources,
            'channel_mappings': self.channel_mappings,
            'last_updated': datetime.now().isoformat()
        }
        with open(self.config_file, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    
    def add_source(self, name: str, url: str, enabled: bool = True, priority: int = 1):
        """Add a new EPG source"""
        self.sources.append({
            "name": name,
            "url": url,
            "enabled": enabled,
            "priority": priority
        })
        self.save_config()
    
    def add_mapping(self, m3u_name: str, epg_channel_id: str):
        """Map an M3U channel name to an EPG channel ID"""
        self.channel_mappings[m3u_name] = epg_channel_id
        self.save_config()


class EPGParser:
    """Parse EPG XML files"""
    
    def __init__(self, xml_path: Optional[str] = None, xml_content: Optional[str] = None):
        self.xml_path = xml_path
        self.xml_content = xml_content
        self.channels: Dict[str, Channel] = {}
        self.programs: List[Program] = []
    
    def parse(self) -> Tuple[Dict[str, Channel], List[Program]]:
        """Parse the XML file and extract channels and programs"""
        
        if self.xml_path:
            tree = ET.parse(self.xml_path)
            root = tree.getroot()
        elif self.xml_content:
            root = ET.fromstring(self.xml_content)
        else:
            raise ValueError("Either xml_path or xml_content must be provided")
        
        # Parse channels
        for channel_elem in root.findall('channel'):
            channel_id = channel_elem.get('id')
            display_name_elem = channel_elem.find('display-name')
            icon_elem = channel_elem.find('icon')
            
            if channel_id and display_name_elem is not None:
                channel = Channel(
                    id=channel_id,
                    display_name=display_name_elem.text or "",
                    icon_url=icon_elem.get('src') if icon_elem is not None else None,
                    lang=display_name_elem.get('lang', 'ru')
                )
                self.channels[channel_id] = channel
        
        # Parse programs
        for programme_elem in root.findall('programme'):
            channel_id = programme_elem.get('channel')
            start = programme_elem.get('start')
            stop = programme_elem.get('stop')
            
            title_elem = programme_elem.find('title')
            desc_elem = programme_elem.find('desc')
            category_elem = programme_elem.find('category')
            
            if channel_id and start and stop and title_elem is not None:
                program = Program(
                    channel_id=channel_id,
                    title=title_elem.text or "",
                    start=start,
                    stop=stop,
                    description=desc_elem.text if desc_elem is not None else None,
                    category=category_elem.text if category_elem is not None else None
                )
                self.programs.append(program)
        
        return self.channels, self.programs


class EPGDownloader:
    """Download EPG data from URLs"""
    
    def __init__(self, cache_dir: str = ".epg_cache"):
        self.cache_dir = cache_dir
        if not os.path.exists(cache_dir):
            os.makedirs(cache_dir)
    
    def download(self, url: str, force: bool = False) -> str:
        """Download EPG XML from URL and cache it"""
        
        # Create cache filename from URL
        cache_file = os.path.join(
            self.cache_dir,
            re.sub(r'[^\w]', '_', url)[-50:] + ".xml"
        )
        
        # Check if cached file is recent (less than 24 hours old)
        if not force and os.path.exists(cache_file):
            file_age = datetime.now().timestamp() - os.path.getmtime(cache_file)
            if file_age < 86400:  # 24 hours
                print(f"Using cached EPG data: {cache_file}")
                return cache_file
        
        print(f"Downloading EPG from: {url}")
        try:
            # Download with timeout
            req = urllib.request.Request(
                url,
                headers={'User-Agent': 'Mozilla/5.0 (compatible; EPGManager/1.0)'}
            )
            with urllib.request.urlopen(req, timeout=60) as response:
                content = response.read()
            
            # Save to cache
            with open(cache_file, 'wb') as f:
                f.write(content)
            
            print(f"EPG downloaded and cached: {cache_file}")
            return cache_file
            
        except Exception as e:
            print(f"Error downloading EPG: {e}")
            if os.path.exists(cache_file):
                print("Using stale cached data")
                return cache_file
            raise


class M3UParser:
    """Parse M3U playlist files"""
    
    def __init__(self, m3u_path: str):
        self.m3u_path = m3u_path
        self.channels: List[Dict] = []
    
    def parse(self) -> List[Dict]:
        """Parse M3U file and extract channel info"""
        
        with open(self.m3u_path, 'r', encoding='utf-8') as f:
            lines = f.readlines()
        
        current_channel = None
        
        for line in lines:
            line = line.strip()
            
            if line.startswith('#EXTINF:'):
                # Extract channel info
                match = re.search(r'tvg-name="([^"]*)"', line)
                name = match.group(1) if match else ""
                
                # Get display name after the comma
                display_match = re.search(r',(.+)$', line)
                display_name = display_match.group(1).strip() if display_match else name
                
                # Extract tvg-id if present
                id_match = re.search(r'tvg-id="([^"]*)"', line)
                tvg_id = id_match.group(1) if id_match else ""
                
                # Extract tvg-logo if present
                logo_match = re.search(r'tvg-logo="([^"]*)"', line)
                logo = logo_match.group(1) if logo_match else ""
                
                # Extract group-title if present
                group_match = re.search(r'group-title="([^"]*)"', line)
                group = group_match.group(1) if group_match else ""
                
                current_channel = {
                    'tvg_name': name,
                    'display_name': display_name,
                    'tvg_id': tvg_id,
                    'logo': logo,
                    'group': group,
                    'url': ''
                }
            
            elif line and not line.startswith('#') and current_channel:
                current_channel['url'] = line
                self.channels.append(current_channel)
                current_channel = None
        
        return self.channels


class ChannelMapper:
    """Map M3U channels to EPG channels"""
    
    def __init__(self, epg_channels: Dict[str, Channel], m3u_channels: List[Dict]):
        self.epg_channels = epg_channels
        self.m3u_channels = m3u_channels
        self.mappings: Dict[str, str] = {}
    
    def auto_map(self) -> Dict[str, str]:
        """Attempt automatic mapping based on name similarity"""
        
        for m3u_channel in self.m3u_channels:
            m3u_name = m3u_channel['tvg_name'].lower().strip()
            m3u_display = m3u_channel['display_name'].lower().strip()
            
            best_match = None
            best_score = 0
            
            for epg_id, epg_channel in self.epg_channels.items():
                epg_name = epg_channel.display_name.lower().strip()
                
                # Calculate similarity score
                score = self._similarity_score(m3u_name, epg_name)
                score = max(score, self._similarity_score(m3u_display, epg_name))
                
                if score > best_score:
                    best_score = score
                    best_match = epg_id
            
            # Only map if score is above threshold
            if best_match and best_score > 0.6:
                self.mappings[m3u_channel['tvg_name']] = best_match
        
        return self.mappings
    
    def _similarity_score(self, s1: str, s2: str) -> float:
        """Calculate similarity between two strings"""
        if s1 == s2:
            return 1.0
        if s1 in s2 or s2 in s1:
            return 0.8
        
        # Word overlap
        words1 = set(s1.split())
        words2 = set(s2.split())
        if words1 and words2:
            overlap = len(words1 & words2)
            total = len(words1 | words2)
            return overlap / total
        
        return 0.0
    
    def generate_mapped_m3u(self, output_path: str):
        """Generate a new M3U with EPG mappings"""
        
        with open(output_path, 'w', encoding='utf-8') as f:
            f.write('#EXTM3U\n')
            
            for m3u_channel in self.m3u_channels:
                tvg_name = m3u_channel['tvg_name']
                epg_id = self.mappings.get(tvg_name, '')
                
                # Get logo from EPG if mapped
                logo = m3u_channel.get('logo', '')
                if epg_id and epg_id in self.epg_channels:
                    epg_channel = self.epg_channels[epg_id]
                    if epg_channel.icon_url and not logo:
                        logo = epg_channel.icon_url
                
                # Build EXTINF line
                extinf = f'#EXTINF:-1 tvg-id="{epg_id}" tvg-name="{tvg_name}"'
                if logo:
                    extinf += f' tvg-logo="{logo}"'
                if m3u_channel.get('group'):
                    extinf += f' group-title="{m3u_channel["group"]}"'
                extinf += f',{m3u_channel["display_name"]}'
                
                f.write(extinf + '\n')
                f.write(m3u_channel['url'] + '\n')


class EPGManager:
    """Main manager class for EPG operations"""
    
    def __init__(self, config_file: str = "epg_config.json"):
        self.config = EPGConfig(config_file)
        self.downloader = EPGDownloader()
        self.channels: Dict[str, Channel] = {}
        self.programs: List[Program] = []
    
    def load_from_file(self, xml_path: str):
        """Load EPG from local XML file"""
        print(f"Loading EPG from: {xml_path}")
        parser = EPGParser(xml_path=xml_path)
        self.channels, self.programs = parser.parse()
        print(f"Loaded {len(self.channels)} channels and {len(self.programs)} programs")
    
    def load_from_url(self, url: str, force: bool = False):
        """Download and load EPG from URL"""
        xml_path = self.downloader.download(url, force)
        self.load_from_file(xml_path)
    
    def load_all_sources(self, force: bool = False):
        """Load EPG from all enabled sources"""
        for source in sorted(self.config.sources, key=lambda x: x.get('priority', 1)):
            if source.get('enabled', True):
                try:
                    print(f"\nLoading source: {source['name']}")
                    self.load_from_url(source['url'], force)
                except Exception as e:
                    print(f"Error loading source {source['name']}: {e}")
    
    def get_channel(self, channel_id: str) -> Optional[Channel]:
        """Get channel by ID"""
        return self.channels.get(channel_id)
    
    def search_channels(self, query: str) -> List[Channel]:
        """Search channels by name"""
        query = query.lower()
        return [
            channel for channel in self.channels.values()
            if query in channel.display_name.lower()
        ]
    
    def list_channels(self, limit: int = None) -> List[Channel]:
        """List all channels"""
        channels = list(self.channels.values())
        if limit:
            channels = channels[:limit]
        return channels
    
    def export_channels_json(self, output_path: str):
        """Export channels to JSON file"""
        data = [asdict(ch) for ch in self.channels.values()]
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"Exported {len(data)} channels to {output_path}")
    
    def export_mappings_json(self, m3u_path: str, output_path: str):
        """Generate and export channel mappings"""
        # Parse M3U
        m3u_parser = M3UParser(m3u_path)
        m3u_channels = m3u_parser.parse()
        
        # Create mapper and auto-map
        mapper = ChannelMapper(self.channels, m3u_channels)
        mappings = mapper.auto_map()
        
        # Save mappings
        with open(output_path, 'w', encoding='utf-8') as f:
            json.dump(mappings, f, ensure_ascii=False, indent=2)
        
        print(f"Generated {len(mappings)} mappings out of {len(m3u_channels)} channels")
        return mappings


def main():
    """Main entry point - demo usage"""
    import argparse
    
    parser = argparse.ArgumentParser(description='EPG Manager - Manage TV channel EPG data')
    parser.add_argument('--xml', '-x', help='Path to local XML file')
    parser.add_argument('--url', '-u', help='URL to download EPG from')
    parser.add_argument('--m3u', '-m', help='Path to M3U file for mapping')
    parser.add_argument('--output', '-o', help='Output path for exports')
    parser.add_argument('--list', '-l', action='store_true', help='List channels')
    parser.add_argument('--search', '-s', help='Search channels by name')
    parser.add_argument('--export-json', action='store_true', help='Export channels to JSON')
    parser.add_argument('--export-mappings', action='store_true', help='Generate M3U to EPG mappings')
    parser.add_argument('--limit', type=int, default=50, help='Limit results')
    
    args = parser.parse_args()
    
    manager = EPGManager()
    
    # Load EPG data
    if args.xml:
        manager.load_from_file(args.xml)
    elif args.url:
        manager.load_from_url(args.url)
    else:
        # Try to load from local TV_Pack.xml
        local_xml = os.path.join(os.path.dirname(__file__), 'TV_Pack.xml')
        if os.path.exists(local_xml):
            manager.load_from_file(local_xml)
        else:
            print("No EPG source specified. Use --xml or --url")
            return
    
    # Perform requested action
    if args.list:
        print(f"\n{'='*60}")
        print(f"{'ID':<15} {'Name':<45}")
        print(f"{'='*60}")
        for channel in manager.list_channels(args.limit):
            print(f"{channel.id:<15} {channel.display_name:<45}")
    
    if args.search:
        print(f"\nSearching for: {args.search}")
        results = manager.search_channels(args.search)
        print(f"Found {len(results)} channels:")
        for channel in results[:args.limit]:
            print(f"  {channel.id}: {channel.display_name}")
    
    if args.export_json:
        output_path = args.output or 'epg_channels.json'
        manager.export_channels_json(output_path)
    
    if args.export_mappings and args.m3u:
        output_path = args.output or 'channel_mappings.json'
        manager.export_mappings_json(args.m3u, output_path)


if __name__ == '__main__':
    main()
