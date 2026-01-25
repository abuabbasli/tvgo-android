/**
 * Samsung Tizen TV Remote Control Key Codes
 * These are the standard key codes for Samsung Smart TV remotes
 */
export const TIZEN_KEYS = {
    // Navigation
    ARROW_UP: 38,
    ARROW_DOWN: 40,
    ARROW_LEFT: 37,
    ARROW_RIGHT: 39,
    ENTER: 13,

    // Back/Exit
    BACK: 10009,
    EXIT: 10182,

    // Media Controls
    PLAY: 415,
    PAUSE: 19,
    PLAY_PAUSE: 10252,
    STOP: 413,
    REWIND: 412,
    FAST_FORWARD: 417,

    // Channel Controls
    CHANNEL_UP: 427,
    CHANNEL_DOWN: 428,

    // Color Buttons
    RED: 403,
    GREEN: 404,
    YELLOW: 405,
    BLUE: 406,

    // Number Keys
    NUM_0: 48,
    NUM_1: 49,
    NUM_2: 50,
    NUM_3: 51,
    NUM_4: 52,
    NUM_5: 53,
    NUM_6: 54,
    NUM_7: 55,
    NUM_8: 56,
    NUM_9: 57,

    // Volume (usually handled by TV, but can be intercepted)
    VOLUME_UP: 447,
    VOLUME_DOWN: 448,
    MUTE: 449,

    // Info
    INFO: 457,
    GUIDE: 458,
    MENU: 18,
} as const;

// Keys that need to be registered with Tizen API
const REGISTERABLE_KEYS = [
    'MediaPlayPause',
    'MediaPlay',
    'MediaPause',
    'MediaStop',
    'MediaRewind',
    'MediaFastForward',
    'ColorF0Red',
    'ColorF1Green',
    'ColorF2Yellow',
    'ColorF3Blue',
    'ChannelUp',
    'ChannelDown',
    'Info',
    'Guide',
];

/**
 * Register Tizen TV remote control keys
 * Must be called on app initialization
 */
export function registerTizenKeys(): void {
    if (typeof window === 'undefined') return;

    const tizen = (window as any).tizen;
    if (!tizen?.tvinputdevice) {
        console.log('Not running on Tizen TV');
        return;
    }

    try {
        REGISTERABLE_KEYS.forEach(key => {
            try {
                tizen.tvinputdevice.registerKey(key);
            } catch (e) {
                console.warn(`Failed to register key: ${key}`);
            }
        });
        console.log('Tizen remote keys registered successfully');
    } catch (error) {
        console.error('Error registering Tizen keys:', error);
    }
}

/**
 * Unregister Tizen TV remote control keys
 * Should be called on app cleanup
 */
export function unregisterTizenKeys(): void {
    if (typeof window === 'undefined') return;

    const tizen = (window as any).tizen;
    if (!tizen?.tvinputdevice) return;

    try {
        REGISTERABLE_KEYS.forEach(key => {
            try {
                tizen.tvinputdevice.unregisterKey(key);
            } catch (e) {
                // Ignore errors during cleanup
            }
        });
    } catch (error) {
        console.error('Error unregistering Tizen keys:', error);
    }
}

/**
 * Check if the key is a back/exit key
 */
export function isBackKey(keyCode: number): boolean {
    return keyCode === TIZEN_KEYS.BACK || keyCode === TIZEN_KEYS.EXIT;
}

/**
 * Check if the key is a navigation key
 */
export function isNavigationKey(keyCode: number): boolean {
    return ([
        TIZEN_KEYS.ARROW_UP,
        TIZEN_KEYS.ARROW_DOWN,
        TIZEN_KEYS.ARROW_LEFT,
        TIZEN_KEYS.ARROW_RIGHT,
    ] as number[]).includes(keyCode);
}

/**
 * Check if the key is a media control key
 */
export function isMediaKey(keyCode: number): boolean {
    return ([
        TIZEN_KEYS.PLAY,
        TIZEN_KEYS.PAUSE,
        TIZEN_KEYS.PLAY_PAUSE,
        TIZEN_KEYS.STOP,
        TIZEN_KEYS.REWIND,
        TIZEN_KEYS.FAST_FORWARD,
    ] as number[]).includes(keyCode);
}

/**
 * Check if running on Tizen TV
 */
export function isTizenTV(): boolean {
    if (typeof window === 'undefined') return false;
    return !!(window as any).tizen;
}

/**
 * Exit the Tizen application
 */
export function exitTizenApp(): void {
    if (typeof window === 'undefined') return;

    const tizen = (window as any).tizen;
    if (tizen?.application) {
        try {
            tizen.application.getCurrentApplication().exit();
        } catch (error) {
            console.error('Error exiting Tizen app:', error);
        }
    }
}
