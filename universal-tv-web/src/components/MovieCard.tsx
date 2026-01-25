import { useState } from 'react';
import { motion } from 'framer-motion';
import { Play, Plus, ChevronDown, Star } from 'lucide-react';
import { Movie } from '../types';

interface MovieCardProps {
    movie: Movie;
    index: number;
    onClick: () => void;
    isFocused?: boolean;
}

// Netflix/TVGO-style landscape movie card (16:9 aspect ratio)
export default function MovieCard({ movie, index, onClick, isFocused = false }: MovieCardProps) {
    const [isHovered, setIsHovered] = useState(false);

    const showOverlay = isHovered || isFocused;

    return (
        <motion.div
            onClick={onClick}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            transition={{
                delay: index * 0.05,
                duration: 0.4,
            }}
            whileHover={{
                scale: 1.05,
                zIndex: 10,
            }}
            whileTap={{ scale: 0.98 }}
            style={{
                position: 'relative',
                width: '320px', // Slightly bigger (was 288px)
                aspectRatio: '16/9',
                borderRadius: '8px',
                overflow: 'hidden',
                cursor: 'pointer',
                background: '#1a1a1a',
                flexShrink: 0,
                transformOrigin: 'center center',
                outline: isFocused ? '3px solid var(--md3-surface-active)' : 'none',
                outlineOffset: '2px',
                transform: isFocused ? 'scale(1.05)' : 'none',
                zIndex: isFocused ? 10 : 1,
                transition: 'transform 0.2s, outline 0.2s',
            }}
        >
            {/* Background Image (Backdrop) */}
            <motion.div
                animate={{ scale: showOverlay ? 1.05 : 1 }}
                transition={{ duration: 0.3 }}
                style={{
                    width: '100%',
                    height: '100%',
                    backgroundImage: `url(${movie.backdrop || movie.thumbnail})`,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center',
                }}
            />

            {/* Gradient Overlay */}
            <motion.div
                animate={{ opacity: showOverlay ? 1 : 0.6 }}
                transition={{ duration: 0.3 }}
                style={{
                    position: 'absolute',
                    inset: 0,
                    background: 'linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.3) 50%, rgba(0, 0, 0, 0.9) 100%)',
                }}
            />

            {/* Center Play Button - Scale effect */}
            <div style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                pointerEvents: 'none',
            }}>
                <motion.div
                    initial={{ scale: 0, opacity: 0 }}
                    animate={{
                        scale: showOverlay ? 1 : 0,
                        opacity: showOverlay ? 1 : 0,
                    }}
                    transition={{ type: 'spring', damping: 20, stiffness: 300 }}
                    style={{
                        width: '48px',
                        height: '48px',
                        borderRadius: '50%',
                        background: 'rgba(255, 255, 255, 0.9)',
                        border: '2px solid white',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                    }}
                >
                    <Play size={20} fill="black" color="black" style={{ marginLeft: '2px' }} />
                </motion.div>
            </div>

            {/* Bottom Info Panel */}
            <motion.div
                initial={{ y: 0 }}
                animate={{
                    y: showOverlay ? 0 : 12,
                    opacity: showOverlay ? 1 : 0.9,
                }}
                transition={{ duration: 0.3 }}
                style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    padding: '16px',
                }}
            >
                {/* Movie Title */}
                <h3 style={{
                    color: 'white',
                    fontSize: '14px',
                    fontWeight: 600,
                    marginBottom: '8px',
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                }}>
                    {movie.title}
                </h3>

                {/* Action Buttons */}
                <motion.div
                    initial={{ opacity: 0, y: 10 }}
                    animate={{
                        opacity: showOverlay ? 1 : 0,
                        y: showOverlay ? 0 : 10,
                    }}
                    transition={{ duration: 0.2, delay: showOverlay ? 0.1 : 0 }}
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        marginBottom: '8px',
                    }}
                >
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            onClick();
                        }}
                        style={{
                            width: '32px',
                            height: '32px',
                            borderRadius: '50%',
                            background: 'white',
                            border: 'none',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                        }}
                    >
                        <Play size={14} fill="black" color="black" style={{ marginLeft: '2px' }} />
                    </button>

                    <button
                        onClick={(e) => e.stopPropagation()}
                        style={{
                            width: '32px',
                            height: '32px',
                            borderRadius: '50%',
                            background: 'transparent',
                            border: '2px solid rgba(255, 255, 255, 0.5)',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: 'white',
                        }}
                    >
                        <Plus size={16} />
                    </button>

                    <button
                        onClick={(e) => e.stopPropagation()}
                        style={{
                            width: '32px',
                            height: '32px',
                            borderRadius: '50%',
                            background: 'transparent',
                            border: '2px solid rgba(255, 255, 255, 0.5)',
                            cursor: 'pointer',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            color: 'white',
                            marginLeft: 'auto',
                        }}
                    >
                        <ChevronDown size={16} />
                    </button>
                </motion.div>

                {/* Meta Data */}
                <motion.div
                    initial={{ opacity: 0 }}
                    animate={{ opacity: showOverlay ? 1 : 0 }}
                    transition={{ duration: 0.2, delay: showOverlay ? 0.15 : 0 }}
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px',
                        fontSize: '12px',
                    }}
                >
                    {movie.rating && (
                        <span style={{
                            color: '#4ade80',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                        }}>
                            <Star size={12} fill="#4ade80" color="#4ade80" />
                            {movie.rating}
                        </span>
                    )}
                    {movie.year && (
                        <span style={{ color: 'rgba(255, 255, 255, 0.7)' }}>{movie.year}</span>
                    )}
                </motion.div>

                {/* Genres */}
                {movie.genre && movie.genre.length > 0 && (
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: showOverlay ? 1 : 0 }}
                        transition={{ duration: 0.2, delay: showOverlay ? 0.2 : 0 }}
                        style={{
                            fontSize: '11px',
                            color: 'rgba(255, 255, 255, 0.6)',
                            marginTop: '4px',
                            display: 'block',
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                        }}
                    >
                        {movie.genre.slice(0, 3).join(' • ')}
                    </motion.div>
                )}
            </motion.div>
        </motion.div>
    );
}
