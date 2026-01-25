import React, { createContext, useContext, useState, useEffect } from 'react';
import { fetchConfig, AppConfig, AppBrand, AppFeatures } from '../api';
import { Category } from '../types';

interface ConfigContextType {
    config: AppConfig | null;
    loading: boolean;
    // Convenience accessors
    brand: AppBrand | null;
    features: AppFeatures | null;
    // Categories derived from config
    channelCategories: Category[];
    movieCategories: Category[];
}

const ConfigContext = createContext<ConfigContextType | null>(null);

export function useConfig() {
    const context = useContext(ConfigContext);
    if (!context) {
        throw new Error('useConfig must be used within ConfigProvider');
    }
    return context;
}

interface ConfigProviderProps {
    children: React.ReactNode;
}

export function ConfigProvider({ children }: ConfigProviderProps) {
    const [config, setConfig] = useState<AppConfig | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        const loadConfig = async () => {
            setLoading(true);
            const data = await fetchConfig();
            setConfig(data);
            setLoading(false);
        };
        loadConfig();
    }, []);

    // Convert channelGroups strings to Category objects
    const channelCategories: Category[] = React.useMemo(() => {
        const defaultCategories: Category[] = [
            { id: 'all', name: 'All Channels' },
            { id: 'favorites', name: 'Favorites' },
        ];

        if (!config?.channelGroups || config.channelGroups.length === 0) {
            return defaultCategories;
        }

        // Filter out groups that match our default categories to avoid duplicates
        const reservedIds = new Set(['all', 'favorites', 'all-channels']);
        const configCategories = config.channelGroups
            .filter(group => !reservedIds.has(group.toLowerCase().replace(/\s+/g, '-')))
            .map(group => ({
                id: group.toLowerCase().replace(/\s+/g, '-'),
                name: group,
            }));

        return [...defaultCategories, ...configCategories];
    }, [config]);

    // Convert movieGenres strings to Category objects
    const movieCategories: Category[] = React.useMemo(() => {
        const defaultCategories: Category[] = [
            { id: 'all', name: 'All Movies' },
        ];

        if (!config?.movieGenres || config.movieGenres.length === 0) {
            return defaultCategories;
        }

        // Filter out genres that match our default categories to avoid duplicates
        const reservedIds = new Set(['all', 'all-movies']);
        const configCategories = config.movieGenres
            .filter(genre => !reservedIds.has(genre.toLowerCase().replace(/\s+/g, '-')))
            .map(genre => ({
                id: genre.toLowerCase().replace(/\s+/g, '-'),
                name: genre,
            }));

        return [...defaultCategories, ...configCategories];
    }, [config]);

    const value: ConfigContextType = {
        config,
        loading,
        brand: config?.brand ?? null,
        features: config?.features ?? null,
        channelCategories,
        movieCategories,
    };

    return (
        <ConfigContext.Provider value={value}>
            {children}
        </ConfigContext.Provider>
    );
}
