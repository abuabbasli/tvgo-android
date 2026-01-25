import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { Settings, Monitor, Volume2, Wifi, Globe, Moon, Bell, Shield, Info, ChevronRight } from 'lucide-react';
import { useNavigation } from '../context/NavigationContext';
import { TIZEN_KEYS } from '../utils/tizenKeys';

interface SettingsPageProps {
    isFocused?: boolean;
}

interface SettingItem {
    id: string;
    icon: React.ElementType;
    title: string;
    description: string;
    type: 'toggle' | 'select' | 'action';
    value?: boolean | string;
    options?: string[];
}

const settingsData: SettingItem[] = [
    { id: 'display', icon: Monitor, title: 'Display', description: 'Resolution, brightness & screen settings', type: 'action' },
    { id: 'audio', icon: Volume2, title: 'Audio', description: 'Volume, output device & sound settings', type: 'action' },
    { id: 'network', icon: Wifi, title: 'Network', description: 'WiFi, ethernet & connection settings', type: 'action' },
    { id: 'language', icon: Globe, title: 'Language', description: 'English', type: 'select', options: ['English', 'Azərbaycan', 'Русский', 'Türkçe'] },
    { id: 'darkMode', icon: Moon, title: 'Dark Mode', description: 'Use dark theme', type: 'toggle', value: true },
    { id: 'notifications', icon: Bell, title: 'Notifications', description: 'Enable notifications', type: 'toggle', value: true },
    { id: 'privacy', icon: Shield, title: 'Privacy', description: 'Data collection & privacy settings', type: 'action' },
    { id: 'about', icon: Info, title: 'About', description: 'Version 1.0.0 • TV Go Universal', type: 'action' },
];

export default function SettingsPage({ isFocused = false }: SettingsPageProps) {
    const [focusedIndex, setFocusedIndex] = useState(0);
    const [settings, setSettings] = useState(settingsData);
    const { setFocusZone } = useNavigation();

    // Keyboard navigation
    const handleKeyDown = useCallback((e: KeyboardEvent) => {
        if (!isFocused) return;

        const keyCode = e.keyCode;

        switch (keyCode) {
            case TIZEN_KEYS.ARROW_UP:
            case 38:
                e.preventDefault();
                setFocusedIndex(prev => Math.max(0, prev - 1));
                break;

            case TIZEN_KEYS.ARROW_DOWN:
            case 40:
                e.preventDefault();
                setFocusedIndex(prev => Math.min(settings.length - 1, prev + 1));
                break;

            case TIZEN_KEYS.ARROW_LEFT:
            case 37:
                e.preventDefault();
                setFocusZone('sidebar');
                break;

            case TIZEN_KEYS.ENTER:
            case 13:
                e.preventDefault();
                const setting = settings[focusedIndex];
                if (setting.type === 'toggle') {
                    setSettings(prev => prev.map((s, i) =>
                        i === focusedIndex ? { ...s, value: !s.value } : s
                    ));
                }
                break;
        }
    }, [isFocused, focusedIndex, settings.length, setFocusZone]);

    useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);

    return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
            style={{
                padding: '64px 48px',
                height: '100%',
                overflow: 'auto',
            }}
        >
            {/* Header */}
            <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                marginBottom: '32px'
            }}>
                <Settings size={32} color="var(--md3-surface-active)" />
                <h1 style={{
                    fontSize: '28px',
                    fontWeight: 600,
                    margin: 0,
                }}>
                    Settings
                </h1>
            </div>

            {/* Settings List */}
            <div style={{
                maxWidth: '600px',
                display: 'flex',
                flexDirection: 'column',
                gap: '8px',
            }}>
                {settings.map((setting, index) => {
                    const Icon = setting.icon;
                    const isFocusedItem = isFocused && focusedIndex === index;

                    return (
                        <motion.div
                            key={setting.id}
                            initial={{ opacity: 0, x: -10 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: index * 0.03 }}
                            onClick={() => {
                                setFocusedIndex(index);
                                if (setting.type === 'toggle') {
                                    setSettings(prev => prev.map((s, i) =>
                                        i === index ? { ...s, value: !s.value } : s
                                    ));
                                }
                            }}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '16px',
                                padding: '16px 20px',
                                borderRadius: 'var(--md3-border-radius)',
                                background: isFocusedItem ? 'rgba(255, 255, 255, 0.1)' : 'var(--md3-bg-secondary)',
                                border: isFocusedItem
                                    ? '2px solid #FFFFFF'
                                    : '1px solid var(--md3-border-color)',
                                cursor: 'pointer',
                                transition: 'all 0.2s ease',
                                transform: isFocusedItem ? 'scale(1.02)' : 'scale(1)',
                            }}
                        >
                            {/* Icon */}
                            <div style={{
                                width: '40px',
                                height: '40px',
                                borderRadius: '10px',
                                background: 'var(--md3-bg-primary)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                flexShrink: 0,
                            }}>
                                <Icon size={20} color={isFocusedItem ? 'var(--md3-surface-active)' : 'var(--md3-text-secondary)'} />
                            </div>

                            {/* Text */}
                            <div style={{ flex: 1 }}>
                                <h3 style={{
                                    fontSize: '16px',
                                    fontWeight: 500,
                                    color: isFocusedItem ? 'var(--md3-text-on-surface)' : 'var(--md3-text-primary)',
                                    margin: 0,
                                    marginBottom: '4px',
                                }}>
                                    {setting.title}
                                </h3>
                                <p style={{
                                    fontSize: '13px',
                                    color: 'var(--md3-text-secondary)',
                                    margin: 0,
                                }}>
                                    {setting.description}
                                </p>
                            </div>

                            {/* Right side control */}
                            {setting.type === 'toggle' && (
                                <div style={{
                                    width: '48px',
                                    height: '28px',
                                    borderRadius: '14px',
                                    background: setting.value ? 'var(--md3-surface-active)' : 'var(--md3-bg-primary)',
                                    border: `1px solid ${setting.value ? 'var(--md3-surface-active)' : 'var(--md3-border-color)'}`,
                                    display: 'flex',
                                    alignItems: 'center',
                                    padding: '2px',
                                    transition: 'all 0.2s ease',
                                }}>
                                    <motion.div
                                        animate={{ x: setting.value ? 20 : 0 }}
                                        transition={{ type: 'spring', stiffness: 500, damping: 30 }}
                                        style={{
                                            width: '22px',
                                            height: '22px',
                                            borderRadius: '50%',
                                            background: 'white',
                                            boxShadow: '0 2px 4px rgba(0,0,0,0.2)',
                                        }}
                                    />
                                </div>
                            )}

                            {setting.type === 'select' && (
                                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                    <span style={{ color: 'var(--md3-text-secondary)', fontSize: '14px' }}>
                                        {setting.options?.[0]}
                                    </span>
                                    <ChevronRight size={18} color="var(--md3-text-secondary)" />
                                </div>
                            )}

                            {setting.type === 'action' && (
                                <ChevronRight size={20} color="var(--md3-text-secondary)" />
                            )}
                        </motion.div>
                    );
                })}
            </div>

            {/* Footer */}
            <div style={{
                marginTop: '48px',
                textAlign: 'center',
                color: 'var(--md3-text-secondary)',
                fontSize: '12px',
            }}>
                <p style={{ margin: 0 }}>TV Go Universal • Version 1.0.0</p>
                <p style={{ margin: '8px 0 0 0' }}>© 2024 All Rights Reserved</p>
            </div>
        </motion.div>
    );
}
