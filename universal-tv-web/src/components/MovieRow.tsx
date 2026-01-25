import { useRef, useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Movie } from '../types';
import MovieCard from './MovieCard';
import { getSmartScrollBehavior } from '../utils/smartScroll';

interface MovieRowProps {
    title: string;
    movies: Movie[];
    onMovieClick: (movie: Movie) => void;
    focusedIndex?: number;
}

export default function MovieRow({ title, movies, onMovieClick, focusedIndex = -1 }: MovieRowProps) {
    const scrollRef = useRef<HTMLDivElement>(null);

    // Auto-scroll focused item into view
    useEffect(() => {
        if (focusedIndex >= 0 && scrollRef.current) {
            const items = scrollRef.current.children;
            if (items[focusedIndex]) {
                (items[focusedIndex] as HTMLElement).scrollIntoView({
                    behavior: getSmartScrollBehavior(),
                    block: 'nearest',
                    inline: 'center'
                });
            }
        }
    }, [focusedIndex]);

    if (movies.length === 0) return null;

    return (
        <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.6, ease: [0.45, 0, 0.55, 1] }}
            style={{ marginBottom: '40px' }}
        >
            {/* Title */}
            <h2 style={{
                fontSize: '24px',
                fontWeight: 600,
                color: 'var(--md3-text-primary)',
                marginBottom: '24px',
            }}>
                {title}
            </h2>

            {/* Row Container */}
            <div style={{ position: 'relative' }}>
                <div
                    ref={scrollRef}
                    className="scrollbar-hide"
                    style={{
                        display: 'flex',
                        gap: '22px', // Slightly more than Home (20px)
                        overflowX: 'auto',
                        paddingBottom: '24px', // Safe space for focus scaling
                        paddingTop: '12px', // Slight breathing room
                        paddingLeft: '48px', // Start with offset
                        paddingRight: '48px',
                        scrollbarWidth: 'none',
                        msOverflowStyle: 'none',
                    }}
                >
                    {movies.map((movie, index) => (
                        <div
                            key={movie.id}
                            style={{
                                flexShrink: 0,
                                transform: focusedIndex === index ? 'scale(1.05)' : 'scale(1)',
                                transition: 'transform 0.3s cubic-bezier(0.2, 0, 0, 1)',
                                zIndex: focusedIndex === index ? 10 : 1,
                            }}
                        >
                            <MovieCard
                                movie={movie}
                                index={index}
                                onClick={() => onMovieClick(movie)}
                                isFocused={focusedIndex === index}
                            />
                        </div>
                    ))}
                </div>
            </div>
        </motion.div>
    );
}
