import { motion } from 'framer-motion';
import { Home, Tv, Film, Gamepad2, Mail } from 'lucide-react';

type ViewType = 'home' | 'channels' | 'movies' | 'games' | 'messages' | 'settings';

interface MobileBottomNavProps {
    activeView: ViewType;
    onViewChange: (view: ViewType) => void;
}

interface NavItemProps {
    icon: React.ElementType;
    label: string;
    isActive: boolean;
    onClick: () => void;
}

function NavItem({ icon: Icon, label, isActive, onClick }: NavItemProps) {
    return (
        <motion.button
            onClick={onClick}
            whileTap={{ scale: 0.9 }}
            style={{
                flex: 1,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: '4px',
                padding: '8px 0',
                background: 'transparent',
                border: 'none',
                cursor: 'pointer',
                position: 'relative',
            }}
        >
            {/* Active indicator */}
            {isActive && (
                <motion.div
                    layoutId="activeIndicator"
                    style={{
                        position: 'absolute',
                        top: '4px',
                        width: '56px',
                        height: '32px',
                        borderRadius: 'var(--md3-border-radius-pill)',
                        background: 'var(--md3-surface-active)',
                    }}
                    transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                />
            )}
            <Icon
                size={22}
                strokeWidth={2}
                style={{
                    position: 'relative',
                    zIndex: 1,
                    color: isActive ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                    transition: 'color 0.2s ease',
                }}
            />
            <span
                style={{
                    fontSize: 'var(--font-size-xs)',
                    fontWeight: isActive ? 'var(--font-weight-semibold)' : 'var(--font-weight-medium)',
                    color: isActive ? 'var(--md3-text-primary)' : 'var(--md3-text-secondary)',
                    transition: 'color 0.2s ease',
                }}
            >
                {label}
            </span>
        </motion.button>
    );
}

export default function MobileBottomNav({ activeView, onViewChange }: MobileBottomNavProps) {
    const navItems: { id: ViewType; icon: React.ElementType; label: string }[] = [
        { id: 'home', icon: Home, label: 'Home' },
        { id: 'channels', icon: Tv, label: 'Channels' },
        { id: 'movies', icon: Film, label: 'Movies' },
        { id: 'games', icon: Gamepad2, label: 'Games' },
        { id: 'messages', icon: Mail, label: 'Messages' },
    ];

    return (
        <motion.nav
            initial={{ y: 100, opacity: 0 }}
            animate={{ y: 0, opacity: 1 }}
            transition={{ duration: 0.3, ease: [0.4, 0, 0.2, 1] }}
            className="hide-desktop"
            style={{
                position: 'fixed',
                bottom: 0,
                left: 0,
                right: 0,
                height: '72px',
                display: 'flex',
                alignItems: 'stretch',
                background: 'var(--md3-bg-primary)',
                borderTop: '1px solid var(--md3-border-color)',
                zIndex: 100,
                paddingBottom: 'env(safe-area-inset-bottom, 0)',
            }}
        >
            {navItems.map((item) => (
                <NavItem
                    key={item.id}
                    icon={item.icon}
                    label={item.label}
                    isActive={activeView === item.id}
                    onClick={() => onViewChange(item.id)}
                />
            ))}
        </motion.nav>
    );
}
