import axios from 'axios';
import { Channel, Movie } from './types';

// API URL - defaults to local backend, can be overridden with VITE_API_URL
export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

const api = axios.create({
    baseURL: API_URL,
    headers: {
        'Content-Type': 'application/json'
    }
});

// Auth State
let authToken: string | null = localStorage.getItem('tvgo_token');

// Add Token Interceptor
api.interceptors.request.use((config) => {
    if (authToken) {
        config.headers.Authorization = `Bearer ${authToken}`;
    }
    return config;
});

// Helper to resolve absolute URLs
const resolveUrl = (path: string | undefined): string => {
    if (!path) return '';
    if (path.startsWith('http')) return path;
    if (path.startsWith('/')) return `${API_URL}${path}`;
    return `${API_URL}/${path}`;
};

// --- App Config Types ---
export interface AppBrand {
    appName: string;
    logoUrl: string;
    accentColor: string;
    backgroundColor: string;
}

export interface AppFeatures {
    enableFavorites: boolean;
    enableSearch: boolean;
    autoplayPreview: boolean;
    enableLiveTv: boolean;
    enableVod: boolean;
}

export interface AppConfig {
    brand: AppBrand;
    features: AppFeatures;
    channelGroups: string[];
    movieGenres: string[];
}

// Cached config
let cachedConfig: AppConfig | null = null;

// Fetch app config (logo, colors, features)
export const fetchConfig = async (): Promise<AppConfig | null> => {
    if (cachedConfig) return cachedConfig;

    try {
        const response = await api.get('/api/config');
        cachedConfig = response.data;
        return cachedConfig;
    } catch (error) {
        console.error('Failed to fetch config:', error);
        return null;
    }
};

// --- Schedule Types ---
export interface ScheduleProgramItem {
    id: string;
    title: string;
    start: string;
    end: string;
    description: string;
    category: string;
    isLive: boolean;
}

export interface ChannelScheduleResponse {
    channel_id: string;
    programs: ScheduleProgramItem[];
}

// --- API Methods ---

export const login = async () => {
    try {
        // Use subscriber credentials
        const response = await api.post('/api/auth/subscriber/login', {
            username: 'Pr23eBtG',
            password: 'uCFzsGrP',
            mac_address: '00:00:00:00:00:WEB',
            device_name: 'Web Client'
        });
        authToken = response.data.accessToken;
        if (authToken) {
            localStorage.setItem('tvgo_token', authToken);
        }
        return true;
    } catch (error) {
        console.error('Login failed:', error);
        return false;
    }
};

// Ensure we are logged in before fetching data
const ensureAuth = async () => {
    if (!authToken) {
        await login();
    }
};

export const fetchChannels = async (): Promise<Channel[]> => {
    try {
        await ensureAuth();
        const response = await api.get('/api/channels');
        return response.data.items.map((dto: any) => ({
            id: dto.id,
            name: dto.name,
            logo: resolveUrl(dto.logo),
            category: dto.category || dto.group || 'All',
            streamUrl: dto.streamUrl,
            description: dto.description || '',
            logoColor: dto.logoColor || '#000000',
            order: dto.order,
            // Map programSchedule from backend to schedule format
            schedule: (dto.programSchedule || []).map((prog: any) => {
                const startTime = new Date(prog.start);
                const endTime = new Date(prog.end);
                const now = new Date();
                // Calculate isLive based on current time
                const isLive = now >= startTime && now <= endTime;

                return {
                    time: startTime.toLocaleTimeString('en-US', {
                        hour: '2-digit',
                        minute: '2-digit',
                        hour12: false
                    }),
                    title: prog.title,
                    duration: `${Math.round((endTime.getTime() - startTime.getTime()) / 60000)} min`,
                    isLive
                };
            })
        }));
    } catch (error) {
        console.error('Error fetching channels:', error);
        return [];
    }
};

// Fetch categories dynamically from channels or use defaults
export const fetchChannelCategories = async (): Promise<{ id: string; name: string }[]> => {
    try {
        await ensureAuth();
        // Try to fetch categories endpoint if exists
        try {
            const response = await api.get('/api/categories');
            if (response.data && response.data.items) {
                return [
                    { id: 'all', name: 'All Channels' },
                    { id: 'favorites', name: 'Favorites' },
                    ...response.data.items.map((c: any) => ({
                        id: c.id || c.name.toLowerCase(),
                        name: c.name
                    }))
                ];
            }
        } catch {
            // Categories endpoint doesn't exist, extract from channels
        }

        // Extract unique categories from channels
        const channels = await fetchChannels();
        const uniqueCategories = new Set<string>();
        channels.forEach(ch => {
            if (ch.category && ch.category !== 'All') {
                uniqueCategories.add(ch.category);
            }
        });

        return [
            { id: 'all', name: 'All Channels' },
            { id: 'favorites', name: 'Favorites' },
            ...Array.from(uniqueCategories).map(cat => ({
                id: cat.toLowerCase(),
                name: cat.charAt(0).toUpperCase() + cat.slice(1)
            }))
        ];
    } catch (error) {
        console.error('Error fetching categories:', error);
        // Return default categories
        return [
            { id: 'all', name: 'All Channels' },
            { id: 'favorites', name: 'Favorites' },
            { id: 'kids', name: 'Kids' },
            { id: 'sports', name: 'Sports' },
            { id: 'news', name: 'News' },
            { id: 'entertainment', name: 'Entertainment' },
            { id: 'movies', name: 'Movies' },
        ];
    }
};

export const fetchChannelSchedule = async (channelId: string): Promise<ScheduleProgramItem[]> => {
    try {
        const response = await api.get(`/api/epg/schedule/${channelId}`);
        return response.data.programs;
    } catch (error) {
        console.error('Error fetching schedule:', error);
        return [];
    }
};

export const fetchMovies = async (): Promise<Movie[]> => {
    try {
        await ensureAuth();
        const response = await api.get('/api/movies');
        // Map backend DTO to domain model
        return response.data.items.map((dto: any) => ({
            id: dto.id,
            title: dto.title,
            thumbnail: resolveUrl(dto.images?.poster || dto.thumbnail),
            backdrop: resolveUrl(dto.images?.hero || dto.images?.landscape || dto.backdrop),
            category: dto.badges?.[0] || 'General',
            year: dto.year || 2024,
            rating: dto.rating ? dto.rating.toFixed(1) : 'N/A',
            description: dto.synopsis || dto.description || '',
            videoUrl: dto.media?.streamUrl || dto.videoUrl || '',
            trailerUrl: dto.media?.trailerUrl || dto.trailerUrl || '',
            genre: dto.genres || [],
            runtime: dto.runtimeMinutes || 0,
            directors: dto.credits?.directors || [],
            cast: dto.credits?.cast || []
        }));
    } catch (error) {
        console.error('Error fetching movies:', error);
        return [];
    }
};

// --- Games API ---

export interface GameItem {
    id: string;
    name: string;
    description: string;
    imageUrl: string;
    gameUrl: string;
    category: string;
    isActive: boolean;
    order: number;
}

export interface GamesResponse {
    items: GameItem[];
    categories: string[];
}

export const fetchGames = async (): Promise<GamesResponse> => {
    try {
        await ensureAuth();
        const response = await api.get('/api/games');
        return {
            items: response.data.items.map((g: any) => ({
                ...g,
                imageUrl: resolveUrl(g.imageUrl)
            })),
            categories: response.data.categories
        };
    } catch (error) {
        console.error('Error fetching games:', error);
        return { items: [], categories: [] };
    }
};

// --- Messages API ---

export interface MessageItem {
    id: string;
    title: string;
    body: string;
    url?: string;
    createdAt: string;
    isRead: boolean;
}

export const fetchMessages = async (): Promise<MessageItem[]> => {
    try {
        await ensureAuth();
        const response = await api.get('/api/messages');
        return response.data.items.map((m: any) => ({
            ...m,
            createdAt: m.created_at || m.createdAt || new Date().toISOString()
        }));
    } catch (error) {
        console.error('Error fetching messages:', error);
        return [];
    }
};

export const markMessageRead = async (id: string): Promise<void> => {
    try {
        await api.post(`/api/messages/${id}/read`);
    } catch (error) {
        console.error('Error marking message read:', error);
    }
};
