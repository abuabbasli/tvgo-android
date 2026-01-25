import { useState } from 'react';
import { motion } from 'framer-motion';
import { Star } from 'lucide-react';
import { Channel } from '../types';

interface ChannelCardProps {
    channel: Channel;
    index: number;
    onClick: () => void;
    onToggleFavorite?: (channelId: string) => void;
    isSelected?: boolean;
    isFocused?: boolean;
}

// Tvgofigma-style channel card
export default function ChannelCard({
    channel,
    index,
    onClick,
    onToggleFavorite,
    isSelected,
    isFocused = false
}: ChannelCardProps) {
    const [imageError, setImageError] = useState(false);
    const [isHovered, setIsHovered] = useState(false);

    const showOverlay = isHovered || isSelected || isFocused;

    return (
        <motion.div
            onClick={onClick}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{
                delay: index * 0.02,
                duration: 0.3,
                ease: [0.2, 0, 0, 1]
            }}
            whileHover={{ scale: 1.01 }}
            whileTap={{ scale: 0.98 }}
            style={{
                position: 'relative',
                aspectRatio: '16/9',
                borderRadius: '8px', // Standard rounded corners to match Figma
                overflow: 'hidden',
                cursor: 'pointer',
                background: 'var(--md3-bg-secondary)',
                border: (isSelected || isFocused)
                    ? '3px solid #FFFFFF'
                    : '1px solid var(--md3-border-color)',
                boxShadow: (isSelected || isFocused)
                    ? '0 0 0 1px rgba(255, 255, 255, 0.2), var(--md3-shadow-elevation-2)'
                    : 'var(--md3-shadow-elevation-1)',
                transition: 'border 0.2s cubic-bezier(0.2, 0, 0, 1), box-shadow 0.2s cubic-bezier(0.2, 0, 0, 1)',
                transform: isFocused ? 'scale(1.02)' : 'none',
            }}
        >
            {/* Channel Number Badge */}
            {channel.order && (
                <div style={{
                    position: 'absolute',
                    top: '8px',
                    left: '8px',
                    zIndex: 10,
                    padding: '4px 8px',
                    borderRadius: '4px',
                    background: 'rgba(0, 0, 0, 0.7)',
                    backdropFilter: 'blur(4px)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}>
                    <span style={{
                        color: '#FFFFFF',
                        fontSize: '12px',
                        fontWeight: 600,
                        fontFamily: 'system-ui, -apple-system, sans-serif',
                    }}>
                        {channel.order}
                    </span>
                </div>
            )}

            {/* Favorite Button */}
            {onToggleFavorite && (
                <motion.button
                    onClick={(e) => {
                        e.stopPropagation();
                        onToggleFavorite(channel.id);
                    }}
                    whileHover={{ scale: 1.1 }}
                    whileTap={{ scale: 0.9 }}
                    style={{
                        position: 'absolute',
                        top: '8px',
                        right: '8px',
                        zIndex: 10,
                        padding: '6px',
                        borderRadius: '50%',
                        background: 'var(--md3-bg-secondary)',
                        border: '1px solid var(--md3-border-color)',
                        cursor: 'pointer',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}
                >
                    <Star
                        size={14}
                        fill={channel.isFavorite ? 'var(--md3-text-on-surface)' : 'none'}
                        color={channel.isFavorite ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)'}
                    />
                </motion.button>
            )}

            {/* Hover overlay background */}
            <motion.div
                animate={{ opacity: showOverlay ? 1 : 0 }}
                transition={{ duration: 0.15, ease: [0.2, 0, 0, 1] }}
                style={{
                    position: 'absolute',
                    inset: 0,
                    background: 'var(--md3-surface-hover)',
                    pointerEvents: 'none',
                }}
            />

            {/* Channel Logo */}
            <div style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '16px',
                pointerEvents: 'none',
            }}>
                {!imageError ? (
                    <motion.img
                        animate={{ scale: showOverlay ? 1.05 : 1 }}
                        transition={{ duration: 0.2, ease: [0.2, 0, 0, 1] }}
                        src={channel.logo}
                        alt={channel.name}
                        style={{
                            maxWidth: '100%',
                            maxHeight: '100%',
                            objectFit: 'contain',
                            filter: 'drop-shadow(0 4px 12px rgba(0, 0, 0, 0.3))',
                        }}
                        onError={() => setImageError(true)}
                    />
                ) : (
                    <div style={{
                        color: 'var(--md3-text-primary)',
                        fontSize: '24px',
                        opacity: 0.8,
                        fontWeight: 600,
                    }}>
                        {channel.name.slice(0, 2)}
                    </div>
                )}
            </div>

            {/* Hover Info Overlay */}
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: showOverlay ? 1 : 0 }}
                transition={{ duration: 0.2, ease: [0.2, 0, 0, 1] }}
                style={{
                    position: 'absolute',
                    inset: 0,
                    background: 'linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.7) 60%, rgba(0, 0, 0, 0.95) 100%)',
                    pointerEvents: 'none',
                }}
            >
                {/* Bottom Info */}
                <div style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    padding: '10px',
                }}>
                    <p style={{
                        color: isFocused ? 'var(--md3-text-on-surface)' : 'var(--md3-text-primary)',
                        fontSize: '13px',
                        lineHeight: '18px',
                        marginBottom: '2px',
                        fontWeight: 500,
                        transition: 'color 0.2s',
                    }}>
                        {channel.name}
                    </p>
                    {channel.description && (
                        <p style={{
                            color: 'var(--md3-text-secondary)',
                            fontSize: '11px',
                            lineHeight: '16px',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                            whiteSpace: 'nowrap',
                        }}>
                            {channel.description}
                        </p>
                    )}
                </div>
            </motion.div>
        </motion.div>
    );
}
