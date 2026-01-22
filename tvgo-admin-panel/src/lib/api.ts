// API Configuration and Utilities

const API_BASE_URL = import.meta.env.VITE_API_URL || 'https://hsbcasafqma6eflzbulquhxflu0stbuw.lambda-url.eu-central-1.on.aws/api';

// Token storage
const ACCESS_TOKEN_KEY = 'admin_access_token';
const REFRESH_TOKEN_KEY = 'admin_refresh_token';

export function getAccessToken(): string | null {
    return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function setTokens(accessToken: string, refreshToken: string): void {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearTokens(): void {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
}

// Fetch wrapper with auth
async function fetchWithAuth<T>(
    endpoint: string,
    options: RequestInit = {}
): Promise<T> {
    const token = getAccessToken();
    const headers: HeadersInit = {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
    };

    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
        ...options,
        headers,
    });

    if (!response.ok) {
        const error = await response.json().catch(() => ({ message: 'Request failed' }));
        throw new Error(error.detail || error.message || 'Request failed');
    }

    return response.json();
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

export interface CompanyServices {
    enable_vod: boolean;
    enable_channels: boolean;
    enable_games: boolean;
    enable_messaging: boolean;
}

export interface Company {
    id: string;
    name: string;
    slug: string;
    username: string;
    is_active: boolean;
    services: CompanyServices;
    created_at: string;
    user_count: number;
    channel_count: number;
    movie_count: number;
}

export interface CompanyCreate {
    name: string;
    slug: string;
    username: string;
    password: string;
    services: CompanyServices;
}

export interface CompanyUpdate {
    name?: string;
    is_active?: boolean;
    services?: CompanyServices;
}

export interface CompanyListResponse {
    items: Company[];
    total: number;
}

export interface SuperAdminStats {
    total_companies: number;
    active_companies: number;
    total_users: number;
    total_channels: number;
    total_movies: number;
}

// API Methods
const api = {
    auth: {
        login: async (username: string, password: string): Promise<LoginResponse> => {
            const response = await fetchWithAuth<LoginResponse>('/auth/login', {
                method: 'POST',
                body: JSON.stringify({ username, password }),
            });
            setTokens(response.accessToken, response.refreshToken);
            return response;
        },

        me: async (): Promise<User> => {
            return fetchWithAuth<User>('/auth/me');
        },

        logout: (): void => {
            clearTokens();
        },
    },

    superAdmin: {
        getStats: async (): Promise<SuperAdminStats> => {
            return fetchWithAuth<SuperAdminStats>('/super-admin/stats');
        },

        listCompanies: async (skip = 0, limit = 50, search?: string): Promise<CompanyListResponse> => {
            const params = new URLSearchParams({ skip: String(skip), limit: String(limit) });
            if (search) params.set('search', search);
            return fetchWithAuth<CompanyListResponse>(`/super-admin/companies?${params}`);
        },

        getCompany: async (id: string): Promise<Company> => {
            return fetchWithAuth<Company>(`/super-admin/companies/${id}`);
        },

        createCompany: async (data: CompanyCreate): Promise<Company> => {
            return fetchWithAuth<Company>('/super-admin/companies', {
                method: 'POST',
                body: JSON.stringify(data),
            });
        },

        updateCompany: async (id: string, data: CompanyUpdate): Promise<Company> => {
            return fetchWithAuth<Company>(`/super-admin/companies/${id}`, {
                method: 'PUT',
                body: JSON.stringify(data),
            });
        },

        deleteCompany: async (id: string): Promise<void> => {
            await fetchWithAuth(`/super-admin/companies/${id}`, {
                method: 'DELETE',
            });
        },

        resetPassword: async (id: string, newPassword: string): Promise<Company> => {
            return fetchWithAuth<Company>(`/super-admin/companies/${id}/reset-password?new_password=${encodeURIComponent(newPassword)}`, {
                method: 'POST',
            });
        },
    },
};

export default api;
