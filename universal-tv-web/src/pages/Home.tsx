import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { Play } from 'lucide-react';
import { fetchChannels, fetchMovies } from '../api';
import { Channel, Movie } from '../types';
import MovieHero from '../components/MovieHero';
import ContentRow from '../components/ContentRow';
import VideoPlayer from '../components/VideoPlayer';
import MovieInfoDialog from '../components/MovieInfoDialog';
import { useNavigation } from '../context/NavigationContext';
import { useConfig } from '../context/ConfigContext';
import { TIZEN_KEYS } from '../utils/tizenKeys';
import { smartScrollIntoView } from '../utils/smartScroll';

interface HomePageProps {
    onNavigateToChannels?: () => void;
    onNavigateToMovies?: () => void;
    isFocused?: boolean;
}

// Channel Card for Home rows - 16:9 aspect ratio matching Tvgofigma (w-64)
function HomeChannelCard({ channel, onClick, isFocused }: { channel: Channel; onClick: () => void; isFocused: boolean }) {
    const [imageError, setImageError] = useState(false);
    const [isHovered, setIsHovered] = useState(false);
    const cardRef = useRef<HTMLButtonElement>(null);

    // Scroll into view when focused
    useEffect(() => {
        if (isFocused && cardRef.current) {
            smartScrollIntoView(cardRef.current, { block: 'nearest', inline: 'center' });
        }
    }, [isFocused]);

    const showOverlay = isHovered || isFocused;

    return (
        <motion.button
            ref={cardRef}
            onClick={onClick}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            whileHover={{ scale: 1.03, y: -6 }}
            whileTap={{ scale: 0.98 }}
            style={{
                flexShrink: 0,
                width: '256px',
                aspectRatio: '16/9',
                borderRadius: '16px',
                overflow: 'hidden',
                background: '#1a1a1a',
                border: isFocused ? '2px solid var(--md3-surface-active)' : '1px solid rgba(0, 0, 0, 0.1)',
                boxShadow: isFocused
                    ? '0 0 0 3px rgba(var(--md3-surface-active-rgb, 138, 180, 248), 0.3), 0 8px 32px rgba(0, 0, 0, 0.6)'
                    : '0 2px 8px rgba(0, 0, 0, 0.6)',
                cursor: 'pointer',
                position: 'relative',
                outline: 'none',
                transition: 'border 0.2s, box-shadow 0.3s',
            }}
        >
            {/* Hover overlay */}
            <motion.div
                animate={{ opacity: showOverlay ? 0.1 : 0 }}
                transition={{ duration: 0.3 }}
                style={{
                    position: 'absolute',
                    inset: 0,
                    background: 'rgba(255, 255, 255, 0.1)',
                }}
            />

            {/* Logo container */}
            <div style={{
                position: 'relative',
                width: '100%',
                height: '100%',
                padding: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
            }}>
                {!imageError && channel.logo ? (
                    <motion.img
                        animate={{ scale: showOverlay ? 1.05 : 1 }}
                        transition={{ type: 'spring', damping: 20, stiffness: 300 }}
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
                        color: '#white',
                        fontSize: '24px',
                        fontWeight: 600,
                        opacity: 0.7,
                        fontFamily: "'Inter', 'Roboto', sans-serif",
                    }}>
                        {channel.name.slice(0, 2)}
                    </div>
                )}
            </div>

            {/* Hover content */}
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: showOverlay ? 1 : 0 }}
                transition={{ duration: 0.3 }}
                style={{
                    position: 'absolute',
                    inset: 0,
                    background: 'linear-gradient(180deg, transparent 0%, rgba(0, 0, 0, 0.8) 70%, rgba(0, 0, 0, 1) 100%)',
                }}
            >
                <div style={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}>
                    <motion.div
                        initial={{ scale: 0, rotate: -90 }}
                        animate={{
                            scale: showOverlay ? 1 : 0,
                            rotate: showOverlay ? 0 : -90,
                        }}
                        transition={{ type: 'spring', damping: 15, stiffness: 300 }}
                        style={{
                            width: '56px',
                            height: '56px',
                            borderRadius: '50%',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            background: 'rgba(255, 255, 255, 0.9)',
                            boxShadow: '0 4px 12px rgba(0, 0, 0, 0.5)',
                        }}
                    >
                        <Play size={20} fill="black" style={{ color: 'black', marginLeft: '2px' }} />
                    </motion.div>
                </div>
                <div style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    padding: '16px',
                }}>
                    <p style={{
                        color: 'white',
                        textAlign: 'center',
                        fontSize: '14px',
                        fontWeight: 500,
                        margin: 0,
                        fontFamily: "'Inter', 'Roboto', sans-serif",
                    }}>
                        {channel.name}
                    </p>
                </div>
            </motion.div>
        </motion.button>
    );
}

// Movie Card for Home rows - 2:3 portrait aspect ratio matching Tvgofigma (w-44)
function HomeMovieCard({ movie, onClick, isFocused }: { movie: Movie; onClick: () => void; isFocused: boolean }) {
    const [isHovered, setIsHovered] = useState(false);
    const [imageLoaded, setImageLoaded] = useState(false);
    const cardRef = useRef<HTMLButtonElement>(null);

    // Scroll into view when focused
    useEffect(() => {
        if (isFocused && cardRef.current) {
            smartScrollIntoView(cardRef.current, { block: 'nearest', inline: 'center' });
        }
    }, [isFocused]);

    const showOverlay = isHovered || isFocused;

    return (
        <motion.button
            ref={cardRef}
            onClick={onClick}
            onMouseEnter={() => setIsHovered(true)}
            onMouseLeave={() => setIsHovered(false)}
            whileHover={{ scale: 1.05, y: -10 }}
            whileTap={{ scale: 0.98 }}
            style={{
                position: 'relative',
                flexShrink: 0,
                width: '180px',
                aspectRatio: '2/3',
                borderRadius: '16px',
                overflow: 'hidden',
                cursor: 'pointer',
                background: 'linear-gradient(145deg, #1a1a1a, #0d0d0d)',
                border: isFocused ? '2px solid var(--md3-surface-active)' : '1px solid rgba(255, 255, 255, 0.08)',
                boxShadow: isFocused
                    ? '0 0 0 3px rgba(var(--md3-surface-active-rgb, 138, 180, 248), 0.3), 0 16px 40px rgba(0, 0, 0, 0.7)'
                    : showOverlay
                        ? '0 20px 40px rgba(0, 0, 0, 0.8), inset 0 1px 0 rgba(255, 255, 255, 0.1)'
                        : '0 4px 20px rgba(0, 0, 0, 0.5)',
                outline: 'none',
                transition: 'border 0.2s, box-shadow 0.3s ease',
            }}
        >
            {/* Background Image with smooth fade-in */}
            <motion.img
                src={movie.thumbnail}
                alt={movie.title}
                animate={{ scale: showOverlay ? 1.08 : 1 }}
                transition={{ duration: 0.5, ease: [0.25, 0.1, 0.25, 1] }}
                onLoad={() => setImageLoaded(true)}
                style={{
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    objectPosition: 'center top',
                    opacity: imageLoaded ? 1 : 0,
                    transition: 'opacity 0.5s ease',
                }}
            />

            {/* Skeleton loader when image is loading */}
            {!imageLoaded && (
                <div style={{
                    position: 'absolute',
                    inset: 0,
                    background: 'linear-gradient(145deg, #2a2a2a, #1a1a1a)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                }}>
                    <motion.div
                        animate={{ opacity: [0.3, 0.6, 0.3] }}
                        transition={{ duration: 1.5, repeat: Infinity }}
                        style={{
                            width: '40px',
                            height: '40px',
                            borderRadius: '50%',
                            background: 'rgba(255, 255, 255, 0.1)',
                        }}
                    />
                </div>
            )}

            {/* Beautiful gradient overlay - always visible but stronger on hover */}
            <div
                style={{
                    position: 'absolute',
                    inset: 0,
                    background: showOverlay
                        ? 'linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.3) 50%, rgba(0,0,0,0.95) 100%)'
                        : 'linear-gradient(180deg, rgba(0,0,0,0) 30%, rgba(0,0,0,0.7) 80%, rgba(0,0,0,0.9) 100%)',
                    transition: 'background 0.4s ease',
                }}
            />

            {/* Subtle shine effect on hover */}
            <motion.div
                animate={{ opacity: showOverlay ? 1 : 0 }}
                transition={{ duration: 0.4 }}
                style={{
                    position: 'absolute',
                    inset: 0,
                    background: 'linear-gradient(135deg, rgba(255,255,255,0.1) 0%, transparent 50%, rgba(255,255,255,0.05) 100%)',
                    pointerEvents: 'none',
                }}
            />

            {/* Center Play Button - Premium glassmorphism style */}
            <div style={{
                position: 'absolute',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                pointerEvents: 'none',
            }}>
                <motion.div
                    initial={{ scale: 0, rotate: -90 }}
                    animate={{
                        scale: showOverlay ? 1 : 0,
                        rotate: showOverlay ? 0 : -90,
                    }}
                    transition={{ type: 'spring', damping: 12, stiffness: 250 }}
                    style={{
                        width: '52px',
                        height: '52px',
                        borderRadius: '50%',
                        background: 'rgba(255, 255, 255, 0.95)',
                        backdropFilter: 'blur(10px)',
                        boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4), inset 0 -2px 4px rgba(0, 0, 0, 0.1)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        border: '1px solid rgba(255, 255, 255, 0.3)',
                    }}
                >
                    <Play size={22} fill="#1a1a1a" style={{ color: '#1a1a1a', marginLeft: '2px' }} />
                </motion.div>
            </div>

            {/* Bottom Info Panel with smooth slide-up */}
            <motion.div
                animate={{
                    y: showOverlay ? 0 : 6,
                    opacity: showOverlay ? 1 : 0.9
                }}
                transition={{ duration: 0.3, ease: 'easeOut' }}
                style={{
                    position: 'absolute',
                    bottom: 0,
                    left: 0,
                    right: 0,
                    padding: '14px',
                }}
            >
                {/* Movie Title */}
                <p style={{
                    color: 'white',
                    fontSize: '14px',
                    fontWeight: 600,
                    fontFamily: "'Inter', 'Roboto', sans-serif",
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                    lineHeight: 1.3,
                    margin: 0,
                    textShadow: '0 2px 8px rgba(0, 0, 0, 0.5)',
                }}>
                    {movie.title}
                </p>

                {/* Movie Metadata */}
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    fontSize: '11px',
                    marginTop: '8px',
                }}>
                    {movie.year && (
                        <span style={{
                            color: 'rgba(255, 255, 255, 0.7)',
                            fontFamily: "'Inter', 'Roboto', sans-serif",
                        }}>{movie.year}</span>
                    )}
                    {movie.rating && movie.rating !== 'N/A' && (
                        <div style={{
                            padding: '3px 8px',
                            borderRadius: '6px',
                            background: 'rgba(255, 255, 255, 0.15)',
                            backdropFilter: 'blur(4px)',
                            border: '1px solid rgba(255, 255, 255, 0.1)',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '4px',
                        }}>
                            <span style={{
                                color: 'rgba(255, 255, 255, 0.95)',
                                fontWeight: 500,
                                fontFamily: "'Inter', 'Roboto', sans-serif",
                            }}>{movie.rating}</span>
                        </div>
                    )}
                </div>
            </motion.div>
        </motion.button>
    );
}


// Row data type for navigation
interface RowData {
    type: 'channel' | 'movie';
    items: (Channel | Movie)[];
    title: string;
}

export default function HomePage({ onNavigateToChannels, onNavigateToMovies, isFocused = false }: HomePageProps) {
    const { config } = useConfig();
    const [channels, setChannels] = useState<Channel[]>([]);
    const [movies, setMovies] = useState<Movie[]>([]);
    const [loading, setLoading] = useState(true);
    const [playingContent, setPlayingContent] = useState<{ url: string; title: string; type: 'tv' | 'movie'; channel?: Channel } | null>(null);
    const [selectedMovie, setSelectedMovie] = useState<Movie | null>(null);

    // Navigation state
    const [focusArea, setFocusArea] = useState<'hero' | 'rows'>('hero'); // Start at hero
    const [heroFocusedButton, setHeroFocusedButton] = useState<'play' | 'info'>('play'); // Which button in hero
    const [focusedRowIndex, setFocusedRowIndex] = useState(0);
    const [focusedItemIndex, setFocusedItemIndex] = useState(0);

    const heroRef = useRef<HTMLDivElement>(null);
    const rowsContainerRef = useRef<HTMLDivElement>(null);

    const { setFocusZone } = useNavigation();

    // Load data from API (config comes from ConfigContext)
    useEffect(() => {
        const loadData = async () => {
            setLoading(true);
            const [channelsData, moviesData] = await Promise.all([
                fetchChannels(),
                fetchMovies(),
            ]);

            // Load favorites from localStorage
            const savedFavorites = localStorage.getItem('tvgo-favorites');
            const favoriteIds: string[] = savedFavorites ? JSON.parse(savedFavorites) : [];

            const channelsWithFavorites = channelsData.map(ch => ({
                ...ch,
                isFavorite: favoriteIds.includes(ch.id)
            }));

            setChannels(channelsWithFavorites);
            setMovies(moviesData);

            setLoading(false);
        };
        loadData();
    }, []);



    // Featured movie for hero section - pick one with good images
    const featuredMovie = useMemo(() => {
        return movies.find(m => m.thumbnail) || movies[0];
    }, [movies]);

    // Channels by category - using actual category values from API
    const channelsByCategory = useMemo(() => {
        const categories: Record<string, Channel[]> = {};
        channels.forEach(ch => {
            const cat = ch.category || 'general';
            if (!categories[cat]) categories[cat] = [];
            if (categories[cat].length < 8) {
                categories[cat].push(ch);
            }
        });
        return categories;
    }, [channels]);

    // Movies by genre - using actual genre values from API
    const moviesByGenre = useMemo(() => {
        const genres: Record<string, Movie[]> = {};
        movies.forEach(m => {
            // Use genres array from API
            const movieGenres = m.genre || [];
            movieGenres.forEach((g: string) => {
                if (!genres[g]) genres[g] = [];
                if (genres[g].length < 8 && !genres[g].find(x => x.id === m.id)) {
                    genres[g].push(m);
                }
            });
        });
        return genres;
    }, [movies]);

    // Build ordered rows for navigation - dynamically based on actual data
    const rows = useMemo((): RowData[] => {
        const result: RowData[] = [];

        // Get actual channel categories from the data (keys of channelsByCategory)
        const actualChannelCategories = Object.keys(channelsByCategory).filter(
            cat => cat !== 'general' && cat !== 'All'
        );

        // Get actual movie genres from the data (keys of moviesByGenre)
        // This replaces the config-based approach to ensure we only show what we have
        const actualMovieGenres = Object.keys(moviesByGenre).filter(
            genre => genre !== 'All'
        );

        // Interleave channel categories and movie genres for a nice alternating pattern
        const maxRows = Math.max(actualChannelCategories.length, actualMovieGenres.length);

        for (let i = 0; i < maxRows; i++) {
            // Add channel category row if available
            if (i < actualChannelCategories.length) {
                const category = actualChannelCategories[i];
                const channelsInCategory = channelsByCategory[category];
                if (channelsInCategory?.length > 0) {
                    result.push({
                        type: 'channel',
                        items: channelsInCategory,
                        title: `${category} Channels`
                    });
                }
            }

            // Add movie genre row if available
            if (i < actualMovieGenres.length) {
                const genre = actualMovieGenres[i];
                const moviesInGenre = moviesByGenre[genre];
                if (moviesInGenre?.length > 0) {
                    result.push({
                        type: 'movie',
                        items: moviesInGenre,
                        title: `${genre} Movies`
                    });
                }
            }
        }

        // Fallback: if no rows found, show all available data
        if (result.length === 0) {
            // Show all channel categories we found
            Object.entries(channelsByCategory).forEach(([category, items]) => {
                if (items.length > 0) {
                    result.push({ type: 'channel', items, title: `${category} Channels` });
                }
            });
            // Show all movie genres we found
            Object.entries(moviesByGenre).forEach(([genre, items]) => {
                if (items.length > 0) {
                    result.push({ type: 'movie', items, title: `${genre} Movies` });
                }
            });
        }

        return result;
    }, [channelsByCategory, moviesByGenre]);



    // Keyboard navigation
    const handleKeyDown = useCallback((e: KeyboardEvent) => {
        if (!isFocused || playingContent || selectedMovie) return;

        const keyCode = e.keyCode;

        // Hero section navigation
        if (focusArea === 'hero') {
            switch (keyCode) {
                // Left Arrow - Move between hero buttons
                case TIZEN_KEYS.ARROW_LEFT:
                case 37:
                    e.preventDefault();
                    if (heroFocusedButton === 'info') {
                        setHeroFocusedButton('play');
                    } else {
                        // At leftmost button, go to sidebar
                        setFocusZone('sidebar');
                    }
                    break;

                // Right Arrow - Move between hero buttons
                case TIZEN_KEYS.ARROW_RIGHT:
                case 39:
                    e.preventDefault();
                    if (heroFocusedButton === 'play') {
                        setHeroFocusedButton('info');
                    }
                    break;

                // Down Arrow - Move to content rows
                case TIZEN_KEYS.ARROW_DOWN:
                case 40:
                    e.preventDefault();
                    if (rows.length > 0) {
                        setFocusArea('rows');
                        setFocusedRowIndex(0);
                        setFocusedItemIndex(0);
                    }
                    break;

                // Enter - Activate hero button
                case TIZEN_KEYS.ENTER:
                case 13:
                    e.preventDefault();
                    if (featuredMovie) {
                        if (heroFocusedButton === 'play') {
                            handlePlayMovie(featuredMovie);
                        } else {
                            handleMovieClick(featuredMovie);
                        }
                    }
                    break;
            }
        }
        // Content rows navigation
        else if (focusArea === 'rows') {
            const currentRow = rows[focusedRowIndex];
            const itemCount = currentRow ? currentRow.items.length + 1 : 0; // +1 for "See All" button
            const isOnSeeAll = focusedItemIndex === currentRow?.items.length; // Last position is See All

            switch (keyCode) {
                // Left Arrow
                case TIZEN_KEYS.ARROW_LEFT:
                case 37:
                    e.preventDefault();
                    if (focusedItemIndex > 0) {
                        setFocusedItemIndex(prev => prev - 1);
                    } else {
                        // At leftmost item, go to sidebar
                        setFocusZone('sidebar');
                    }
                    break;

                // Right Arrow
                case TIZEN_KEYS.ARROW_RIGHT:
                case 39:
                    e.preventDefault();
                    if (focusedItemIndex < itemCount - 1) {
                        setFocusedItemIndex(prev => prev + 1);
                    }
                    break;

                // Up Arrow
                case TIZEN_KEYS.ARROW_UP:
                case 38:
                    e.preventDefault();
                    if (focusedRowIndex > 0) {
                        setFocusedRowIndex(prev => prev - 1);
                        // Reset item index to 0 or keep if valid
                        const newRowItems = rows[focusedRowIndex - 1]?.items.length || 0;
                        setFocusedItemIndex(prev => Math.min(prev, newRowItems - 1));
                    } else {
                        // At first row, go back to hero section
                        setFocusArea('hero');
                        setHeroFocusedButton('play');
                        // Scroll hero into view
                        heroRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                    }
                    break;

                // Down Arrow
                case TIZEN_KEYS.ARROW_DOWN:
                case 40:
                    e.preventDefault();
                    if (focusedRowIndex < rows.length - 1) {
                        setFocusedRowIndex(prev => prev + 1);
                        // Reset item index to 0 or keep if valid
                        const newRowItems = rows[focusedRowIndex + 1]?.items.length || 0;
                        setFocusedItemIndex(prev => Math.min(prev, newRowItems - 1));
                    }
                    break;

                // Enter - Select/Play
                case TIZEN_KEYS.ENTER:
                case 13:
                    e.preventDefault();
                    if (currentRow) {
                        if (isOnSeeAll) {
                            // Trigger See All navigation
                            if (currentRow.type === 'channel' && onNavigateToChannels) {
                                onNavigateToChannels();
                            } else if (currentRow.type === 'movie' && onNavigateToMovies) {
                                onNavigateToMovies();
                            }
                        } else {
                            const item = currentRow.items[focusedItemIndex];
                            if (currentRow.type === 'channel') {
                                handleChannelClick(item as Channel);
                            } else {
                                handleMovieClick(item as Movie);
                            }
                        }
                    }
                    break;
            }
        }
    }, [isFocused, playingContent, selectedMovie, focusArea, heroFocusedButton, focusedRowIndex, focusedItemIndex, rows, setFocusZone, featuredMovie]);

    useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);

    // Scroll focused row into view
    useEffect(() => {
        if (focusArea === 'rows' && rowsContainerRef.current) {
            const rowElement = rowsContainerRef.current.querySelector(`[data-row-index="${focusedRowIndex}"]`);
            if (rowElement) {
                rowElement.scrollIntoView({
                    behavior: 'smooth',
                    block: 'center',
                });
            }
        }
    }, [focusArea, focusedRowIndex]);

    // Handle channel click
    const handleChannelClick = (channel: Channel) => {
        setPlayingContent({
            url: channel.streamUrl,
            title: channel.name,
            type: 'tv',
            channel: channel
        });
    };

    // Handle movie click
    const handleMovieClick = (movie: Movie) => {
        setSelectedMovie(movie);
    };

    // Handle play movie
    const handlePlayMovie = (movie: Movie) => {
        setPlayingContent({
            url: movie.videoUrl || movie.trailerUrl,
            title: movie.title,
            type: 'movie'
        });
        setSelectedMovie(null);
    };

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

    // Fullscreen player
    if (playingContent) {
        return (
            <VideoPlayer
                url={playingContent.url}
                title={playingContent.title}
                onClose={() => setPlayingContent(null)}
                variant={playingContent.type}
                channel={playingContent.channel}
            />
        );
    }

    // Render row with focus tracking
    const renderRow = (row: RowData, rowIndex: number) => {
        const isRowFocused = isFocused && focusArea === 'rows' && focusedRowIndex === rowIndex;
        const isSeeAllFocused = isRowFocused && focusedItemIndex === row.items.length;

        if (row.type === 'channel') {
            return (
                <ContentRow
                    key={row.title}
                    title={row.title}
                    onSeeAll={onNavigateToChannels}
                    isSeeAllFocused={isSeeAllFocused}
                >
                    {row.items.map((channel, itemIndex) => (
                        <HomeChannelCard
                            key={(channel as Channel).id}
                            channel={channel as Channel}
                            onClick={() => handleChannelClick(channel as Channel)}
                            isFocused={isRowFocused && focusedItemIndex === itemIndex}
                        />
                    ))}
                </ContentRow>
            );
        } else {
            return (
                <ContentRow
                    key={row.title}
                    title={row.title}
                    onSeeAll={onNavigateToMovies}
                    isSeeAllFocused={isSeeAllFocused}
                >
                    {row.items.map((movie, itemIndex) => (
                        <HomeMovieCard
                            key={(movie as Movie).id}
                            movie={movie as Movie}
                            onClick={() => handleMovieClick(movie as Movie)}
                            isFocused={isRowFocused && focusedItemIndex === itemIndex}
                        />
                    ))}
                </ContentRow>
            );
        }
    };

    return (
        <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: [0, 0, 0, 1] }}
            style={{
                overflow: 'auto',
                height: '100%',
            }}
        >
            {/* Hero Section - Full width connected to edges */}
            <div ref={heroRef} style={{ padding: 0 }}>
                {featuredMovie && (
                    <MovieHero
                        movie={featuredMovie}
                        onPlay={() => handlePlayMovie(featuredMovie)}
                        onInfo={() => handleMovieClick(featuredMovie)}
                        logo={config?.brand?.logoUrl}
                        focusedButton={isFocused && focusArea === 'hero' ? heroFocusedButton : null}
                    />
                )}
            </div>

            {/* Content Rows */}
            <div ref={rowsContainerRef} style={{ marginTop: '48px', padding: '0 32px' }}>
                {rows.map((row, index) => (
                    <div key={row.title} data-row-index={index}>
                        {renderRow(row, index)}
                    </div>
                ))}

                {/* Fallback: All Movies if no categories */}
                {rows.length === 0 && movies.length > 0 && (
                    <ContentRow title="Movies" onSeeAll={onNavigateToMovies}>
                        {movies.slice(0, 8).map((movie, itemIndex) => (
                            <HomeMovieCard
                                key={movie.id}
                                movie={movie}
                                onClick={() => handleMovieClick(movie)}
                                isFocused={isFocused && focusArea === 'rows' && focusedRowIndex === 0 && focusedItemIndex === itemIndex}
                            />
                        ))}
                    </ContentRow>
                )}

                {/* Fallback: All Channels if no categories */}
                {rows.length === 0 && channels.length > 0 && movies.length === 0 && (
                    <ContentRow title="Channels" onSeeAll={onNavigateToChannels}>
                        {channels.slice(0, 8).map((channel, itemIndex) => (
                            <HomeChannelCard
                                key={channel.id}
                                channel={channel}
                                onClick={() => handleChannelClick(channel)}
                                isFocused={isFocused && focusArea === 'rows' && focusedRowIndex === 0 && focusedItemIndex === itemIndex}
                            />
                        ))}
                    </ContentRow>
                )}
            </div>

            {/* Bottom padding */}
            <div style={{ height: '48px' }} />

            {/* Movie Info Dialog */}
            <MovieInfoDialog
                movie={selectedMovie}
                onClose={() => setSelectedMovie(null)}
                onPlay={handlePlayMovie}
            />
        </motion.div>
    );
}
