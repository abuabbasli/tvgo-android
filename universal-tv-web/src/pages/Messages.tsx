import { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { fetchMessages, MessageItem, markMessageRead } from '../api';
import { Mail, MailOpen, QrCode, ChevronDown, ChevronUp } from 'lucide-react';
import { useNavigation } from '../context/NavigationContext';
import { TIZEN_KEYS } from '../utils/tizenKeys';

interface MessagesPageProps {
    isFocused?: boolean;
}

const MessagesPage = ({ isFocused = false }: MessagesPageProps) => {
    const [messages, setMessages] = useState<MessageItem[]>([]);
    const [loading, setLoading] = useState(true);
    const [focusedIndex, setFocusedIndex] = useState(0);
    const [expandedId, setExpandedId] = useState<string | null>(null);

    useEffect(() => {
        setLoading(true);
        fetchMessages().then(data => {
            setMessages(data);
            setLoading(false);
        });
    }, []);

    const toggleExpand = (id: string) => {
        if (expandedId === id) {
            setExpandedId(null);
        } else {
            setExpandedId(id);
            const msg = messages.find(m => m.id === id);
            if (msg && !msg.isRead) {
                markMessageRead(id);
                setMessages(prev => prev.map(m => m.id === id ? { ...m, isRead: true } : m));
            }
        }
    };

    const { setFocusZone } = useNavigation();

    // Keyboard Navigation
    const handleKeyDown = useCallback((e: KeyboardEvent) => {
        if (!isFocused) return;

        const keyCode = e.keyCode;

        switch (keyCode) {
            case TIZEN_KEYS.ARROW_DOWN:
            case 40:
                e.preventDefault();
                if (focusedIndex < messages.length - 1) setFocusedIndex(prev => prev + 1);
                break;
            case TIZEN_KEYS.ARROW_UP:
            case 38:
                e.preventDefault();
                if (focusedIndex > 0) setFocusedIndex(prev => prev - 1);
                break;
            case TIZEN_KEYS.ARROW_LEFT:
            case 37:
                e.preventDefault();
                setFocusZone('sidebar');
                break;
            case TIZEN_KEYS.ENTER:
            case 13:
                e.preventDefault();
                if (messages[focusedIndex]) {
                    toggleExpand(messages[focusedIndex].id);
                }
                break;
        }
    }, [isFocused, messages, focusedIndex, setFocusZone]);

    useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);

    if (loading) {
        return (
            <div style={{
                width: '100%',
                height: '100%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
            }}>
                <motion.div
                    animate={{ rotate: 360 }}
                    transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                    style={{
                        width: '48px',
                        height: '48px',
                        border: '3px solid var(--md3-border-color)',
                        borderTopColor: 'var(--md3-surface-active)',
                        borderRadius: '50%',
                    }}
                />
            </div>
        );
    }

    return (
        <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            style={{
                padding: '64px 48px',
                maxWidth: '800px',
                height: '100%',
                overflowY: 'auto',
                display: 'flex',
                flexDirection: 'column',
            }}
        >
            {/* Header */}
            <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                marginBottom: '32px'
            }}>
                <Mail size={32} color="var(--md3-surface-active)" />
                <h1 style={{
                    fontSize: '28px',
                    fontWeight: 600,
                    margin: 0,
                }}>
                    Messages
                </h1>
                {messages.filter(m => !m.isRead).length > 0 && (
                    <span style={{
                        padding: '4px 12px',
                        borderRadius: '12px',
                        background: 'var(--md3-surface-active)',
                        fontSize: '12px',
                        fontWeight: 600,
                    }}>
                        {messages.filter(m => !m.isRead).length} new
                    </span>
                )}
            </div>

            {messages.length === 0 && (
                <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: 1 }}
                    style={{
                        textAlign: 'center',
                        color: 'var(--md3-text-secondary)',
                        marginTop: '60px',
                        display: 'flex',
                        flexDirection: 'column',
                        alignItems: 'center',
                    }}
                >
                    <Mail size={64} style={{ marginBottom: '16px', opacity: 0.5 }} />
                    <p style={{ fontSize: '18px' }}>No messages yet</p>
                </motion.div>
            )}

            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                {messages.map((msg, idx) => {
                    const isFocused = focusedIndex === idx;
                    const isExpanded = expandedId === msg.id;

                    return (
                        <motion.div
                            key={msg.id}
                            onClick={() => toggleExpand(msg.id)}
                            initial={{ opacity: 0, x: -10 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: idx * 0.02 }}
                            whileHover={{ scale: 1.01 }}
                            style={{
                                backgroundColor: 'var(--md3-bg-secondary)',
                                border: isFocused
                                    ? '2px solid var(--md3-surface-active)'
                                    : '1px solid var(--md3-border-color)',
                                borderRadius: 'var(--md3-border-radius)',
                                padding: '20px',
                                cursor: 'pointer',
                                transition: 'all 0.2s ease',
                            }}
                        >
                            <div style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center'
                            }}>
                                <div style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '16px',
                                    flex: 1,
                                }}>
                                    {msg.isRead
                                        ? <MailOpen size={24} color="var(--md3-text-secondary)" />
                                        : <Mail size={24} color="var(--md3-surface-active)" />
                                    }
                                    <span style={{
                                        fontSize: '16px',
                                        fontWeight: msg.isRead ? 400 : 600,
                                        color: msg.isRead ? 'var(--md3-text-secondary)' : 'var(--md3-text-primary)',
                                    }}>
                                        {msg.title}
                                    </span>
                                </div>
                                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                    {msg.url && <QrCode size={18} color="#10B981" />}
                                    {isExpanded ? <ChevronUp size={20} /> : <ChevronDown size={20} />}
                                </div>
                            </div>

                            {/* Collapsed Preview */}
                            {!isExpanded && (
                                <p style={{
                                    marginTop: '8px',
                                    color: 'var(--md3-text-secondary)',
                                    fontSize: '14px',
                                    overflow: 'hidden',
                                    whiteSpace: 'nowrap',
                                    textOverflow: 'ellipsis',
                                    marginLeft: '40px',
                                }}>
                                    {msg.body}
                                </p>
                            )}

                            {/* Expanded Content */}
                            <AnimatePresence>
                                {isExpanded && (
                                    <motion.div
                                        initial={{ opacity: 0, height: 0 }}
                                        animate={{ opacity: 1, height: 'auto' }}
                                        exit={{ opacity: 0, height: 0 }}
                                        style={{
                                            marginTop: '20px',
                                            borderTop: '1px solid var(--md3-border-color)',
                                            paddingTop: '20px',
                                            overflow: 'hidden',
                                        }}
                                    >
                                        <p style={{
                                            lineHeight: '1.6',
                                            color: 'var(--md3-text-primary)',
                                            fontSize: '15px',
                                        }}>
                                            {msg.body}
                                        </p>

                                        {msg.url && (
                                            <div style={{
                                                marginTop: '24px',
                                                display: 'flex',
                                                flexDirection: 'column',
                                                alignItems: 'center',
                                                gap: '12px'
                                            }}>
                                                <div style={{
                                                    padding: '12px',
                                                    backgroundColor: 'white',
                                                    borderRadius: '12px'
                                                }}>
                                                    <img
                                                        src={`https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(msg.url)}`}
                                                        alt="QR Code"
                                                        style={{ display: 'block' }}
                                                    />
                                                </div>
                                                <span style={{
                                                    color: 'var(--md3-text-secondary)',
                                                    fontSize: '13px'
                                                }}>
                                                    Scan to open link
                                                </span>
                                            </div>
                                        )}

                                        <div style={{
                                            marginTop: '20px',
                                            fontSize: '12px',
                                            color: 'var(--md3-text-secondary)'
                                        }}>
                                            Received: {new Date(msg.createdAt).toLocaleDateString()}
                                        </div>
                                    </motion.div>
                                )}
                            </AnimatePresence>
                        </motion.div>
                    );
                })}
            </div>
        </motion.div>
    );
};

export default MessagesPage;
