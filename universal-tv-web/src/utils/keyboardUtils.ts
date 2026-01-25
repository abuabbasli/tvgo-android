// Keyboard navigation utilities

// Throttle function - prevents function from being called more than once per delay period
export function throttle<T extends (...args: any[]) => void>(
    func: T,
    delay: number
): (...args: Parameters<T>) => void {
    let lastCall = 0;
    let timeoutId: ReturnType<typeof setTimeout> | null = null;

    return (...args: Parameters<T>) => {
        const now = Date.now();
        const timeSinceLastCall = now - lastCall;

        if (timeSinceLastCall >= delay) {
            lastCall = now;
            func(...args);
        } else if (!timeoutId) {
            // Schedule the call for when the delay period ends
            timeoutId = setTimeout(() => {
                lastCall = Date.now();
                timeoutId = null;
                func(...args);
            }, delay - timeSinceLastCall);
        }
    };
}

// Debounce function - delays execution until after wait period of inactivity
export function debounce<T extends (...args: any[]) => void>(
    func: T,
    wait: number
): (...args: Parameters<T>) => void {
    let timeoutId: ReturnType<typeof setTimeout> | null = null;

    return (...args: Parameters<T>) => {
        if (timeoutId) {
            clearTimeout(timeoutId);
        }
        timeoutId = setTimeout(() => {
            func(...args);
            timeoutId = null;
        }, wait);
    };
}

// Key repeat rate limiter - allows first press immediately, then throttles repeats
export function createKeyRepeatLimiter(initialDelay: number = 200, repeatDelay: number = 100) {
    const keyStates = new Map<number, { lastTime: number; isRepeating: boolean }>();

    return (keyCode: number): boolean => {
        const now = Date.now();
        const state = keyStates.get(keyCode);

        if (!state) {
            // First press - allow immediately
            keyStates.set(keyCode, { lastTime: now, isRepeating: false });
            return true;
        }

        const elapsed = now - state.lastTime;
        const delay = state.isRepeating ? repeatDelay : initialDelay;

        if (elapsed >= delay) {
            keyStates.set(keyCode, { lastTime: now, isRepeating: true });
            return true;
        }

        return false;
    };
}

// Reset key state on key up
export function createKeyUpHandler(keyStates: Map<number, any>) {
    return (keyCode: number) => {
        keyStates.delete(keyCode);
    };
}
