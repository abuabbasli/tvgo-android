import { motion } from 'framer-motion';
import { useState, useRef, useEffect } from 'react';
import { getSmartScrollBehavior } from '../utils/smartScroll';
import { Category } from '../types';
import {
    Menu,
    Smile,
    Heart,
    Film as FilmIcon,
    Music,
    Baby,
    Trophy,
    Newspaper,
    Swords,
    Laugh,
    Drama,
    Sparkles,
    Zap,
    Ghost,
    HeartHandshake
} from 'lucide-react';

interface HorizontalCategoryMenuProps {
    categories: Category[];
    activeCategory: string;
    onCategoryChange: (categoryId: string) => void;
    focusedIndex?: number;
}

// Map category IDs to icons (Material Design style)
const categoryIcons: Record<string, React.ElementType> = {
    all: Menu,
    favorites: Heart,
    kids: Baby,
    sports: Trophy,
    news: Newspaper,
    entertainment: Smile,
    movies: FilmIcon,
    music: Music,
    // Movie categories
    action: Swords,
    comedy: Laugh,
    drama: Drama,
    scifi: Sparkles,
    thriller: Zap,
    horror: Ghost,
    romance: HeartHandshake,
};

interface CategoryChipProps {
    category: Category;
    index: number;
    isActive: boolean;
    isFocusedByKeyboard?: boolean;
    onCategoryChange: (id: string) => void;
}

function CategoryChip({ category, index, isActive, isFocusedByKeyboard = false, onCategoryChange }: CategoryChipProps) {
    const [isFocused, setIsFocused] = useState(false);
    const IconComponent = categoryIcons[category.id] || Menu;
    const showFocusRing = isFocusedByKeyboard || (isFocused && !isActive);

    return (
        <motion.button
            onClick={() => onCategoryChange(category.id)}
            onFocus={() => setIsFocused(true)}
            onBlur={() => setIsFocused(false)}
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{
                delay: index * 0.03,
                duration: 0.3,
                ease: [0.2, 0, 0, 1]
            }}
            whileHover={{ scale: 1.02 }}
            whileTap={{ scale: 0.98 }}
            className="tv-focusable"
            style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                flexShrink: 0,
                height: '56px',
                padding: '0 28px',
                borderRadius: '28px',
                border: '1px solid var(--md3-border-color)',
                background: isActive ? 'var(--md3-surface-active)' : 'var(--md3-bg-secondary)',
                cursor: 'pointer',
                outline: showFocusRing ? '2px solid var(--md3-surface-active)' : 'none',
                outlineOffset: '2px',
                transform: isFocusedByKeyboard ? 'scale(1.05)' : 'none',
                transition: 'all 0.2s cubic-bezier(0.2, 0, 0, 1)',
            }}
        >
            <IconComponent
                size={24}
                strokeWidth={2}
                style={{
                    flexShrink: 0,
                    color: isActive ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                }}
            />
            <span
                style={{
                    fontSize: '16px',
                    fontWeight: 500,
                    lineHeight: '24px',
                    color: isActive ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                    whiteSpace: 'nowrap',
                    fontFamily: "'Inter', 'Roboto', sans-serif",
                }}
            >
                {category.name}
            </span>
        </motion.button>
    );
}

export default function HorizontalCategoryMenu({
    categories,
    activeCategory,
    onCategoryChange,
    focusedIndex = -1
}: HorizontalCategoryMenuProps) {
    const scrollRef = useRef<HTMLDivElement>(null);

    // Auto-scroll focused item into view
    useEffect(() => {
        if (focusedIndex >= 0 && scrollRef.current) {
            const items = scrollRef.current.children;
            // The first child is the <style> tag if we use index directly, 
            // but we need to account for it or use a query.
            // Actually, the style tag is a child. Let's use querySelectorAll or adjust index.
            const chips = scrollRef.current.querySelectorAll('button');
            if (chips[focusedIndex]) {
                chips[focusedIndex].scrollIntoView({
                    behavior: getSmartScrollBehavior(),
                    block: 'nearest',
                    inline: 'center'
                });
            }
        }
    }, [focusedIndex]);

    return (
        <motion.div
            ref={scrollRef}
            initial={{ opacity: 0, y: -10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{
                duration: 0.3,
                ease: [0.2, 0, 0, 1]
            }}
            className="horizontal-category-scroll"
            style={{
                display: 'flex',
                gap: '12px',
                overflowX: 'auto',
                paddingBottom: '16px',
                paddingTop: '8px',
                paddingLeft: '12px',
                paddingRight: '12px',
                background: 'transparent',
            }}
        >
            <style>{`
                .horizontal-category-scroll::-webkit-scrollbar {
                    height: 6px;
                }
                .horizontal-category-scroll::-webkit-scrollbar-track {
                    background: rgba(255, 255, 255, 0.03);
                    border-radius: 10px;
                }
                .horizontal-category-scroll::-webkit-scrollbar-thumb {
                    background: rgba(225, 227, 224, 0.2);
                    border-radius: 10px;
                }
                .horizontal-category-scroll::-webkit-scrollbar-thumb:hover {
                    background: rgba(225, 227, 224, 0.3);
                }
            `}</style>
            {categories.map((category, index) => (
                <CategoryChip
                    key={category.id}
                    category={category}
                    index={index}
                    isActive={activeCategory === category.id}
                    isFocusedByKeyboard={focusedIndex === index}
                    onCategoryChange={onCategoryChange}
                />
            ))}
        </motion.div>
    );
}
