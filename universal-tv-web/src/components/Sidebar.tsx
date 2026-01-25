import { useState, useEffect, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import {
    Home,
    Tv,
    Film,
    Gamepad2,
    Mail,
    Settings,
    Grid3X3,
    List
} from 'lucide-react';
import { useConfig } from '../context/ConfigContext';
import { TIZEN_KEYS } from '../utils/tizenKeys';

interface SidebarProps {
    activeTab: string;
    onTabChange: (tab: string) => void;
    viewMode: 'grid' | 'list';
    onViewModeChange: (mode: 'grid' | 'list') => void;
    isCollapsed?: boolean;
    onToggleCollapse?: () => void;
    isFocused?: boolean;
}

interface NavItemProps {
    icon: React.ElementType;
    isActive: boolean;
    isFocused: boolean;
    onClick: () => void;
    index: number;
}

function NavItem({ icon: Icon, isActive, isFocused, onClick, index }: NavItemProps) {
    return (
        <motion.button
            onClick={onClick}
            initial={{ opacity: 0, x: -20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{
                delay: index * 0.03,
                duration: 0.3,
                ease: [0.2, 0, 0, 1]
            }}
            className="tv-focusable"
            style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                width: '56px',
                height: '56px',
                borderRadius: '12px',
                background: isActive ? 'var(--md3-surface-active)' : isFocused ? 'rgba(255, 255, 255, 0.1)' : 'transparent',
                border: 'none',
                cursor: 'pointer',
                transition: 'background 0.2s cubic-bezier(0.2, 0, 0, 1)',
                outline: isFocused ? '2px solid var(--md3-surface-active)' : 'none',
                outlineOffset: '2px',
            }}
        >
            <Icon
                size={28}
                strokeWidth={2}
                style={{
                    color: isActive || isFocused ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                }}
            />
        </motion.button>
    );
}

export default function Sidebar({
    activeTab,
    onTabChange,
    viewMode,
    onViewModeChange,
    isFocused = false
}: SidebarProps) {
    const { config } = useConfig();
    const [focusedIndex, setFocusedIndex] = useState(0); // Start on Home (index 0)
    const [focusSection, setFocusSection] = useState<'nav' | 'viewButtons'>('nav'); // Which section is focused
    const [focusedViewButtonIndex, setFocusedViewButtonIndex] = useState(0); // 0 = grid, 1 = list

    const navItems = [
        { id: 'home', icon: Home, label: 'Home' },
        { id: 'channels', icon: Tv, label: 'Channels' },
        { id: 'movies', icon: Film, label: 'Movies' },
        { id: 'games', icon: Gamepad2, label: 'Games' },
        { id: 'messages', icon: Mail, label: 'Messages' },
        { id: 'settings', icon: Settings, label: 'Settings' },
    ];

    // Keyboard navigation
    const handleKeyDown = useCallback((e: KeyboardEvent) => {
        if (!isFocused) return;

        const keyCode = e.keyCode;
        const showViewButtons = activeTab === 'channels';

        // Navigation for nav items section
        if (focusSection === 'nav') {
            switch (keyCode) {
                // Up Arrow
                case TIZEN_KEYS.ARROW_UP:
                case 38:
                    e.preventDefault();
                    e.stopPropagation();
                    setFocusedIndex(prev => Math.max(0, prev - 1));
                    break;

                // Down Arrow
                case TIZEN_KEYS.ARROW_DOWN:
                case 40:
                    e.preventDefault();
                    e.stopPropagation();
                    if (focusedIndex < navItems.length - 1) {
                        setFocusedIndex(prev => prev + 1);
                    } else if (showViewButtons) {
                        // Move to view buttons section
                        setFocusSection('viewButtons');
                        setFocusedViewButtonIndex(0);
                    }
                    break;

                // Enter - Select
                case TIZEN_KEYS.ENTER:
                case 13:
                    e.preventDefault();
                    e.stopPropagation();
                    const item = navItems[focusedIndex];
                    onTabChange(item.id);
                    break;
            }
        }
        // Navigation for view toggle buttons section
        else if (focusSection === 'viewButtons') {
            switch (keyCode) {
                // Up Arrow - go back to nav or switch between view buttons
                case TIZEN_KEYS.ARROW_UP:
                case 38:
                    e.preventDefault();
                    e.stopPropagation();
                    if (focusedViewButtonIndex > 0) {
                        setFocusedViewButtonIndex(prev => prev - 1);
                    } else {
                        // Go back to nav items
                        setFocusSection('nav');
                        setFocusedIndex(navItems.length - 1);
                    }
                    break;

                // Down Arrow
                case TIZEN_KEYS.ARROW_DOWN:
                case 40:
                    e.preventDefault();
                    e.stopPropagation();
                    if (focusedViewButtonIndex < 1) {
                        setFocusedViewButtonIndex(prev => prev + 1);
                    }
                    break;

                // Enter - Select view mode
                case TIZEN_KEYS.ENTER:
                case 13:
                    e.preventDefault();
                    e.stopPropagation();
                    onViewModeChange(focusedViewButtonIndex === 0 ? 'grid' : 'list');
                    break;
            }
        }
    }, [isFocused, focusedIndex, focusSection, focusedViewButtonIndex, navItems, onTabChange, activeTab, onViewModeChange]);

    useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);

    // Track if we've synced on this focus session
    const hasSyncedRef = useRef(false);

    // Sync focused index with active tab ONLY when sidebar first gains focus
    useEffect(() => {
        if (isFocused && !hasSyncedRef.current) {
            const activeIndex = navItems.findIndex(item => item.id === activeTab);
            if (activeIndex !== -1) {
                setFocusedIndex(activeIndex);
                setFocusSection('nav'); // Reset to nav section when gaining focus
            }
            hasSyncedRef.current = true;
        } else if (!isFocused) {
            // Reset the ref when we lose focus
            hasSyncedRef.current = false;
        }
    }, [isFocused, activeTab, navItems]);

    return (
        <motion.div
            initial={{ x: -280, opacity: 0 }}
            animate={{
                x: 0,
                opacity: 1,
                width: 72
            }}
            transition={{
                duration: 0.3,
                ease: [0.4, 0, 0.2, 1]
            }}
            className="hide-mobile"
            style={{
                position: 'fixed',
                left: 0,
                top: 0,
                height: '100vh',
                display: 'flex',
                flexDirection: 'column',
                padding: '0 8px',
                background: 'var(--md3-bg-primary)',
                zIndex: 50,
                borderRight: 'none',
            }}
        >
            {/* Logo */}
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                paddingTop: '28px',
                paddingBottom: '20px',
            }}>
                <motion.img
                    src="/tvgo-logo.png"
                    alt="TV Go"
                    initial={{ opacity: 0, scale: 0.9 }}
                    animate={{
                        opacity: 1,
                        scale: 1,
                    }}
                    transition={{ duration: 0.3, ease: [0.2, 0, 0, 1] }}
                    style={{
                        objectFit: 'contain',
                        height: '40px',
                        maxWidth: '64px',
                    }}
                />
            </div>

            {/* Nav Items */}
            <div style={{
                display: 'flex',
                flexDirection: 'column',
                gap: '16px',
                paddingTop: '20px',
            }}>
                {navItems.map((item, index) => (
                    <NavItem
                        key={item.id}
                        icon={item.icon}
                        isActive={activeTab === item.id}
                        isFocused={isFocused && focusSection === 'nav' && focusedIndex === index}
                        onClick={() => onTabChange(item.id)}
                        index={index}
                    />
                ))}
            </div>

            {/* View Toggle at Bottom - Only show for channels tab */}
            {activeTab === 'channels' && (
                <div style={{
                    marginTop: 'auto',
                    marginBottom: '24px',
                    display: 'flex',
                    justifyContent: 'center',
                }}>
                    <div style={{
                        display: 'inline-flex',
                        flexDirection: 'column',
                        gap: '8px',
                        padding: '6px',
                        borderRadius: 'var(--md3-border-radius-md)',
                        background: 'var(--md3-bg-secondary)',
                    }}>
                        <button
                            onClick={() => onViewModeChange('grid')}
                            style={{
                                padding: '12px',
                                borderRadius: '10px',
                                background: viewMode === 'grid' ? 'var(--md3-surface-active)' : 'transparent',
                                color: viewMode === 'grid' ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                                border: 'none',
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                transition: 'all var(--transition-fast)',
                                outline: (isFocused && focusSection === 'viewButtons' && focusedViewButtonIndex === 0)
                                    ? '2px solid var(--md3-surface-active)' : 'none',
                                outlineOffset: '2px',
                            }}
                        >
                            <Grid3X3 size={24} />
                        </button>
                        <button
                            onClick={() => onViewModeChange('list')}
                            style={{
                                padding: '12px',
                                borderRadius: '10px',
                                background: viewMode === 'list' ? 'var(--md3-surface-active)' : 'transparent',
                                color: viewMode === 'list' ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                                border: 'none',
                                cursor: 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                transition: 'all var(--transition-fast)',
                                outline: (isFocused && focusSection === 'viewButtons' && focusedViewButtonIndex === 1)
                                    ? '2px solid var(--md3-surface-active)' : 'none',
                                outlineOffset: '2px',
                            }}
                        >
                            <List size={24} />
                        </button>
                    </div>
                </div>
            )}
        </motion.div>
    );
}
