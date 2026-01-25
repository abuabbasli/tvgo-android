import { useState } from 'react';
import { motion } from 'framer-motion';
import { Star, Play, Pause } from 'lucide-react';
import { Channel } from '../types';

interface ChannelListItemProps {
    channel: Channel;
    index: number;
    onClick: () => void;
    onToggleFavorite?: (channelId: string) => void;
    isSelected?: boolean;
    isFocused?: boolean;
    isPlaying?: boolean;
    isLoading?: boolean;
}

export default function ChannelListItem({
    channel,
    index,
    onClick,
    onToggleFavorite,
    isSelected,
    isFocused = false,
    isPlaying = false,
    isLoading = false
}: ChannelListItemProps) {
    const [imageError, setImageError] = useState(false);
    const [isHovered, setIsHovered] = useState(false);

    const showActive = isSelected || isFocused;

    return (
        <motion.div
            onClick={onClick}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            initial={{ opacity: 0, x: -10 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{
                delay: index * 0.02,
                duration: 0.3,
                ease: [0.2, 0, 0, 1]
            }}
            whileHover={{ scale: 1.01 }}
            whileTap={{ scale: 0.99 }}
            style={{
                display: 'flex',
                alignItems: 'center',
                gap: '16px',
                padding: '12px 16px',
                borderRadius: 'var(--md3-border-radius)',
                cursor: 'pointer',
                background: showActive ? 'rgba(255, 255, 255, 0.1)' : 'var(--md3-bg-secondary)',
                border: showActive
                    ? '2px solid #FFFFFF'
                    : '1px solid var(--md3-border-color)',
                boxShadow: showActive ? 'var(--md3-shadow-elevation-2)' : 'none',
                transition: 'all 0.2s ease',
                transform: showActive ? 'scale(1.02)' : 'scale(1)',
            }}
        >
            {/* Channel Number */}
            {channel.order && (
                <div style={{
                    minWidth: '32px',
                    height: '32px',
                    flexShrink: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    background: 'rgba(255, 255, 255, 0.1)',
                    borderRadius: '6px',
                }}>
                    <span style={{
                        color: showActive ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                        fontSize: '14px',
                        fontWeight: 600,
                        fontFamily: 'system-ui, -apple-system, sans-serif',
                    }}>
                        {channel.order}
                    </span>
                </div>
            )}

            {/* Channel Logo */}
            <div style={{
                width: '64px',
                height: '48px',
                flexShrink: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: 'var(--md3-bg-primary)',
                borderRadius: '8px',
                overflow: 'hidden',
            }}>
                {!imageError ? (
                    <img
                        src={channel.logo}
                        alt={channel.name}
                        style={{
                            maxWidth: '100%',
                            maxHeight: '100%',
                            objectFit: 'contain',
                        }}
                        onError={() => setImageError(true)}
                    />
                ) : (
                    <span style={{
                        fontSize: '16px',
                        fontWeight: 600,
                        color: 'var(--md3-text-secondary)',
                    }}>
                        {channel.name.slice(0, 2)}
                    </span>
                )}
            </div>

            {/* Channel Info */}
            <div style={{
                flex: 1,
                minWidth: 0,
            }}>
                <h3 style={{
                    fontSize: '16px',
                    fontWeight: 600,
                    color: showActive ? 'var(--md3-text-on-surface)' : 'var(--md3-text-primary)',
                    margin: 0,
                    marginBottom: '4px',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                }}>
                    {channel.name}
                </h3>
                {channel.description && (
                    <p style={{
                        fontSize: '13px',
                        color: 'var(--md3-text-secondary)',
                        margin: 0,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                    }}>
                        {channel.description}
                    </p>
                )}
            </div>

            {/* Actions */}
            <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '8px',
            }}>
                {onToggleFavorite && (
                    <motion.button
                        onClick={(e) => {
                            e.stopPropagation();
                            onToggleFavorite(channel.id);
                        }}
                        whileHover={{ scale: 1.1 }}
                        whileTap={{ scale: 0.9 }}
                        style={{
                            padding: '8px',
                            borderRadius: '50%',
                            background: 'transparent',
                            border: 'none',
                            cursor: 'pointer',
                            display: 'flex',
                        }}
                    >
                        <Star
                            size={18}
                            fill={channel.isFavorite ? 'var(--md3-surface-active)' : 'none'}
                            color={channel.isFavorite ? 'var(--md3-surface-active)' : 'var(--md3-text-secondary)'}
                        />
                    </motion.button>
                )}

                {(isHovered || showActive) && (
                    <motion.div
                        initial={{ opacity: 0, scale: 0.8 }}
                        animate={{ opacity: 1, scale: 1 }}
                        style={{
                            width: '36px',
                            height: '36px',
                            borderRadius: '50%',
                            background: isPlaying ? 'var(--md3-surface-active)' : 'rgba(255, 255, 255, 0.9)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                        }}
                    >
                        {isLoading ? (
                            <motion.div
                                animate={{ rotate: 360 }}
                                transition={{ duration: 0.8, repeat: Infinity, ease: 'linear' }}
                                style={{
                                    width: '16px',
                                    height: '16px',
                                    border: '2px solid rgba(0, 0, 0, 0.2)',
                                    borderTopColor: 'black',
                                    borderRadius: '50%',
                                }}
                            />
                        ) : isPlaying ? (
                            <Pause size={16} fill="white" color="white" />
                        ) : (
                            <Play size={16} fill="black" color="black" style={{ marginLeft: '2px' }} />
                        )}
                    </motion.div>
                )}
            </div>
        </motion.div>
    );
}
