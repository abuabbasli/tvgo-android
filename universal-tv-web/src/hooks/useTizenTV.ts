/**
 * React hooks for Tizen TV functionality
 */

import { useEffect, useCallback, useRef } from 'react';
import {
    registerTizenKeys,
    unregisterTizenKeys,
    TIZEN_KEYS,
    isTizenTV,
    exitTizenApp,
    isBackKey
} from '../utils/tizenKeys';
import { focusManager } from '../utils/focusManager';

/**
 * Initialize Tizen TV support
 * Should be called once at the app root level
 */
export function useTizenInit(): void {
    useEffect(() => {
        registerTizenKeys();

        return () => {
            unregisterTizenKeys();
        };
    }, []);
}

/**
 * Handle back button navigation
 * @param onBack - Callback when back is pressed
 * @param canGoBack - Whether we can go back (if false, exits app on Tizen)
 */
export function useTizenBack(onBack: () => void, canGoBack: boolean = true): void {
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (isBackKey(event.keyCode)) {
                event.preventDefault();
                event.stopPropagation();

                if (canGoBack) {
                    onBack();
                } else if (isTizenTV()) {
                    // On Tizen, exit the app when at root level
                    exitTizenApp();
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown, true);
        return () => window.removeEventListener('keydown', handleKeyDown, true);
    }, [onBack, canGoBack]);
}

/**
 * Handle media control keys (play, pause, etc.)
 */
interface MediaKeyHandlers {
    onPlay?: () => void;
    onPause?: () => void;
    onPlayPause?: () => void;
    onStop?: () => void;
    onRewind?: () => void;
    onFastForward?: () => void;
}

export function useTizenMediaKeys(handlers: MediaKeyHandlers): void {
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            switch (event.keyCode) {
                case TIZEN_KEYS.PLAY:
                    handlers.onPlay?.();
                    break;
                case TIZEN_KEYS.PAUSE:
                    handlers.onPause?.();
                    break;
                case TIZEN_KEYS.PLAY_PAUSE:
                    handlers.onPlayPause?.();
                    break;
                case TIZEN_KEYS.STOP:
                    handlers.onStop?.();
                    break;
                case TIZEN_KEYS.REWIND:
                    handlers.onRewind?.();
                    break;
                case TIZEN_KEYS.FAST_FORWARD:
                    handlers.onFastForward?.();
                    break;
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handlers]);
}

/**
 * Handle channel up/down keys
 */
export function useTizenChannelKeys(
    onChannelUp: () => void,
    onChannelDown: () => void
): void {
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            if (event.keyCode === TIZEN_KEYS.CHANNEL_UP) {
                event.preventDefault();
                onChannelUp();
            } else if (event.keyCode === TIZEN_KEYS.CHANNEL_DOWN) {
                event.preventDefault();
                onChannelDown();
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [onChannelUp, onChannelDown]);
}

/**
 * Handle color button keys (Red, Green, Yellow, Blue)
 */
interface ColorKeyHandlers {
    onRed?: () => void;
    onGreen?: () => void;
    onYellow?: () => void;
    onBlue?: () => void;
}

export function useTizenColorKeys(handlers: ColorKeyHandlers): void {
    useEffect(() => {
        const handleKeyDown = (event: KeyboardEvent) => {
            switch (event.keyCode) {
                case TIZEN_KEYS.RED:
                    handlers.onRed?.();
                    break;
                case TIZEN_KEYS.GREEN:
                    handlers.onGreen?.();
                    break;
                case TIZEN_KEYS.YELLOW:
                    handlers.onYellow?.();
                    break;
                case TIZEN_KEYS.BLUE:
                    handlers.onBlue?.();
                    break;
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handlers]);
}

/**
 * Register a focusable element for TV navigation
 */
export function useTVFocusable(
    id: string,
    options?: { row?: number; col?: number; autoFocus?: boolean }
): React.RefCallback<HTMLElement> {
    const elementRef = useRef<HTMLElement | null>(null);
    const { row = 0, col = 0, autoFocus = false } = options || {};

    const setRef = useCallback((element: HTMLElement | null) => {
        // Unregister previous element
        if (elementRef.current) {
            focusManager.unregister(id);
        }

        elementRef.current = element;

        // Register new element
        if (element) {
            focusManager.register(element, id, row, col);
            if (autoFocus) {
                setTimeout(() => focusManager.focus(id), 0);
            }
        }
    }, [id, row, col, autoFocus]);

    // Cleanup on unmount
    useEffect(() => {
        return () => {
            if (elementRef.current) {
                focusManager.unregister(id);
            }
        };
    }, [id]);

    return setRef;
}

/**
 * Programmatically focus an element
 */
export function useTVFocusControl(): {
    focus: (id: string) => boolean;
    getCurrentFocusId: () => string | null;
} {
    return {
        focus: (id: string) => focusManager.focus(id),
        getCurrentFocusId: () => focusManager.getCurrentFocusId(),
    };
}

/**
 * Check if running on Tizen TV
 */
export function useIsTizenTV(): boolean {
    return isTizenTV();
}
