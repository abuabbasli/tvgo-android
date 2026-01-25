export interface ScheduleItem {
    time: string;
    title: string;
    duration: string;
    isLive?: boolean;
}

export interface Channel {
    id: string;
    name: string;
    logo: string;
    category: string;
    streamUrl: string;
    description: string;
    logoColor: string;
    isFavorite?: boolean;
    schedule?: ScheduleItem[];
    order?: number;
}

export interface Movie {
    id: string;
    title: string;
    thumbnail: string;
    backdrop: string;
    category: string;
    year: number;
    rating: string;
    description: string;
    videoUrl: string;
    trailerUrl: string;
    genre: string[];
    runtime: number;
    directors: string[];
    cast: string[];
}

export interface Category {
    id: string;
    name: string;
}

// Backend Response DTOs
export interface ChannelDTO {
    id: string;
    name: string;
    logo?: string;
    category?: string;
    group?: string;
    streamUrl: string;
    description?: string;
    logoColor?: string;
}

export interface MovieDTO {
    id: string;
    title: string;
    images?: {
        poster?: string;
        landscape?: string;
        hero?: string;
    };
    genres?: string[];
    year?: number;
    rating?: number;
    synopsis?: string;
    media?: {
        streamUrl?: string;
        trailerUrl?: string;
    };
    runtimeMinutes?: number;
    credits?: {
        directors?: string[];
        cast?: string[];
    };
}
