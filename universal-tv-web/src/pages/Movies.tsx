import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { motion } from 'framer-motion';
import { fetchMovies } from '../api';
import { Movie } from '../types';
import HorizontalCategoryMenu from '../components/HorizontalCategoryMenu';
import MovieHero from '../components/MovieHero';
import MovieRow from '../components/MovieRow';
import MovieInfoDialog from '../components/MovieInfoDialog';
import VideoPlayer from '../components/VideoPlayer';
import { useNavigation } from '../context/NavigationContext';
import { useConfig } from '../context/ConfigContext';
import { TIZEN_KEYS } from '../utils/tizenKeys';
import { getSmartScrollBehavior } from '../utils/smartScroll';

interface MoviesPageProps {
    isFocused?: boolean;
}

export default function MoviesPage({ isFocused = false }: MoviesPageProps) {
    const { movieCategories } = useConfig();
    const [movies, setMovies] = useState<Movie[]>([]);
    const [loading, setLoading] = useState(true);
    const [activeCategory, setActiveCategory] = useState('all');
    const [selectedMovie, setSelectedMovie] = useState<Movie | null>(null);
    const [playingMovie, setPlayingMovie] = useState<Movie | null>(null);

    // Navigation state
    const [focusArea, setFocusArea] = useState<'hero' | 'categories' | 'rows'>('hero'); // Start at hero
    const [heroFocusedButton, setHeroFocusedButton] = useState<'play' | 'info'>('play'); // Which button in hero
    const [focusedCategoryIndex, setFocusedCategoryIndex] = useState(0);
    const [focusedRowIndex, setFocusedRowIndex] = useState(0);
    const [focusedItemIndex, setFocusedItemIndex] = useState(0);

    // Refs for scrolling
    const categoriesRef = useRef<HTMLDivElement>(null);
    const heroRef = useRef<HTMLDivElement>(null);
    const rowsRef = useRef<HTMLDivElement>(null);

    const { setFocusZone } = useNavigation();

    // Load movies from API (categories come from ConfigContext)
    useEffect(() => {
        const loadMovies = async () => {
            setLoading(true);
            const data = await fetchMovies();
            setMovies(data);
            setLoading(false);
        };
        loadMovies();
    }, []);

    // Filter movies based on category
    const filteredMovies = useMemo(() => {
        if (activeCategory === 'all') return movies;
        return movies.filter(m =>
            m.category?.toLowerCase().replace(/\s+/g, '-') === activeCategory ||
            m.genre?.some(g => g.toLowerCase().replace(/\s+/g, '-') === activeCategory)
        );
    }, [movies, activeCategory]);

    // Group movies by category for rows
    const moviesByCategory = useMemo(() => {
        const catMap: Record<string, Movie[]> = {};
        movieCategories.filter(c => c.id !== 'all').forEach(cat => {
            catMap[cat.id] = movies.filter(m =>
                m.category?.toLowerCase().replace(/\s+/g, '-') === cat.id ||
                m.genre?.some(g => g.toLowerCase().replace(/\s+/g, '-') === cat.id)
            );
        });
        return catMap;
    }, [movies, movieCategories]);

    // Build rows for navigation
    const rows = useMemo(() => {
        if (activeCategory === 'all') {
            return Object.entries(moviesByCategory)
                .filter(([_, categoryMovies]) => categoryMovies.length > 0)
                .map(([categoryId, categoryMovies]) => ({
                    id: categoryId,
                    movies: categoryMovies,
                    title: movieCategories.find(c => c.id === categoryId)?.name || categoryId
                }));
        } else {
            return [{
                id: activeCategory,
                movies: filteredMovies,
                title: movieCategories.find(c => c.id === activeCategory)?.name || 'Movies'
            }];
        }
    }, [activeCategory, moviesByCategory, filteredMovies, movieCategories]);

    // Featured movie (first one or from filtered)
    const featuredMovie = filteredMovies[0] || movies[0];

    // Handle movie click
    const handleMovieClick = useCallback((movie: Movie) => {
        setSelectedMovie(movie);
    }, []);

    // Handle play movie
    const handlePlayMovie = useCallback((movie: Movie) => {
        setPlayingMovie(movie);
        setSelectedMovie(null);
    }, []);



    // Keyboard navigation
    const handleKeyDown = useCallback((e: KeyboardEvent) => {
        if (!isFocused || playingMovie || selectedMovie) return;

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

                // Up Arrow - Move to categories (above hero)
                case TIZEN_KEYS.ARROW_UP:
                case 38:
                    e.preventDefault();
                    if (movieCategories.length > 0) {
                        setFocusArea('categories');
                        setFocusedCategoryIndex(0);
                        categoriesRef.current?.scrollIntoView({ behavior: getSmartScrollBehavior(), block: 'center' });
                    }
                    break;

                // Down Arrow - Move to rows (below hero)
                case TIZEN_KEYS.ARROW_DOWN:
                case 40:
                    e.preventDefault();
                    if (rows.length > 0) {
                        setFocusArea('rows');
                        setFocusedRowIndex(0);
                        setFocusedItemIndex(0);
                        rowsRef.current?.scrollIntoView({ behavior: getSmartScrollBehavior(), block: 'start' });
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
        // Categories navigation
        else if (focusArea === 'categories') {
            switch (keyCode) {
                // Left Arrow - Move between categories
                case TIZEN_KEYS.ARROW_LEFT:
                case 37:
                    e.preventDefault();
                    if (focusedCategoryIndex > 0) {
                        setFocusedCategoryIndex(prev => prev - 1);
                    } else {
                        // At leftmost category, go to sidebar
                        setFocusZone('sidebar');
                    }
                    break;

                // Right Arrow - Move between categories
                case TIZEN_KEYS.ARROW_RIGHT:
                case 39:
                    e.preventDefault();
                    if (focusedCategoryIndex < movieCategories.length - 1) {
                        setFocusedCategoryIndex(prev => prev + 1);
                    }
                    break;

                // Up Arrow - Categories are at the top, nothing above
                case TIZEN_KEYS.ARROW_UP:
                case 38:
                    e.preventDefault();
                    // Already at the top, do nothing (or could go to sidebar)
                    break;

                // Down Arrow - Move to hero (below categories)
                case TIZEN_KEYS.ARROW_DOWN:
                case 40:
                    e.preventDefault();
                    setFocusArea('hero');
                    setHeroFocusedButton('play');
                    heroRef.current?.scrollIntoView({ behavior: 'auto', block: 'center' });
                    break;

                // Enter - Select category
                case TIZEN_KEYS.ENTER:
                case 13:
                    e.preventDefault();
                    const selectedCategory = movieCategories[focusedCategoryIndex];
                    if (selectedCategory) {
                        setActiveCategory(selectedCategory.id);
                    }
                    break;
            }
        }
        // Content rows navigation
        else if (focusArea === 'rows') {
            const currentRow = rows[focusedRowIndex];
            const itemCount = currentRow?.movies.length || 0;

            switch (keyCode) {
                // Left Arrow
                case TIZEN_KEYS.ARROW_LEFT:
                case 37:
                    e.preventDefault();
                    if (focusedItemIndex > 0) {
                        setFocusedItemIndex(prev => prev - 1);
                    } else {
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
                        const newRowItems = rows[focusedRowIndex - 1]?.movies.length || 0;
                        setFocusedItemIndex(prev => Math.min(prev, newRowItems - 1));
                    } else {
                        // At first row, go back to hero (above rows)
                        setFocusArea('hero');
                        setHeroFocusedButton('play');
                        heroRef.current?.scrollIntoView({ behavior: getSmartScrollBehavior(), block: 'center' });
                    }
                    break;

                // Down Arrow
                case TIZEN_KEYS.ARROW_DOWN:
                case 40:
                    e.preventDefault();
                    if (focusedRowIndex < rows.length - 1) {
                        setFocusedRowIndex(prev => prev + 1);
                        const newRowItems = rows[focusedRowIndex + 1]?.movies.length || 0;
                        setFocusedItemIndex(prev => Math.min(prev, newRowItems - 1));
                    }
                    break;

                // Enter - Select/Play
                case TIZEN_KEYS.ENTER:
                case 13:
                    e.preventDefault();
                    if (currentRow) {
                        const movie = currentRow.movies[focusedItemIndex];
                        if (movie) handleMovieClick(movie);
                    }
                    break;
            }
        }
    }, [isFocused, playingMovie, selectedMovie, focusArea, heroFocusedButton, focusedCategoryIndex, focusedRowIndex, focusedItemIndex, rows, movieCategories, setFocusZone, featuredMovie, handlePlayMovie, handleMovieClick]);

    useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);

    // Scroll focused row into view
    useEffect(() => {
        if (focusArea === 'rows' && rowsRef.current) {
            const rowElement = rowsRef.current.querySelector(`[data-row-index="${focusedRowIndex}"]`);
            if (rowElement) {
                rowElement.scrollIntoView({
                    behavior: getSmartScrollBehavior(),
                    block: 'center',
                });
            }
        }
    }, [focusArea, focusedRowIndex]);

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

    // Fullscreen video player
    if (playingMovie) {
        return (
            <VideoPlayer
                url={playingMovie.videoUrl || playingMovie.trailerUrl}
                title={playingMovie.title}
                onClose={() => setPlayingMovie(null)}
                variant="movie"
            />
        );
    }

    return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3 }}
            style={{
                width: '100%',
                height: '100%',
                overflow: 'auto',
            }}
        >
            {/* Category Menu */}
            <div ref={categoriesRef} style={{
                padding: '32px 48px 24px 48px',
                position: 'sticky',
                top: 0,
                zIndex: 20,
                background: 'linear-gradient(180deg, var(--md3-bg-primary) 0%, var(--md3-bg-primary) 80%, transparent 100%)',
            }}>
                <HorizontalCategoryMenu
                    categories={movieCategories}
                    activeCategory={activeCategory}
                    onCategoryChange={setActiveCategory}
                    focusedIndex={isFocused && focusArea === 'categories' ? focusedCategoryIndex : -1}
                />
            </div>

            {/* Hero Section */}
            <div ref={heroRef}>
                {featuredMovie && (
                    <MovieHero
                        movie={featuredMovie}
                        onPlay={handlePlayMovie}
                        onInfo={handleMovieClick}
                        focusedButton={isFocused && focusArea === 'hero' ? heroFocusedButton : null}
                    />
                )}
            </div>

            {/* Movie Rows */}
            <div ref={rowsRef} style={{ padding: '0 48px 48px 48px', marginTop: '32px' }}>
                {rows.map((row, rowIndex) => (
                    <div key={row.id} data-row-index={rowIndex}>
                        <MovieRow
                            title={row.title}
                            movies={row.movies}
                            onMovieClick={handleMovieClick}
                            focusedIndex={isFocused && focusArea === 'rows' && focusedRowIndex === rowIndex ? focusedItemIndex : -1}
                        />
                    </div>
                ))}
            </div>

            {/* Movie Info Modal */}
            <MovieInfoDialog
                movie={selectedMovie}
                onClose={() => setSelectedMovie(null)}
                onPlay={handlePlayMovie}
            />
        </motion.div>
    );
}
