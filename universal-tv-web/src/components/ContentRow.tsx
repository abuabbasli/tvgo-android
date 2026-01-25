import { ReactNode } from 'react';
import { motion } from 'framer-motion';
import { ChevronRight } from 'lucide-react';

interface ContentRowProps {
    title: string;
    children: ReactNode;
    onSeeAll?: () => void;
    isSeeAllFocused?: boolean;
}

export default function ContentRow({ title, children, onSeeAll, isSeeAllFocused = false }: ContentRowProps) {
    return (
        <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.6, ease: [0.45, 0, 0.55, 1] }}
            style={{ marginBottom: '40px' }}
        >
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                marginBottom: '24px',
            }}>
                <h2 style={{
                    fontSize: '24px',
                    fontWeight: 600,
                    color: 'var(--md3-text-primary)',
                    margin: 0,
                }}>
                    {title}
                </h2>
                {onSeeAll && (
                    <motion.button
                        whileHover={{ scale: 1.03, x: 4 }}
                        whileTap={{ scale: 0.97 }}
                        onClick={onSeeAll}
                        style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '8px',
                            padding: '8px 16px',
                            background: isSeeAllFocused ? 'var(--md3-surface-active)' : 'var(--md3-bg-secondary)',
                            border: isSeeAllFocused ? '2px solid #FFFFFF' : '1px solid var(--md3-border-color)',
                            borderRadius: 'var(--md3-border-radius)',
                            color: isSeeAllFocused ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                            cursor: 'pointer',
                            fontSize: '14px',
                            fontWeight: isSeeAllFocused ? 600 : 400,
                            transition: 'all 0.2s ease',
                            transform: isSeeAllFocused ? 'scale(1.05)' : 'scale(1)',
                            boxShadow: isSeeAllFocused ? '0 0 0 2px rgba(255, 255, 255, 0.2)' : 'none',
                        }}
                    >
                        See All
                        <ChevronRight size={18} />
                    </motion.button>
                )}
            </div>
            <div
                className="scrollbar-hide"
                style={{
                    overflowX: 'auto',
                    overflowY: 'hidden',
                    paddingBottom: '16px',
                    scrollbarWidth: 'none',
                    msOverflowStyle: 'none',
                }}
            >
                <div style={{
                    display: 'flex',
                    gap: '20px',
                }}>
                    {children}
                </div>
            </div>
        </motion.div>
    );
}
