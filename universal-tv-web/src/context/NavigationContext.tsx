import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';
import { TIZEN_KEYS } from '../utils/tizenKeys';

type FocusZone = 'sidebar' | 'content';

interface NavigationContextType {
    focusZone: FocusZone;
    setFocusZone: (zone: FocusZone) => void;
    isSidebarFocused: boolean;
    isContentFocused: boolean;
}

const NavigationContext = createContext<NavigationContextType | null>(null);

export function useNavigation() {
    const context = useContext(NavigationContext);
    if (!context) {
        throw new Error('useNavigation must be used within NavigationProvider');
    }
    return context;
}

interface NavigationProviderProps {
    children: React.ReactNode;
}

export function NavigationProvider({ children }: NavigationProviderProps) {
    const [focusZone, setFocusZone] = useState<FocusZone>('sidebar');

    // Global key handler for zone transitions
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            const keyCode = e.keyCode;

            // Left arrow on content -> move to sidebar
            if (
                (keyCode === TIZEN_KEYS.ARROW_LEFT || keyCode === 37) &&
                focusZone === 'content'
            ) {
                // Only switch if at leftmost position (pages handle this)
                // This is a fallback - pages should call setFocusZone directly
            }

            // Right arrow on sidebar -> move to content
            if (
                (keyCode === TIZEN_KEYS.ARROW_RIGHT || keyCode === 39) &&
                focusZone === 'sidebar'
            ) {
                e.preventDefault();
                setFocusZone('content');
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [focusZone]);

    const value: NavigationContextType = {
        focusZone,
        setFocusZone: useCallback((zone: FocusZone) => setFocusZone(zone), []),
        isSidebarFocused: focusZone === 'sidebar',
        isContentFocused: focusZone === 'content',
    };

    return (
        <NavigationContext.Provider value={value}>
            {children}
        </NavigationContext.Provider>
    );
}
