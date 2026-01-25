// Simple scroll utility - always use instant scroll for responsive TV navigation
// TV apps should feel instant and responsive, not wait for animations

export function scrollToElement(
    element: HTMLElement | null,
    options: Omit<ScrollIntoViewOptions, 'behavior'> = {}
): void {
    if (!element) return;

    // Always use auto (instant) for TV navigation - it's more responsive
    element.scrollIntoView({
        ...options,
        behavior: 'auto'
    });
}

// Legacy exports for compatibility
export function smartScrollIntoView(
    element: HTMLElement | null,
    options: ScrollIntoViewOptions = {}
): void {
    scrollToElement(element, options);
}

export function getSmartScrollBehavior(): ScrollBehavior {
    return 'auto';
}

export function resetScrollTimer(): void {
    // No-op for compatibility
}
