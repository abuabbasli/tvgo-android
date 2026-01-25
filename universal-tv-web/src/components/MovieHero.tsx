import { motion } from 'framer-motion';
import { Play, Info } from 'lucide-react';
import { Movie } from '../types';

interface MovieHeroProps {
    movie: Movie;
    onPlay: (movie: Movie) => void;
    onInfo: (movie: Movie) => void;
    isMobile?: boolean;
    focusedButton?: 'play' | 'info' | null;
    logo?: string;
}

export default function MovieHero({ movie, onPlay, onInfo, isMobile = false, focusedButton = null, logo }: MovieHeroProps) {
    const isPlayFocused = focusedButton === 'play';
    const isInfoFocused = focusedButton === 'info';
    return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.8 }}
            style={{
                position: 'relative',
                width: '100%',
                height: isMobile ? '50vh' : '70vh',
                minHeight: isMobile ? '320px' : '450px',
                overflow: 'hidden',
                marginBottom: isMobile ? '8px' : '16px',
            }}
        >
            {/* Background Image */}
            <div style={{
                position: 'absolute',
                inset: 0,
            }}>
                <div
                    style={{
                        width: '100%',
                        height: '100%',
                        backgroundImage: `url(${movie.backdrop || movie.thumbnail})`,
                        backgroundSize: 'cover',
                        backgroundPosition: isMobile ? 'center 20%' : 'center 30%',
                        backgroundRepeat: 'no-repeat',
                    }}
                />
                {/* Gradient Overlays */}
                <div
                    style={{
                        position: 'absolute',
                        inset: 0,
                        background: isMobile
                            ? 'linear-gradient(to bottom, rgba(0, 0, 0, 0.2) 0%, rgba(0, 0, 0, 0.7) 70%, #0e0e0e 100%)'
                            : 'linear-gradient(to right, rgba(0, 0, 0, 0.8) 0%, rgba(0, 0, 0, 0.4) 40%, transparent 100%)',
                    }}
                />
                {!isMobile && (
                    <div
                        style={{
                            position: 'absolute',
                            inset: 0,
                            background: 'linear-gradient(to bottom, transparent 0%, transparent 60%, rgba(0, 0, 0, 0.8) 90%, #0e0e0e 100%)',
                        }}
                    />
                )}
            </div>

            {/* Logo in Top Left (desktop only) */}
            {logo && !isMobile && (
                <motion.div
                    initial={{ opacity: 0, x: -20 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: 0.2, duration: 0.7, ease: [0.45, 0, 0.55, 1] }}
                    style={{
                        position: 'absolute',
                        top: '32px',
                        left: '48px',
                        zIndex: 10,
                    }}
                >
                    <img
                        src={logo}
                        alt="TV Go"
                        style={{
                            height: '40px',
                            filter: 'drop-shadow(0 2px 8px rgba(0, 0, 0, 0.8))',
                        }}
                    />
                </motion.div>
            )}

            {/* Content */}
            <div style={{
                position: 'relative',
                height: '100%',
                display: 'flex',
                alignItems: 'flex-end',
                paddingBottom: isMobile ? '24px' : '80px',
                paddingLeft: isMobile ? '16px' : '48px',
                paddingRight: isMobile ? '16px' : '48px',
            }}>
                <div style={{ maxWidth: isMobile ? '100%' : '640px' }}>
                    {/* Service Badge - Hide on mobile */}
                    {!isMobile && (
                        <motion.div
                            initial={{ opacity: 0, y: 20 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: 0.2 }}
                            style={{ marginBottom: '16px' }}
                        >
                            <span style={{
                                color: 'rgba(255, 255, 255, 0.7)',
                                letterSpacing: '0.1em',
                                textTransform: 'uppercase',
                                fontSize: '14px',
                            }}>
                                tvGO Original
                            </span>
                        </motion.div>
                    )}

                    {/* Title */}
                    <motion.h1
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: 0.3 }}
                        style={{
                            color: 'white',
                            fontSize: isMobile ? '1.75rem' : '3.5rem',
                            lineHeight: 1.1,
                            textShadow: '0 2px 20px rgba(0, 0, 0, 0.8)',
                            fontWeight: 700,
                            marginBottom: isMobile ? '8px' : '16px',
                        }}
                    >
                        {movie.title}
                    </motion.h1>

                    {/* Movie Info */}
                    <motion.div
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: 0.4 }}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            color: 'rgba(255, 255, 255, 0.7)',
                            fontSize: isMobile ? '12px' : '14px',
                            marginBottom: isMobile ? '12px' : '16px',
                        }}
                    >
                        {movie.year && <span>{movie.year}</span>}
                        {movie.rating && (
                            <>
                                <span>•</span>
                                <span>⭐ {movie.rating}</span>
                            </>
                        )}
                        {!isMobile && movie.genre && movie.genre.length > 0 && (
                            <>
                                <span>•</span>
                                <span>{movie.genre.join(', ')}</span>
                            </>
                        )}
                    </motion.div>

                    {/* Description - Hide on mobile */}
                    {!isMobile && (
                        <motion.p
                            initial={{ opacity: 0, y: 20 }}
                            animate={{ opacity: 1, y: 0 }}
                            transition={{ delay: 0.5 }}
                            style={{
                                color: 'rgba(255, 255, 255, 0.8)',
                                fontSize: '1rem',
                                lineHeight: 1.5,
                                textShadow: '0 1px 10px rgba(0, 0, 0, 0.8)',
                                marginBottom: '24px',
                                maxWidth: '600px',
                            }}
                        >
                            {movie.description}
                        </motion.p>
                    )}

                    {/* Action Buttons */}
                    <motion.div
                        initial={{ opacity: 0, y: 20 }}
                        animate={{ opacity: 1, y: 0 }}
                        transition={{ delay: 0.6 }}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '12px',
                        }}
                    >
                        <motion.button
                            onClick={() => onPlay(movie)}
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                            animate={{ scale: isPlayFocused ? 1.05 : 1 }}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                padding: isMobile ? '10px 24px' : '16px 32px',
                                borderRadius: '9999px',
                                background: 'rgba(255, 255, 255, 0.95)',
                                boxShadow: isPlayFocused ? '0 0 0 3px var(--md3-surface-active), 0 4px 16px rgba(0, 0, 0, 0.5)' : '0 4px 16px rgba(0, 0, 0, 0.5)',
                                border: 'none',
                                cursor: 'pointer',
                                outline: isPlayFocused ? '3px solid var(--md3-surface-active)' : 'none',
                                outlineOffset: '2px',
                            }}
                        >
                            <Play size={isMobile ? 18 : 22} fill="black" style={{ color: 'black' }} />
                            <span style={{
                                color: 'black',
                                fontWeight: 600,
                                fontSize: isMobile ? '14px' : '16px',
                            }}>
                                Play
                            </span>
                        </motion.button>

                        <motion.button
                            onClick={() => onInfo(movie)}
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                            animate={{ scale: isInfoFocused ? 1.05 : 1 }}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                padding: isMobile ? '10px 24px' : '16px 32px',
                                borderRadius: '9999px',
                                background: 'rgba(255, 255, 255, 0.15)',
                                backdropFilter: 'blur(10px)',
                                border: '1px solid rgba(255, 255, 255, 0.2)',
                                cursor: 'pointer',
                                outline: isInfoFocused ? '3px solid var(--md3-surface-active)' : 'none',
                                outlineOffset: '2px',
                            }}
                        >
                            <Info size={isMobile ? 18 : 22} style={{ color: 'white' }} />
                            <span style={{
                                color: 'white',
                                fontWeight: 600,
                                fontSize: isMobile ? '14px' : '16px',
                            }}>
                                Info
                            </span>
                        </motion.button>
                    </motion.div>
                </div>
            </div>
        </motion.div>
    );
}
