/**
 * API Client for tvgo-backend
 * Handles all communication with the backend API
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'https://hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws';

// Token management
let accessToken: string | null = localStorage.getItem('accessToken');
let refreshToken: string | null = localStorage.getItem('refreshToken');

export const setTokens = (access: string, refresh: string) => {
    accessToken = access;
    refreshToken = refresh;
    localStorage.setItem('accessToken', access);
    localStorage.setItem('refreshToken', refresh);
};

export const clearTokens = () => {
    accessToken = null;
    refreshToken = null;
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
};

export const getAccessToken = () => accessToken;

// Helper to resolve relative image URLs to full URLs
export const resolveImageUrl = (url: string | undefined | null): string | undefined => {
    if (!url) return undefined;
    if (url.startsWith('http://') || url.startsWith('https://')) {
        return url;
    }
    // Relative URL - prepend API base
    return `${API_BASE_URL}${url}`;
};

// API request helper
async function apiRequest<T>(
    endpoint: string,
    options: RequestInit = {}
): Promise<T> {
    const url = `${API_BASE_URL}${endpoint}`;

    const headers: HeadersInit = {
        'Content-Type': 'application/json',
        ...options.headers,
    };

    if (accessToken) {
        (headers as Record<string, string>)['Authorization'] = `Bearer ${accessToken}`;
    }

    const response = await fetch(url, {
        ...options,
        headers,
    });

    if (response.status === 401 && refreshToken) {
        // Try to refresh the token
        const refreshed = await refreshAccessToken();
        if (refreshed) {
            // Retry the original request
            (headers as Record<string, string>)['Authorization'] = `Bearer ${accessToken}`;
            const retryResponse = await fetch(url, { ...options, headers });
            if (!retryResponse.ok) {
                throw new Error(`API Error: ${retryResponse.status}`);
            }
            return retryResponse.json();
        }
    }

    if (!response.ok) {
        const errorData = await response.json().catch(() => ({}));
        throw new Error(errorData.detail || `API Error: ${response.status}`);
    }

    return response.json();
}

async function refreshAccessToken(): Promise<boolean> {
    if (!refreshToken) return false;

    try {
        const response = await fetch(`${API_BASE_URL}/api/auth/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ refreshToken }),
        });

        if (!response.ok) {
            clearTokens();
            return false;
        }

        const data = await response.json();
        accessToken = data.accessToken;
        localStorage.setItem('accessToken', data.accessToken);
        return true;
    } catch {
        clearTokens();
        return false;
    }
}

// Types
export interface User {
    id: string;
    username: string;
    displayName?: string;
    avatarUrl?: string;
}

export interface LoginResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
    user: User;
}

export interface Channel {
    id: string;
    name: string;
    group?: string;
    logo?: string;
    streamUrl: string;
    drm?: { type: string; licenseUrl: string };
    lang?: string[];
    country?: string;
    badges?: string[];
    metadata?: Record<string, unknown>;
    streamerName?: string;
    order?: number;
}

export interface ChannelsListResponse {
    total: number;
    items: Channel[];
    nextOffset?: number;
}

export interface Streamer {
    id: string;
    name: string;
    url: string;
    status: string;
    last_sync?: string;
    channel_count: number;
}

export interface StreamerListResponse {
    items: Streamer[];
    total: number;
}

export interface M3UChannelPreview {
    id: string;
    name: string;
    group?: string;
    logo_url?: string;
    stream_url: string;
}

export interface M3UParseResponse {
    channels: M3UChannelPreview[];
    total: number;
}

export interface Package {
    id: string;
    name: string;
    description?: string;
    price?: string;
    channel_ids: string[];
    created_at: string;
    updated_at?: string;
}

export interface PackageListResponse {
    packages: Package[];
    total: number;
}

export interface DashboardStats {
    total_channels: number;
    active_channels: number;
    inactive_channels: number;
    total_streamers: number;
    total_packages: number;
}

export interface BrandConfig {
    appName: string;
    logoUrl?: string;
    accentColor?: string;
    backgroundColor?: string;
}

export interface Features {
    enableFavorites: boolean;
    enableSearch: boolean;
    autoplayPreview: boolean;
    enableLiveTv: boolean;
    enableVod: boolean;
}

export interface Movie {
    id: string;
    title: string;
    year?: number;
    genres?: string[];
    rating?: number;
    runtimeMinutes?: number;
    synopsis?: string;
    images?: {
        poster?: string;
        landscape?: string;
        hero?: string;
    };
    media?: {
        streamUrl?: string;
        trailerUrl?: string;
        drm?: { type: string; licenseUrl: string };
    };
    badges?: string[];
    credits?: {
        directors?: string[];
        cast?: string[];
    };
    availability?: {
        start?: string;
        end?: string;
    };
    order?: number;
}

export interface MoviesListResponse {
    items: Movie[];
    total: number;
    nextOffset?: number;
}

// EPG Types
export interface EPGSource {
    id: string;
    name: string;
    url: string;
    enabled: boolean;
    priority: number;
    description?: string;
    last_sync?: string;
    channel_count: number;
}

export interface EPGChannel {
    id: string;
    display_name: string;
    icon_url?: string;
    lang: string;
}

export interface EPGPreviewResponse {
    channels: EPGChannel[];
    programs_count: number;
    source_url: string;
}

export interface EPGSyncResult {
    status: string;
    channels_parsed: number;
    programs_parsed: number;
    mappings_applied: number;
    errors: string[];
}

export interface ChannelEPGMapping {
    channel_id: string;
    channel_name: string;
    epg_id?: string;
    has_mapping: boolean;
}

// API methods
export const api = {
    // Auth
    auth: {
        login: async (username: string, password: string): Promise<LoginResponse> => {
            const response = await apiRequest<LoginResponse>('/api/auth/login', {
                method: 'POST',
                body: JSON.stringify({ username, password }),
            });
            setTokens(response.accessToken, response.refreshToken);
            return response;
        },

        logout: () => {
            clearTokens();
        },

        me: () => apiRequest<User>('/api/auth/me'),

        bootstrapAdmin: () => apiRequest('/api/auth/bootstrap-admin', { method: 'POST' }),
    },

    // Channels (using admin endpoint for faster response - no programSchedule)
    channels: {
        list: async (params?: { group?: string; search?: string; limit?: number; offset?: number }): Promise<ChannelsListResponse> => {
            // Use admin endpoint which returns lighter data (no programSchedule)
            const channels = await apiRequest<Channel[]>('/api/admin/channels');

            // Apply client-side filtering if needed
            let filtered = channels;
            if (params?.group) {
                filtered = filtered.filter(ch => ch.group === params.group);
            }
            if (params?.search) {
                const search = params.search.toLowerCase();
                filtered = filtered.filter(ch =>
                    ch.name.toLowerCase().includes(search) ||
                    ch.id.toLowerCase().includes(search)
                );
            }

            // Apply pagination
            const offset = params?.offset || 0;
            const limit = params?.limit || 50;
            const paginated = filtered.slice(offset, offset + limit);

            return {
                items: paginated,
                total: filtered.length,
                nextOffset: offset + limit < filtered.length ? offset + limit : undefined,
            };
        },

        get: (id: string) => apiRequest<Channel>(`/api/channels/${id}`),

        // Admin endpoints
        adminList: () => apiRequest<Channel[]>('/api/admin/channels'),

        create: (channel: Partial<Channel> & { id: string; name: string; stream_url: string }) =>
            apiRequest<Channel>('/api/admin/channels', {
                method: 'POST',
                body: JSON.stringify(channel),
            }),

        update: (id: string, channel: Partial<Channel>) =>
            apiRequest<Channel>(`/api/admin/channels/${id}`, {
                method: 'PUT',
                body: JSON.stringify(channel),
            }),

        reorder: (items: { id: string; order: number }[]) =>
            apiRequest('/api/admin/channels/reorder', {
                method: 'PUT',
                body: JSON.stringify({ items }),
            }),

        delete: (id: string) =>
            apiRequest(`/api/admin/channels/${id}`, { method: 'DELETE' }),
    },

    // Streamers
    streamers: {
        list: () => apiRequest<StreamerListResponse>('/api/admin/streamers'),

        get: (id: string) => apiRequest<Streamer>(`/api/admin/streamers/${id}`),

        create: (data: { name: string; url: string }) =>
            apiRequest<Streamer>('/api/admin/streamers', {
                method: 'POST',
                body: JSON.stringify(data),
            }),

        update: (id: string, data: { name: string; url: string }) =>
            apiRequest<Streamer>(`/api/admin/streamers/${id}`, {
                method: 'PUT',
                body: JSON.stringify(data),
            }),

        delete: (id: string) =>
            apiRequest(`/api/admin/streamers/${id}`, { method: 'DELETE' }),

        sync: (id: string) =>
            apiRequest<Streamer>(`/api/admin/streamers/${id}/sync`, { method: 'POST' }),
    },

    // M3U Ingestion
    ingest: {
        previewM3UUrl: (url: string, streamerName?: string) =>
            apiRequest<M3UParseResponse>('/api/admin/ingest/m3u-url/preview', {
                method: 'POST',
                body: JSON.stringify({ url, streamer_name: streamerName }),
            }),

        previewM3UFile: async (file: File): Promise<M3UParseResponse> => {
            const formData = new FormData();
            formData.append('file', file);

            // Use direct fetch for FormData - apiRequest adds Content-Type: application/json
            // which breaks multipart/form-data uploads
            const url = `${API_BASE_URL}/api/admin/ingest/m3u/preview`;
            const headers: HeadersInit = {};
            if (accessToken) {
                headers['Authorization'] = `Bearer ${accessToken}`;
            }

            const response = await fetch(url, {
                method: 'POST',
                headers,
                body: formData,
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || `HTTP ${response.status}`);
            }

            return response.json();
        },

        ingestM3UUrl: (url: string, streamerName?: string, channelIds?: string[]) =>
            apiRequest<{ status: string; created: number; updated: number; total: number }>(
                '/api/admin/ingest/m3u-url',
                {
                    method: 'POST',
                    body: JSON.stringify({ url, streamer_name: streamerName, channel_ids: channelIds }),
                }
            ),
    },

    // Packages
    packages: {
        list: () => apiRequest<PackageListResponse>('/api/admin/packages'),

        get: (id: string) => apiRequest<Package>(`/api/admin/packages/${id}`),

        create: (data: { name: string; description?: string; price?: string; channel_ids: string[] }) =>
            apiRequest<Package>('/api/admin/packages', {
                method: 'POST',
                body: JSON.stringify(data),
            }),

        update: (id: string, data: { name?: string; description?: string; price?: string; channel_ids?: string[] }) =>
            apiRequest<Package>(`/api/admin/packages/${id}`, {
                method: 'PUT',
                body: JSON.stringify(data),
            }),

        delete: (id: string) =>
            apiRequest(`/api/admin/packages/${id}`, { method: 'DELETE' }),
    },

    // Config & Stats
    config: {
        get: () => apiRequest('/api/config'),

        getAdmin: () => apiRequest('/api/admin/config'),

        updateBrand: (brand: BrandConfig) =>
            apiRequest('/api/admin/config/brand', {
                method: 'PUT',
                body: JSON.stringify(brand),
            }),

        getStats: () => apiRequest<DashboardStats>('/api/admin/config/stats'),

        updateFeatures: (features: Features) =>
            apiRequest('/api/admin/config/features', {
                method: 'PUT',
                body: JSON.stringify(features),
            }),
    },

    // Movies Management
    movies: {
        list: (params?: { search?: string; limit?: number; offset?: number }) => {
            const query = new URLSearchParams();
            if (params?.search) query.set('search', params.search);
            if (params?.limit) query.set('limit', params.limit.toString());
            if (params?.offset) query.set('offset', params.offset.toString());
            const queryString = query.toString();
            return apiRequest<Movie[]>(`/api/admin/movies${queryString ? `?${queryString}` : ''}`);
        },

        get: (id: string) => apiRequest<Movie>(`/api/admin/movies/${id}`),

        create: (data: Partial<Movie> & { id: string; title: string }) =>
            apiRequest<Movie>('/api/admin/movies', {
                method: 'POST',
                body: JSON.stringify(data),
            }),

        update: (id: string, data: Partial<Movie>) =>
            apiRequest<Movie>(`/api/admin/movies/${id}`, {
                method: 'PUT',
                body: JSON.stringify(data),
            }),

        delete: (id: string) =>
            apiRequest(`/api/admin/movies/${id}`, { method: 'DELETE' }),

        reorder: (items: { id: string; order: number }[]) =>
            apiRequest('/api/admin/movies/reorder', {
                method: 'POST',
                body: JSON.stringify({ items }),
            }),

        batchCreate: (movies: Array<Partial<Movie> & { id: string; title: string }>) =>
            apiRequest<{ status: string; created: number; skipped: number }>('/api/admin/movies/batch', {
                method: 'POST',
                body: JSON.stringify(movies),
            }),

        enrich: (id: string) =>
            apiRequest<Movie>(`/api/admin/movies/${id}/enrich`, {
                method: 'POST',
            }),

        enrichAll: () =>
            apiRequest<{ status: string; enriched: number; failed: number; total: number }>('/api/admin/movies/enrich-all', {
                method: 'POST',
            }),
    },

    // EPG Management
    epg: {
        listSources: () => apiRequest<{ sources: EPGSource[]; total: number }>('/api/admin/epg/sources'),

        createSource: (data: { name: string; url: string; enabled?: boolean; priority?: number; description?: string }) =>
            apiRequest<EPGSource>('/api/admin/epg/sources', {
                method: 'POST',
                body: JSON.stringify(data),
            }),

        deleteSource: (id: string) =>
            apiRequest(`/api/admin/epg/sources/${id}`, { method: 'DELETE' }),

        preview: (url: string, force?: boolean) =>
            apiRequest<EPGPreviewResponse>(`/api/admin/epg/preview?url=${encodeURIComponent(url)}&force=${force || false}`, {
                method: 'POST',
            }),

        sync: (params: { sourceId?: string; url?: string; force?: boolean }) => {
            const query = new URLSearchParams();
            if (params.sourceId) query.set('source_id', params.sourceId);
            if (params.url) query.set('url', params.url);
            if (params.force) query.set('force', 'true');
            return apiRequest<EPGSyncResult>(`/api/admin/epg/sync?${query.toString()}`, {
                method: 'POST',
            });
        },

        listChannels: (url?: string) => {
            const query = url ? `?url=${encodeURIComponent(url)}` : '';
            return apiRequest<{ channels: EPGChannel[]; total: number }>(`/api/admin/epg/channels${query}`);
        },

        listMappings: () =>
            apiRequest<{ mappings: ChannelEPGMapping[]; total: number; mapped_count: number }>('/api/admin/epg/mappings'),

        setMapping: (channelId: string, epgChannelId: string) =>
            apiRequest('/api/admin/epg/mappings', {
                method: 'POST',
                body: JSON.stringify({ channel_id: channelId, epg_channel_id: epgChannelId }),
            }),

        upload: async (file: File) => {
            const formData = new FormData();
            formData.append('file', file);
            // Direct fetch for multipart
            const url = `${API_BASE_URL}/api/admin/epg/upload`;
            const headers: HeadersInit = {};
            if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;

            const response = await fetch(url, {
                method: 'POST',
                headers,
                body: formData,
            });

            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.detail || `Upload failed: ${response.status}`);
            }
            return response.json();
        },
    },

    // Upload
    upload: {
        image: async (file: File): Promise<{ url: string }> => {
            const formData = new FormData();
            formData.append('file', file);

            const url = `${API_BASE_URL}/api/admin/upload-image`;
            const headers: HeadersInit = {};
            if (accessToken) {
                headers['Authorization'] = `Bearer ${accessToken}`;
            }

            const response = await fetch(url, {
                method: 'POST',
                headers,
                body: formData,
            });

            if (!response.ok) {
                throw new Error(`Upload failed: ${response.status}`);
            }

            return response.json();
        },
    },

    // User Management (Subscribers)
    users: {
        list: (params?: { search?: string; limit?: number; offset?: number }) => {
            const query = new URLSearchParams();
            if (params?.search) query.set('search', params.search);
            if (params?.limit) query.set('limit', params.limit.toString());
            if (params?.offset) query.set('offset', params.offset.toString());
            const queryString = query.toString();
            return apiRequest<SubscriberListResponse>(`/api/admin/users${queryString ? `?${queryString}` : ''}`);
        },

        createByMac: (data: { mac_address: string; display_name?: string; package_ids?: string[]; max_devices?: number }) =>
            apiRequest<SubscriberResponse>('/api/admin/users/mac', {
                method: 'POST',
                body: JSON.stringify(data),
            }),

        createGenerated: (data: { display_name?: string; package_ids?: string[]; max_devices?: number }) =>
            apiRequest<SubscriberCreateResponse>('/api/admin/users/generate', {
                method: 'POST',
                body: JSON.stringify(data),
            }),

        update: (id: string, data: Partial<SubscriberResponse>) =>
            apiRequest<SubscriberResponse>(`/api/admin/users/${id}`, {
                method: 'PUT',
                body: JSON.stringify(data),
            }),

        delete: (id: string) =>
            apiRequest(`/api/admin/users/${id}`, { method: 'DELETE' }),

        removeDevice: (userId: string, macAddress: string) =>
            apiRequest(`/api/admin/users/${userId}/devices/${macAddress}`, { method: 'DELETE' }),

        importMacUsers: async (file: File): Promise<{ imported: number; skipped: number; errors: string[] }> => {
            const formData = new FormData();
            formData.append('file', file);
            const url = `${API_BASE_URL}/api/admin/users/import-mac`;
            const headers: HeadersInit = {};
            if (accessToken) headers['Authorization'] = `Bearer ${accessToken}`;

            const response = await fetch(url, {
                method: 'POST',
                headers,
                body: formData,
            });

            if (!response.ok) {
                const error = await response.json().catch(() => ({}));
                throw new Error(error.detail || `Import failed: ${response.status}`);
            }
            return response.json();
        },
    },
};

export interface DeviceInfo {
    mac_address: string;
    device_name?: string;
    last_seen?: string;
    first_seen?: string;
}

export enum UserStatus {
    ACTIVE = "active",
    INACTIVE = "inactive",
    EXPIRED = "expired",
    BONUS = "bonus",
    TEST = "test"
}

export interface SubscriberResponse {
    id: string;
    username?: string;
    password?: string; // Visible for admins
    mac_address?: string;
    display_name?: string;
    surname?: string;
    building?: string;
    address?: string;
    client_no?: string;
    status: UserStatus | string;
    is_active: boolean; // Computed from status
    package_ids: string[];
    max_devices: number;
    devices: DeviceInfo[];
    created_at?: string;
    last_login?: string;
}

export interface SubscriberCreateResponse extends SubscriberResponse {
    password?: string; // Only returned once on creation
}

export interface SubscriberListResponse {
    items: SubscriberResponse[];
    total: number;
}

// User Groups Types
export interface UserGroup {
    id: string;
    name: string;
    description?: string;
    user_ids: string[];
    user_count: number;
    created_at?: string;
    updated_at?: string;
}

export interface UserGroupListResponse {
    items: UserGroup[];
    total: number;
}

// Messages Types
export type MessageTargetType = 'all' | 'groups' | 'users';

export interface Message {
    id: string;
    title: string;
    body: string;
    url?: string;
    target_type: MessageTargetType;
    target_ids: string[];
    is_active: boolean;
    created_at?: string;
    read_by: string[];
}

export interface MessageListResponse {
    items: Message[];
    total: number;
}

export interface MessageCreate {
    title: string;
    body: string;
    url?: string;
    target_type: MessageTargetType;
    target_ids: string[];
}

// Add to api object - User Groups
const userGroupsApi = {
    list: (params?: { search?: string }) => {
        const query = new URLSearchParams();
        if (params?.search) query.set('search', params.search);
        const queryString = query.toString();
        return apiRequest<UserGroupListResponse>(`/api/admin/user-groups${queryString ? `?${queryString}` : ''}`);
    },

    get: (id: string) => apiRequest<UserGroup>(`/api/admin/user-groups/${id}`),

    create: (data: { name: string; description?: string; user_ids: string[] }) =>
        apiRequest<UserGroup>('/api/admin/user-groups', {
            method: 'POST',
            body: JSON.stringify(data),
        }),

    update: (id: string, data: { name?: string; description?: string; user_ids?: string[] }) =>
        apiRequest<UserGroup>(`/api/admin/user-groups/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data),
        }),

    delete: (id: string) =>
        apiRequest(`/api/admin/user-groups/${id}`, { method: 'DELETE' }),

    getUsers: (id: string) =>
        apiRequest<SubscriberListResponse>(`/api/admin/user-groups/${id}/users`),
};

// Add to api object - Messages
const messagesApi = {
    list: (params?: { active_only?: boolean }) => {
        const query = new URLSearchParams();
        if (params?.active_only) query.set('active_only', 'true');
        const queryString = query.toString();
        return apiRequest<MessageListResponse>(`/api/admin/messages${queryString ? `?${queryString}` : ''}`);
    },

    get: (id: string) => apiRequest<Message>(`/api/admin/messages/${id}`),

    send: (data: MessageCreate) =>
        apiRequest<Message>('/api/admin/messages', {
            method: 'POST',
            body: JSON.stringify(data),
        }),

    delete: (id: string, hardDelete: boolean = false) =>
        apiRequest(`/api/admin/messages/${id}?hard_delete=${hardDelete}`, { method: 'DELETE' }),
};

// ---- Games ----

export interface Game {
    id: string;
    name: string;
    description?: string;
    imageUrl?: string;
    gameUrl: string;
    category?: string;
    isActive: boolean;
    order?: number;
}

export interface GamesListResponse {
    total: number;
    items: Game[];
    categories: string[];
}

export interface GameCreate {
    id: string;
    name: string;
    description?: string;
    image_url?: string;
    game_url: string;
    category?: string;
    is_active?: boolean;
    order?: number;
}

export interface GameUpdate {
    name?: string;
    description?: string;
    image_url?: string;
    game_url?: string;
    category?: string;
    is_active?: boolean;
    order?: number;
}

// Add to api object - Games
const gamesApi = {
    list: () => apiRequest<GamesListResponse>('/api/admin/games'),

    getCategories: () => apiRequest<string[]>('/api/admin/games/categories'),

    get: (id: string) => apiRequest<Game>(`/api/admin/games/${id}`),

    create: (data: GameCreate) =>
        apiRequest<Game>('/api/admin/games', {
            method: 'POST',
            body: JSON.stringify(data),
        }),

    update: (id: string, data: GameUpdate) =>
        apiRequest<Game>(`/api/admin/games/${id}`, {
            method: 'PUT',
            body: JSON.stringify(data),
        }),

    delete: (id: string) =>
        apiRequest(`/api/admin/games/${id}`, { method: 'DELETE' }),

    reorder: (items: { id: string; order: number }[]) =>
        apiRequest('/api/admin/games/reorder', {
            method: 'PUT',
            body: JSON.stringify({ items }),
        }),
};

// Extend the api object
Object.assign(api, {
    userGroups: userGroupsApi,
    messages: messagesApi,
    games: gamesApi,
});

export default api;

