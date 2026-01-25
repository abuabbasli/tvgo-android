/**
 * Focus Manager for TV Navigation
 * Handles D-pad navigation and focus management for Samsung Tizen TV
 */

import { TIZEN_KEYS } from './tizenKeys';

interface FocusableElement {
    element: HTMLElement;
    id: string;
    row: number;
    col: number;
}

class FocusManager {
    private focusableElements: Map<string, FocusableElement> = new Map();
    private currentFocusId: string | null = null;
    private isEnabled: boolean = true;

    constructor() {
        if (typeof window !== 'undefined') {
            window.addEventListener('keydown', this.handleKeyDown.bind(this));
        }
    }

    /**
     * Register a focusable element
     */
    register(element: HTMLElement, id: string, row: number = 0, col: number = 0): void {
        this.focusableElements.set(id, { element, id, row, col });

        // Add focus styles
        element.setAttribute('tabindex', '0');
        element.classList.add('tv-focusable');

        // Set focus handlers
        element.addEventListener('focus', () => {
            this.currentFocusId = id;
            element.classList.add('tv-focused');
        });

        element.addEventListener('blur', () => {
            element.classList.remove('tv-focused');
        });
    }

    /**
     * Unregister a focusable element
     */
    unregister(id: string): void {
        const item = this.focusableElements.get(id);
        if (item) {
            item.element.classList.remove('tv-focusable', 'tv-focused');
            item.element.removeAttribute('tabindex');
        }
        this.focusableElements.delete(id);
    }

    /**
     * Focus a specific element by ID
     */
    focus(id: string): boolean {
        const item = this.focusableElements.get(id);
        if (item) {
            item.element.focus();
            this.currentFocusId = id;
            return true;
        }
        return false;
    }

    /**
     * Get the currently focused element ID
     */
    getCurrentFocusId(): string | null {
        return this.currentFocusId;
    }

    /**
     * Enable or disable focus management
     */
    setEnabled(enabled: boolean): void {
        this.isEnabled = enabled;
    }

    /**
     * Handle keyboard navigation
     */
    private handleKeyDown(event: KeyboardEvent): void {
        if (!this.isEnabled) return;

        const keyCode = event.keyCode;

        switch (keyCode) {
            case TIZEN_KEYS.ARROW_UP:
                this.moveFocus('up');
                event.preventDefault();
                break;
            case TIZEN_KEYS.ARROW_DOWN:
                this.moveFocus('down');
                event.preventDefault();
                break;
            case TIZEN_KEYS.ARROW_LEFT:
                this.moveFocus('left');
                event.preventDefault();
                break;
            case TIZEN_KEYS.ARROW_RIGHT:
                this.moveFocus('right');
                event.preventDefault();
                break;
            case TIZEN_KEYS.ENTER:
                this.activateCurrent();
                break;
        }
    }

    /**
     * Move focus in a direction
     */
    private moveFocus(direction: 'up' | 'down' | 'left' | 'right'): void {
        if (!this.currentFocusId) {
            // Focus first available element
            const first = Array.from(this.focusableElements.values())[0];
            if (first) {
                this.focus(first.id);
            }
            return;
        }

        const current = this.focusableElements.get(this.currentFocusId);
        if (!current) return;

        const candidates = Array.from(this.focusableElements.values());
        let best: FocusableElement | null = null;
        let bestDistance = Infinity;

        candidates.forEach(candidate => {
            if (candidate.id === this.currentFocusId) return;

            const isValidDirection = this.isInDirection(current, candidate, direction);
            if (!isValidDirection) return;

            const distance = this.calculateDistance(current, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        });

        if (best) {
            this.focus((best as FocusableElement).id);
        }
    }

    /**
     * Check if candidate is in the specified direction from current
     */
    private isInDirection(
        current: FocusableElement,
        candidate: FocusableElement,
        direction: 'up' | 'down' | 'left' | 'right'
    ): boolean {
        const currentRect = current.element.getBoundingClientRect();
        const candidateRect = candidate.element.getBoundingClientRect();

        switch (direction) {
            case 'up':
                return candidateRect.bottom <= currentRect.top;
            case 'down':
                return candidateRect.top >= currentRect.bottom;
            case 'left':
                return candidateRect.right <= currentRect.left;
            case 'right':
                return candidateRect.left >= currentRect.right;
        }
    }

    /**
     * Calculate distance between two elements
     */
    private calculateDistance(a: FocusableElement, b: FocusableElement): number {
        const aRect = a.element.getBoundingClientRect();
        const bRect = b.element.getBoundingClientRect();

        const aCenterX = aRect.left + aRect.width / 2;
        const aCenterY = aRect.top + aRect.height / 2;
        const bCenterX = bRect.left + bRect.width / 2;
        const bCenterY = bRect.top + bRect.height / 2;

        return Math.sqrt(
            Math.pow(bCenterX - aCenterX, 2) + Math.pow(bCenterY - aCenterY, 2)
        );
    }

    /**
     * Activate (click) the currently focused element
     */
    private activateCurrent(): void {
        if (!this.currentFocusId) return;

        const current = this.focusableElements.get(this.currentFocusId);
        if (current) {
            current.element.click();
        }
    }

    /**
     * Clear all registered elements
     */
    clear(): void {
        this.focusableElements.forEach((_, id) => this.unregister(id));
        this.focusableElements.clear();
        this.currentFocusId = null;
    }
}

// Export singleton instance
export const focusManager = new FocusManager();

// React hook for focus management
export function useTVFocus(
    ref: React.RefObject<HTMLElement>,
    id: string,
    options?: { row?: number; col?: number; autoFocus?: boolean }
): void {
    if (typeof window === 'undefined') return;

    const { row = 0, col = 0, autoFocus = false } = options || {};

    // Register on mount, unregister on unmount
    if (ref.current) {
        focusManager.register(ref.current, id, row, col);

        if (autoFocus) {
            focusManager.focus(id);
        }
    }
}
