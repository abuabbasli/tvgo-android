import { useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Movie } from '../types';
import { Play, Star, Clock } from 'lucide-react';
import { TIZEN_KEYS } from '../utils/tizenKeys';

interface MovieInfoDialogProps {
    movie: Movie | null;
    onClose: () => void;
    onPlay: (movie: Movie) => void;
    isMobile?: boolean;
}

// Get initials from a name (e.g., "David Arquette" -> "D")
function getInitial(name: string): string {
    return name.trim().charAt(0).toUpperCase();
}

export default function MovieInfoDialog({ movie, onClose, onPlay, isMobile = false }: MovieInfoDialogProps) {
    // Keyboard navigation
    const handleKeyDown = useCallback((e: KeyboardEvent) => {
        if (!movie) return;

        const keyCode = e.keyCode;

        switch (keyCode) {
            case TIZEN_KEYS.BACK:
            case 27:
            case 10009:
                e.preventDefault();
                e.stopPropagation();
                onClose();
                break;

            case TIZEN_KEYS.ENTER:
            case 13:
                e.preventDefault();
                e.stopPropagation();
                onPlay(movie);
                onClose();
                break;
        }
    }, [movie, onClose, onPlay]);

    useEffect(() => {
        if (movie) {
            window.addEventListener('keydown', handleKeyDown);
            return () => window.removeEventListener('keydown', handleKeyDown);
        }
    }, [movie, handleKeyDown]);

    if (!movie) return null;

    const formattedRuntime = movie.runtime ? `${movie.runtime} min` : null;

    // TV-optimized sizes
    const sizes = isMobile ? {
        titleSize: '32px',
        metaSize: '16px',
        genreSize: '13px',
        descSize: '15px',
        directorSize: '15px',
        castLabelSize: '17px',
        castInitialSize: '20px',
        castNameSize: '12px',
        castAvatarSize: '50px',
        castWidth: '70px',
        buttonPadding: '14px 32px',
        buttonFontSize: '16px',
        buttonIconSize: 22,
        posterWidth: '200px',
        contentPadding: '24px',
        contentMaxWidth: '100%',
        gap: '12px',
    } : {
        // TV sizes - larger for 10-foot viewing distance
        titleSize: '72px',
        metaSize: '24px',
        genreSize: '18px',
        descSize: '22px',
        directorSize: '20px',
        castLabelSize: '24px',
        castInitialSize: '28px',
        castNameSize: '14px',
        castAvatarSize: '70px',
        castWidth: '90px',
        buttonPadding: '20px 48px',
        buttonFontSize: '24px',
        buttonIconSize: 32,
        posterWidth: '320px',
        contentPadding: '80px',
        contentMaxWidth: '50%',
        gap: '20px',
    };

    return (
        <AnimatePresence>
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                style={{
                    position: 'fixed',
                    inset: 0,
                    zIndex: 50,
                    background: '#0a0a0a',
                    overflow: 'hidden',
                }}
            >
                {/* Background - Backdrop Image */}
                <div style={{
                    position: 'absolute',
                    inset: 0,
                    overflow: 'hidden',
                }}>
                    <img
                        src={movie.backdrop || movie.thumbnail}
                        alt={movie.title}
                        style={{
                            position: 'absolute',
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover',
                        }}
                    />

                    {/* Gradient Overlays - stronger for TV readability */}
                    <div style={{
                        position: 'absolute',
                        inset: 0,
                        background: isMobile
                            ? 'linear-gradient(to right, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.6) 50%, rgba(0,0,0,0.2) 100%)'
                            : 'linear-gradient(to right, rgba(0,0,0,0.95) 0%, rgba(0,0,0,0.85) 35%, rgba(0,0,0,0.4) 55%, rgba(0,0,0,0.1) 75%)',
                    }} />
                    <div style={{
                        position: 'absolute',
                        inset: 0,
                        background: 'linear-gradient(to top, rgba(0,0,0,0.8) 0%, rgba(0,0,0,0.3) 20%, transparent 50%)',
                    }} />
                </div>

                {/* Content - Left Side */}
                <div style={{
                    position: 'absolute',
                    left: 0,
                    top: 0,
                    bottom: 0,
                    width: sizes.contentMaxWidth,
                    padding: sizes.contentPadding,
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'center',
                    overflowY: 'auto',
                }}>
                    {/* Title */}
                    <motion.h1
                        initial={{ opacity: 0, x: -30 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.1 }}
                        style={{
                            color: 'white',
                            fontSize: sizes.titleSize,
                            fontWeight: 800,
                            marginBottom: sizes.gap,
                            textShadow: '0 4px 30px rgba(0,0,0,0.8)',
                            lineHeight: 1.1,
                        }}
                    >
                        {movie.title}
                    </motion.h1>

                    {/* Meta Info Row */}
                    <motion.div
                        initial={{ opacity: 0, x: -30 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.15 }}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: sizes.gap,
                            marginBottom: sizes.gap,
                        }}
                    >
                        {movie.year && (
                            <span style={{
                                color: 'rgb(74, 222, 128)',
                                fontSize: sizes.metaSize,
                                fontWeight: 700,
                            }}>
                                {movie.year}
                            </span>
                        )}

                        {movie.rating && (
                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                            }}>
                                <Star size={isMobile ? 18 : 26} fill="rgb(251, 191, 36)" color="rgb(251, 191, 36)" />
                                <span style={{
                                    color: 'white',
                                    fontWeight: 700,
                                    fontSize: sizes.metaSize,
                                }}>
                                    {movie.rating}
                                </span>
                            </div>
                        )}

                        {formattedRuntime && (
                            <div style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '8px',
                                color: 'rgba(255,255,255,0.8)',
                            }}>
                                <Clock size={isMobile ? 16 : 24} />
                                <span style={{ fontSize: sizes.metaSize }}>
                                    {formattedRuntime}
                                </span>
                            </div>
                        )}
                    </motion.div>

                    {/* Genres */}
                    {movie.genre && movie.genre.length > 0 && (
                        <motion.div
                            initial={{ opacity: 0, x: -30 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: 0.2 }}
                            style={{
                                display: 'flex',
                                flexWrap: 'wrap',
                                gap: isMobile ? '8px' : '12px',
                                marginBottom: isMobile ? '16px' : '28px',
                            }}
                        >
                            {movie.genre.map((genre, index) => (
                                <motion.span
                                    key={genre}
                                    initial={{ opacity: 0, scale: 0.8 }}
                                    animate={{ opacity: 1, scale: 1 }}
                                    transition={{ delay: 0.25 + index * 0.05 }}
                                    style={{
                                        padding: isMobile ? '6px 14px' : '10px 24px',
                                        borderRadius: '6px',
                                        background: 'rgba(239, 68, 68, 0.9)',
                                        color: 'white',
                                        fontSize: sizes.genreSize,
                                        fontWeight: 600,
                                    }}
                                >
                                    {genre}
                                </motion.span>
                            ))}
                        </motion.div>
                    )}

                    {/* Description */}
                    <motion.p
                        initial={{ opacity: 0, x: -30 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.3 }}
                        style={{
                            color: 'rgba(255,255,255,0.9)',
                            fontSize: sizes.descSize,
                            lineHeight: 1.6,
                            marginBottom: isMobile ? '24px' : '36px',
                            maxWidth: isMobile ? '100%' : '700px',
                            display: '-webkit-box',
                            WebkitLineClamp: isMobile ? 4 : 4,
                            WebkitBoxOrient: 'vertical',
                            overflow: 'hidden',
                        }}
                    >
                        {movie.description}
                    </motion.p>

                    {/* Play Button */}
                    <motion.div
                        initial={{ opacity: 0, x: -30 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.35 }}
                        style={{
                            marginBottom: isMobile ? '24px' : '36px',
                        }}
                    >
                        <motion.button
                            whileHover={{ scale: 1.05 }}
                            whileTap={{ scale: 0.95 }}
                            animate={{
                                scale: 1.05,
                                boxShadow: '0 0 0 4px rgba(255,255,255,0.6), 0 15px 40px rgba(0,0,0,0.4)'
                            }}
                            onClick={() => {
                                onPlay(movie);
                                onClose();
                            }}
                            style={{
                                padding: sizes.buttonPadding,
                                borderRadius: '12px',
                                display: 'inline-flex',
                                alignItems: 'center',
                                gap: '12px',
                                background: 'white',
                                border: 'none',
                                cursor: 'pointer',
                                transition: 'all 0.2s',
                            }}
                        >
                            <Play size={sizes.buttonIconSize} fill="black" color="black" />
                            <span style={{
                                color: 'black',
                                fontSize: sizes.buttonFontSize,
                                fontWeight: 700,
                            }}>
                                Play
                            </span>
                        </motion.button>
                    </motion.div>

                    {/* Director */}
                    {movie.directors && movie.directors.length > 0 && (
                        <motion.div
                            initial={{ opacity: 0, x: -30 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: 0.4 }}
                            style={{ marginBottom: isMobile ? '20px' : '28px' }}
                        >
                            <span style={{
                                color: 'rgba(255,255,255,0.5)',
                                fontSize: sizes.directorSize,
                            }}>
                                Directed by{' '}
                            </span>
                            <span style={{
                                color: 'white',
                                fontSize: sizes.directorSize,
                                fontWeight: 600,
                            }}>
                                {movie.directors.join(', ')}
                            </span>
                        </motion.div>
                    )}

                    {/* Cast */}
                    {movie.cast && movie.cast.length > 0 && (
                        <motion.div
                            initial={{ opacity: 0, x: -30 }}
                            animate={{ opacity: 1, x: 0 }}
                            transition={{ delay: 0.45 }}
                        >
                            <h4 style={{
                                color: 'white',
                                fontSize: sizes.castLabelSize,
                                marginBottom: isMobile ? '14px' : '20px',
                                fontWeight: 600,
                            }}>
                                Cast
                            </h4>
                            <div style={{
                                display: 'flex',
                                gap: isMobile ? '14px' : '20px',
                            }}>
                                {movie.cast.slice(0, isMobile ? 5 : 7).map((actor, index) => (
                                    <motion.div
                                        key={index}
                                        initial={{ opacity: 0, y: 20 }}
                                        animate={{ opacity: 1, y: 0 }}
                                        transition={{ delay: 0.5 + index * 0.05 }}
                                        style={{
                                            display: 'flex',
                                            flexDirection: 'column',
                                            alignItems: 'center',
                                            gap: '10px',
                                            width: sizes.castWidth,
                                        }}
                                    >
                                        <div style={{
                                            width: sizes.castAvatarSize,
                                            height: sizes.castAvatarSize,
                                            borderRadius: '50%',
                                            background: 'rgba(100, 116, 139, 0.6)',
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'center',
                                            fontSize: sizes.castInitialSize,
                                            fontWeight: 600,
                                            color: 'white',
                                        }}>
                                            {getInitial(actor)}
                                        </div>
                                        <span style={{
                                            color: 'rgba(255,255,255,0.7)',
                                            fontSize: sizes.castNameSize,
                                            textAlign: 'center',
                                            lineHeight: 1.3,
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis',
                                            display: '-webkit-box',
                                            WebkitLineClamp: 2,
                                            WebkitBoxOrient: 'vertical',
                                        }}>
                                            {actor}
                                        </span>
                                    </motion.div>
                                ))}
                            </div>
                        </motion.div>
                    )}
                </div>

                {/* Movie Poster - Right Side (TV only) */}
                {!isMobile && (
                    <motion.div
                        initial={{ opacity: 0, x: 50 }}
                        animate={{ opacity: 1, x: 0 }}
                        transition={{ delay: 0.3 }}
                        style={{
                            position: 'absolute',
                            right: '8%',
                            bottom: '10%',
                            width: sizes.posterWidth,
                            borderRadius: '16px',
                            overflow: 'hidden',
                            boxShadow: '0 30px 80px rgba(0,0,0,0.6)',
                        }}
                    >
                        <img
                            src={movie.thumbnail}
                            alt={movie.title}
                            style={{
                                width: '100%',
                                aspectRatio: '2/3',
                                objectFit: 'cover',
                            }}
                        />
                    </motion.div>
                )}
            </motion.div>
        </AnimatePresence>
    );
}
