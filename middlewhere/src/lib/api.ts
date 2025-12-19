/**
 * API Client for tvgo-backend
 * Handles all communication with the backend API
 */

const API_BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8000';

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

export default api;

