import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { fetchGames, GameItem } from '../api';
import { X, Gamepad2 } from 'lucide-react';
import HorizontalCategoryMenu from '../components/HorizontalCategoryMenu';
import { Category } from '../types';
import { useNavigation } from '../context/NavigationContext';
import { TIZEN_KEYS } from '../utils/tizenKeys';

interface GamesPageProps {
    isFocused?: boolean;
}

const GamesPage = ({ isFocused = false }: GamesPageProps) => {
    const [games, setGames] = useState<GameItem[]>([]);
    const [categories, setCategories] = useState<Category[]>([]);
    const [selectedCategory, setSelectedCategory] = useState("all");
    const [playingGame, setPlayingGame] = useState<GameItem | null>(null);
    const [focusedIndex, setFocusedIndex] = useState(0);

    useEffect(() => {
        const load = async () => {
            const data = await fetchGames();
            setGames(data.items);
            setCategories([
                { id: 'all', name: 'All Games' },
                ...data.categories.map(c => ({ id: c.toLowerCase(), name: c }))
            ]);
        };
        load();
    }, []);

    // Filter Logic
    const filteredGames = selectedCategory === "all"
        ? games
        : games.filter(g => g.category?.toLowerCase() === selectedCategory);

    const { setFocusZone } = useNavigation();

    // Keyboard Navigation
    const handleKeyDown = useCallback((e: KeyboardEvent) => {
        if (!isFocused) return;

        if (playingGame) {
            if (e.key === 'Back' || e.key === 'Escape' || e.key === 'Backspace') {
                setPlayingGame(null);
            }
            return;
        }

        const keyCode = e.keyCode;
        const COLUMNS = 6;

        switch (keyCode) {
            case TIZEN_KEYS.ARROW_RIGHT:
            case 39:
                e.preventDefault();
                if (focusedIndex < filteredGames.length - 1) setFocusedIndex(prev => prev + 1);
                break;
            case TIZEN_KEYS.ARROW_LEFT:
            case 37:
                e.preventDefault();
                if (focusedIndex > 0) {
                    setFocusedIndex(prev => prev - 1);
                } else {
                    setFocusZone('sidebar');
                }
                break;
            case TIZEN_KEYS.ARROW_DOWN:
            case 40:
                e.preventDefault();
                if (focusedIndex + COLUMNS < filteredGames.length) setFocusedIndex(prev => prev + COLUMNS);
                break;
            case TIZEN_KEYS.ARROW_UP:
            case 38:
                e.preventDefault();
                if (focusedIndex - COLUMNS >= 0) setFocusedIndex(prev => prev - COLUMNS);
                break;
            case TIZEN_KEYS.ENTER:
            case 13:
                e.preventDefault();
                if (filteredGames[focusedIndex]) {
                    setPlayingGame(filteredGames[focusedIndex]);
                }
                break;
        }
    }, [isFocused, filteredGames, focusedIndex, playingGame, setFocusZone]);

    useEffect(() => {
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [handleKeyDown]);

    if (playingGame) {
        return (
            <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                style={{
                    position: 'fixed',
                    inset: 0,
                    zIndex: 1000,
                    backgroundColor: 'black',
                    display: 'flex',
                    flexDirection: 'column'
                }}
            >
                {/* Header Bar */}
                <div style={{
                    padding: '16px 24px',
                    backgroundColor: 'var(--md3-bg-primary)',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    borderBottom: '1px solid var(--md3-border-color)',
                }}>
                    <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                        <motion.button
                            onClick={() => setPlayingGame(null)}
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.9 }}
                            style={{
                                background: 'var(--md3-bg-secondary)',
                                border: 'none',
                                color: 'white',
                                cursor: 'pointer',
                                padding: '8px',
                                borderRadius: '8px',
                                display: 'flex',
                            }}
                        >
                            <X size={24} />
                        </motion.button>
                        <span style={{ fontWeight: 600, fontSize: '18px' }}>{playingGame.name}</span>
                    </div>
                </div>

                {/* Game Iframe */}
                <iframe
                    src={playingGame.gameUrl}
                    title={playingGame.name}
                    style={{ flex: 1, border: 'none', width: '100%', height: '100%' }}
                    allow="autoplay; fullscreen; gamepad"
                />
            </motion.div>
        );
    }

    return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            style={{
                padding: '64px 48px',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                overflow: 'hidden',
            }}
        >
            {/* Header */}
            <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                marginBottom: '24px'
            }}>
                <Gamepad2 size={32} color="var(--md3-surface-active)" />
                <h1 style={{
                    fontSize: '28px',
                    fontWeight: 600,
                    margin: 0,
                }}>
                    Games Arcade
                </h1>
            </div>

            {/* Categories */}
            <div style={{ marginBottom: '24px' }}>
                <HorizontalCategoryMenu
                    categories={categories}
                    activeCategory={selectedCategory}
                    onCategoryChange={(cat) => { setSelectedCategory(cat); setFocusedIndex(0); }}
                />
            </div>

            {/* Grid */}
            <div style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                gap: '16px',
                overflowY: 'auto',
                flex: 1,
                paddingBottom: '24px',
            }}>
                {filteredGames.map((game, idx) => (
                    <motion.div
                        key={game.id}
                        onClick={() => setPlayingGame(game)}
                        initial={{ opacity: 0, scale: 0.95 }}
                        animate={{ opacity: 1, scale: 1 }}
                        transition={{ delay: idx * 0.02 }}
                        whileHover={{ scale: 1.03 }}
                        whileTap={{ scale: 0.98 }}
                        style={{
                            position: 'relative',
                            aspectRatio: '1/1',
                            borderRadius: 'var(--md3-border-radius)',
                            overflow: 'hidden',
                            cursor: 'pointer',
                            backgroundImage: `url(${game.imageUrl})`,
                            backgroundSize: 'cover',
                            backgroundPosition: 'center',
                            border: focusedIndex === idx ? '3px solid var(--md3-surface-active)' : '1px solid var(--md3-border-color)',
                            boxShadow: focusedIndex === idx ? 'var(--md3-shadow-elevation-2)' : 'var(--md3-shadow-elevation-1)',
                        }}
                    >
                        {/* Overlay */}
                        <div style={{
                            position: 'absolute',
                            bottom: 0,
                            left: 0,
                            right: 0,
                            padding: '12px',
                            background: 'linear-gradient(to top, rgba(0,0,0,0.9), transparent)',
                        }}>
                            <p style={{
                                color: 'white',
                                textAlign: 'center',
                                fontWeight: 600,
                                fontSize: '14px',
                                margin: 0,
                            }}>
                                {game.name}
                            </p>
                        </div>
                    </motion.div>
                ))}
            </div>
        </motion.div>
    );
};

export default GamesPage;
