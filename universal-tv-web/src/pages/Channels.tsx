import { useState, useEffect, useMemo, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { fetchChannels } from '../api';
import { Channel } from '../types';
import ChannelCard from '../components/ChannelCard';
import ChannelListItem from '../components/ChannelListItem';
import HorizontalCategoryMenu from '../components/HorizontalCategoryMenu';
import StaticPreviewPlayer from '../components/StaticPreviewPlayer';
import VideoPlayer from '../components/VideoPlayer';
import { TIZEN_KEYS } from '../utils/tizenKeys';
import { useNavigation } from '../context/NavigationContext';
import { useConfig } from '../context/ConfigContext';
import { getSmartScrollBehavior } from '../utils/smartScroll';

interface ChannelsPageProps {
    viewMode: 'grid' | 'list';
    isFocused?: boolean;
}

const getFilteredChannels = (channels: Channel[], categoryId: string) => {
    if (categoryId === 'all') return channels;
    if (categoryId === 'favorites') return channels.filter(ch => ch.isFavorite);
    return channels.filter(ch =>
        ch.category?.toLowerCase() === categoryId.toLowerCase()
    );
};

export default function ChannelsPage({ viewMode, isFocused = false }: ChannelsPageProps) {
    const { channelCategories } = useConfig();
    const [channels, setChannels] = useState<Channel[]>([]);
    const [loading, setLoading] = useState(true);
    const [activeCategory, setActiveCategory] = useState('all');
    const [selectedChannel, setSelectedChannel] = useState<Channel | null>(null);
    const [previewChannel, setPreviewChannel] = useState<Channel | null>(null); // Channel being previewed (separate from keyboard focus)
    const [playingChannel, setPlayingChannel] = useState<Channel | null>(null);
    const [focusedIndex, setFocusedIndex] = useState(0);
    const [focusArea, setFocusArea] = useState<'categories' | 'channels' | 'preview'>('channels');
    const [previewActive, setPreviewActive] = useState(false); // Whether preview is actively playing
    const [focusedCategoryIndex, setFocusedCategoryIndex] = useState(0);
    const [numberBuffer, setNumberBuffer] = useState('');
    const [isNotFound, setIsNotFound] = useState(false);
    const numberTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const channelListRef = useRef<HTMLDivElement>(null);

    // Load channels from API (categories come from ConfigContext)
    useEffect(() => {
        const loadData = async () => {
            setLoading(true);

            const channelsData = await fetchChannels();

            // Load favorites from localStorage
            const savedFavorites = localStorage.getItem('tvgo-favorites');
            const favoriteIds: string[] = savedFavorites ? JSON.parse(savedFavorites) : [];

            const channelsWithFavorites = channelsData.map(ch => ({
                ...ch,
                isFavorite: favoriteIds.includes(ch.id)
            }));

            setChannels(channelsWithFavorites);

            // Auto-select first channel
            if (channelsWithFavorites.length > 0) {
                setSelectedChannel(channelsWithFavorites[0]);
                setPreviewChannel(channelsWithFavorites[0]); // Also set preview channel initially
                setFocusedIndex(0);
            }

            setLoading(false);
        };
        loadData();
    }, []);

    // Reset buffer on mount or when page becomes active
    useEffect(() => {
        setNumberBuffer('');
        setIsNotFound(false);
        if (numberTimeoutRef.current) {
            clearTimeout(numberTimeoutRef.current);
        }
    }, []);

    // Filter channels based on category
    const filteredChannels = useMemo(() => {
        return getFilteredChannels(channels, activeCategory);
    }, [channels, activeCategory]);

    const { setFocusZone } = useNavigation();

    // Handle category change - defined before keyboard handler that uses it
    const handleCategoryChange = useCallback((categoryId: string) => {
        setActiveCategory(categoryId);
        setFocusedIndex(0);
        setFocusArea('channels');

        // Auto-select first channel in new category
        const newChannels = getFilteredChannels(channels, categoryId);
        if (newChannels.length > 0) {
            setSelectedChannel(newChannels[0]);
        }
    }, [channels]);

    // Toggle favorite - defined before keyboard handler that uses it
    const toggleFavorite = useCallback((channelId: string) => {
        setChannels(prev => {
            const updated = prev.map(ch =>
                ch.id === channelId ? { ...ch, isFavorite: !ch.isFavorite } : ch
            );

            // Save to localStorage
            const favoriteIds = updated.filter(ch => ch.isFavorite).map(ch => ch.id);
            localStorage.setItem('tvgo-favorites', JSON.stringify(favoriteIds));

            return updated;
        });

        // Update selected channel if it's the one being toggled
        setSelectedChannel(prev => {
            if (prev?.id === channelId) {
                return { ...prev, isFavorite: !prev.isFavorite };
            }
            return prev;
        });
    }, []);

    // Keyboard Navigation for TV Remote
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            // Only handle when focused
            if (!isFocused) return;
            // Don't handle keys if fullscreen player is open
            if (playingChannel) return;

            const keyCode = e.keyCode;
            const isGrid = viewMode === 'grid';
            const cols = isGrid ? 2 : 1;

            // Handle number keys (0-9) for direct channel navigation
            // Number keys: 48-57 (top row) or 96-105 (numpad)
            const isNumberKey = (keyCode >= 48 && keyCode <= 57) || (keyCode >= 96 && keyCode <= 105);
            if (isNumberKey) {
                e.preventDefault();
                // Get the number pressed
                const num = keyCode >= 96 ? keyCode - 96 : keyCode - 48;

                // Clear previous timeout
                if (numberTimeoutRef.current) {
                    clearTimeout(numberTimeoutRef.current);
                }

                // Append to buffer
                setNumberBuffer(prev => {
                    // If we were in "Not Found" state, start fresh with the new number
                    if (isNotFound) {
                        setIsNotFound(false);
                        return num.toString();
                    }

                    const newBuffer = prev + num.toString();
                    setIsNotFound(false); // Reset not found when typing

                    // REMOVED: Immediate switching logic
                    // We now wait for timeout or Enter key

                    return newBuffer;
                });

                // Clear buffer after 1.5 seconds of no input
                numberTimeoutRef.current = setTimeout(() => {
                    setNumberBuffer(currentBuffer => {
                        const targetOrder = parseInt(currentBuffer, 10);

                        // Find channel with matching order in the full channel list (not filtered)
                        const channelByOrder = channels.find(ch => ch.order === targetOrder);

                        if (channelByOrder) {
                            // Find index in filtered list
                            const indexInFiltered = filteredChannels.findIndex(ch => ch.id === channelByOrder.id);
                            if (indexInFiltered >= 0) {
                                setFocusedIndex(indexInFiltered);
                                setSelectedChannel(channelByOrder);
                                setPreviewActive(true); // Start preview when channel selected by number
                                setFocusArea('channels');
                                return ''; // Clear buffer on success
                            } else {
                                // Channel exists but not in current filter - switch to 'all' category
                                setActiveCategory('all');
                                // After category change, we need to find index in full list
                                const indexInAll = channels.findIndex(ch => ch.id === channelByOrder.id);
                                if (indexInAll >= 0) {
                                    setFocusedIndex(indexInAll);
                                    setSelectedChannel(channelByOrder);
                                    setPreviewActive(true); // Start preview when channel selected by number
                                    setFocusArea('channels');
                                    return ''; // Clear buffer on success
                                }
                            }
                        }

                        if (!channelByOrder && currentBuffer !== '') {
                            setIsNotFound(true);
                            // Keep the buffer visible for 0.5 more seconds to show "Not Found"
                            numberTimeoutRef.current = setTimeout(() => {
                                setNumberBuffer('');
                                setIsNotFound(false);
                            }, 500); // Reduced to 500ms for faster clearance
                            return currentBuffer;
                        }

                        return '';
                    });
                }, 2000); // 2s wait for input completion

                return;
            }

            // Handle preview focus navigation
            if (focusArea === 'preview') {
                switch (keyCode) {
                    case TIZEN_KEYS.ARROW_LEFT:
                    case 37:
                        e.preventDefault();
                        // Go back to channels
                        setFocusArea('channels');
                        break;
                    case TIZEN_KEYS.ENTER:
                    case 13:
                        e.preventDefault();
                        // Play fullscreen
                        if (selectedChannel) {
                            setPlayingChannel(selectedChannel);
                        }
                        break;
                    case TIZEN_KEYS.BACK:
                    case 10009:
                    case 27: // Escape
                        e.preventDefault();
                        // Go back to channels
                        setFocusArea('channels');
                        break;
                    case TIZEN_KEYS.PLAY:
                    case TIZEN_KEYS.PLAY_PAUSE:
                        e.preventDefault();
                        if (selectedChannel) {
                            setPlayingChannel(selectedChannel);
                        }
                        break;
                }
                return;
            }

            // Handle category navigation
            if (focusArea === 'categories') {
                switch (keyCode) {
                    case TIZEN_KEYS.ARROW_LEFT:
                    case 37:
                        e.preventDefault();
                        if (focusedCategoryIndex > 0) {
                            setFocusedCategoryIndex(prev => prev - 1);
                        } else {
                            setFocusZone('sidebar');
                        }
                        break;
                    case TIZEN_KEYS.ARROW_RIGHT:
                    case 39:
                        e.preventDefault();
                        if (focusedCategoryIndex < channelCategories.length - 1) {
                            setFocusedCategoryIndex(prev => prev + 1);
                        }
                        break;
                    case TIZEN_KEYS.ARROW_DOWN:
                    case 40:
                        e.preventDefault();
                        setFocusArea('channels');
                        break;
                    case TIZEN_KEYS.ENTER:
                    case 13:
                        e.preventDefault();
                        if (channelCategories[focusedCategoryIndex]) {
                            handleCategoryChange(channelCategories[focusedCategoryIndex].id);
                        }
                        break;
                }
                return;
            }

            // Helper to safe update index - only updates focus, not preview
            const updateFocus = (change: number) => {
                const newIndex = Math.max(0, Math.min(filteredChannels.length - 1, focusedIndex + change));
                if (newIndex !== focusedIndex && filteredChannels[newIndex]) {
                    setFocusedIndex(newIndex);
                    setSelectedChannel(filteredChannels[newIndex]);
                    // Only set preview channel if no preview is currently active
                    if (!previewActive) {
                        setPreviewChannel(filteredChannels[newIndex]);
                    }
                    setFocusArea('channels');
                }
            };

            switch (keyCode) {
                // Left Arrow
                case TIZEN_KEYS.ARROW_LEFT:
                case 37: // Standard ArrowLeft
                    e.preventDefault();
                    if (isGrid) {
                        // Check if at first column (index % cols === 0)
                        if (focusedIndex % cols === 0) {
                            setFocusZone('sidebar');
                        } else {
                            updateFocus(-1);
                        }
                    } else {
                        // List mode - always go to sidebar on left
                        setFocusZone('sidebar');
                    }
                    break;

                // Right Arrow
                case TIZEN_KEYS.ARROW_RIGHT:
                case 39: // Standard ArrowRight
                    e.preventDefault();
                    if (isGrid) updateFocus(1);
                    break;

                // Up Arrow
                case TIZEN_KEYS.ARROW_UP:
                case 38: // Standard ArrowUp
                    e.preventDefault();
                    // Check if at top row
                    if (focusedIndex < cols) {
                        // Move to categories
                        setFocusArea('categories');
                    } else {
                        updateFocus(-cols);
                    }
                    break;

                // Down Arrow
                case TIZEN_KEYS.ARROW_DOWN:
                case 40: // Standard ArrowDown
                    e.preventDefault();
                    updateFocus(cols);
                    break;

                // Enter - Play
                case TIZEN_KEYS.ENTER:
                case 13: // Standard Enter
                    e.preventDefault();

                    // If we have a number buffer, try to confirm selection
                    if (numberBuffer) {
                        const targetOrder = parseInt(numberBuffer, 10);
                        const channelByOrder = channels.find(ch => ch.order === targetOrder);

                        if (channelByOrder) {
                            // Logic to select channel (similar to timeout logic)
                            // Find index in filtered list
                            const indexInFiltered = filteredChannels.findIndex(ch => ch.id === channelByOrder.id);
                            if (indexInFiltered >= 0) {
                                setFocusedIndex(indexInFiltered);
                                setSelectedChannel(channelByOrder);
                                setPreviewChannel(channelByOrder); // Set preview channel when selected by number
                                setPreviewActive(true); // Start preview when channel selected by number
                                setFocusArea('channels');
                            } else {
                                setActiveCategory('all');
                                const indexInAll = channels.findIndex(ch => ch.id === channelByOrder.id);
                                if (indexInAll >= 0) {
                                    setFocusedIndex(indexInAll);
                                    setSelectedChannel(channelByOrder);
                                    setPreviewChannel(channelByOrder); // Set preview channel when selected by number
                                    setPreviewActive(true); // Start preview when channel selected by number
                                    setFocusArea('channels');
                                }
                            }
                            setNumberBuffer('');
                            setIsNotFound(false);
                            if (numberTimeoutRef.current) clearTimeout(numberTimeoutRef.current);
                        } else {
                            // Not found
                            setIsNotFound(true);
                            if (numberTimeoutRef.current) clearTimeout(numberTimeoutRef.current);
                            numberTimeoutRef.current = setTimeout(() => {
                                setNumberBuffer('');
                                setIsNotFound(false);
                            }, 500); // Reduced to 500ms for consistency
                        }
                        return;
                    }

                    if (selectedChannel) {
                        if (previewActive && previewChannel?.id === selectedChannel.id) {
                            // Second click on same channel - go to fullscreen
                            setPlayingChannel(selectedChannel);
                        } else {
                            // First click or different channel - activate preview for this channel
                            setPreviewChannel(selectedChannel);
                            setPreviewActive(true);
                        }
                    }
                    break;

                // Channel Up/Down buttons
                case TIZEN_KEYS.CHANNEL_UP:
                    e.preventDefault();
                    updateFocus(-1);
                    break;
                case TIZEN_KEYS.CHANNEL_DOWN:
                    e.preventDefault();
                    updateFocus(1);
                    break;

                // Play/Pause button
                case TIZEN_KEYS.PLAY:
                case TIZEN_KEYS.PLAY_PAUSE:
                    e.preventDefault();
                    if (selectedChannel) {
                        setPlayingChannel(selectedChannel);
                    }
                    break;

                // 'F' key - Toggle favorite on selected channel
                case 70: // 'F' key
                    e.preventDefault();
                    if (selectedChannel) {
                        toggleFavorite(selectedChannel.id);
                    }
                    break;
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [filteredChannels, channels, selectedChannel, playingChannel, previewActive, previewChannel, viewMode, isFocused, focusedIndex, setFocusZone, focusArea, focusedCategoryIndex, channelCategories, handleCategoryChange, toggleFavorite]);

    // Scroll focused channel into view
    useEffect(() => {
        if (channelListRef.current && filteredChannels.length > 0) {
            const channelElements = channelListRef.current.querySelectorAll('[data-channel-item]');
            if (channelElements[focusedIndex]) {
                channelElements[focusedIndex].scrollIntoView({
                    behavior: 'auto',
                    block: 'nearest',
                });
            }
        }
    }, [focusedIndex, filteredChannels.length]);




    // Handle channel click - Update to PLAY on click
    const handleChannelClick = useCallback((channel: Channel, index: number) => {
        setSelectedChannel(channel);
        setFocusedIndex(index);
        setPlayingChannel(channel); // Play immediately on click
    }, []);

    // Handle play fullscreen
    const handlePlayFullscreen = useCallback((channel: Channel) => {
        setPlayingChannel(channel);
    }, []);

    // Handle channel switching in fullscreen player
    const handleNextChannel = useCallback(() => {
        if (!playingChannel) return;
        const currentIndex = filteredChannels.findIndex(ch => ch.id === playingChannel.id);
        if (currentIndex !== -1) {
            const nextIndex = (currentIndex + 1) % filteredChannels.length;
            const nextChannel = filteredChannels[nextIndex];
            setPlayingChannel(nextChannel);
            setSelectedChannel(nextChannel);
            setFocusedIndex(nextIndex);
        }
    }, [playingChannel, filteredChannels]);

    const handlePrevChannel = useCallback(() => {
        if (!playingChannel) return;
        const currentIndex = filteredChannels.findIndex(ch => ch.id === playingChannel.id);
        if (currentIndex !== -1) {
            const prevIndex = (currentIndex - 1 + filteredChannels.length) % filteredChannels.length;
            const prevChannel = filteredChannels[prevIndex];
            setPlayingChannel(prevChannel);
            setSelectedChannel(prevChannel);
            setFocusedIndex(prevIndex);
        }
    }, [playingChannel, filteredChannels]);

    const handleChannelSelect = useCallback((channel: Channel) => {
        setPlayingChannel(channel);
        setSelectedChannel(channel);

        // Find index in filtered list
        const indexInFiltered = filteredChannels.findIndex(ch => ch.id === channel.id);
        if (indexInFiltered >= 0) {
            setFocusedIndex(indexInFiltered);
        } else {
            // Not in current filter, switch to 'all'
            setActiveCategory('all');
            const indexInAll = channels.findIndex(ch => ch.id === channel.id);
            if (indexInAll >= 0) {
                setFocusedIndex(indexInAll);
            }
        }
    }, [filteredChannels, channels]);

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
    if (playingChannel) {
        return (
            <VideoPlayer
                url={playingChannel.streamUrl}
                title={playingChannel.name}
                onClose={() => setPlayingChannel(null)}
                onNextChannel={handleNextChannel}
                onPrevChannel={handlePrevChannel}
                onChannelSelect={handleChannelSelect}
                variant="tv"
                channel={playingChannel}
                channels={channels}
            />
        );
    }

    return (
        <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.3, ease: [0, 0, 0, 1] }}
            style={{
                width: '100%',
                height: '100%',
                display: 'flex',
                flexDirection: 'column',
                padding: '64px 48px 24px 48px',
                overflow: 'auto',
            }}
        >
            {/* Category Menu */}
            <div style={{ marginBottom: '32px', marginTop: '8px' }}>
                <HorizontalCategoryMenu
                    categories={channelCategories}
                    activeCategory={activeCategory}
                    onCategoryChange={handleCategoryChange}
                    focusedIndex={focusArea === 'categories' && isFocused ? focusedCategoryIndex : -1}
                />
            </div>

            {/* Main Content - Split Layout */}
            <div style={{
                flex: 1,
                display: 'grid',
                gridTemplateColumns: '1fr 1fr',
                gap: '24px',
                overflow: 'hidden',
            }}>
                {/* Channel Grid/List */}
                <div ref={channelListRef} style={{
                    overflow: 'auto',
                    paddingRight: '16px',
                }}>
                    {filteredChannels.length === 0 ? (
                        <motion.div
                            initial={{ opacity: 0, scale: 0.95 }}
                            animate={{ opacity: 1, scale: 1 }}
                            style={{
                                height: '100%',
                                display: 'flex',
                                flexDirection: 'column',
                                alignItems: 'center',
                                justifyContent: 'center',
                                padding: '32px',
                            }}
                        >
                            <h3 style={{
                                color: 'var(--md3-text-primary)',
                                marginBottom: '8px',
                            }}>
                                {activeCategory === 'favorites' ? 'No Favorites Yet' : 'No Channels'}
                            </h3>
                            <p style={{
                                color: 'var(--md3-text-secondary)',
                                textAlign: 'center',
                            }}>
                                {activeCategory === 'favorites'
                                    ? 'Click the star icon on any channel to add it to your favorites'
                                    : 'No channels found in this category'}
                            </p>
                        </motion.div>
                    ) : viewMode === 'grid' ? (
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(2, 1fr)',
                            gap: '12px',
                            paddingBottom: '24px',
                        }}>
                            {filteredChannels.map((channel, index) => (
                                <div key={channel.id} data-channel-item>
                                    <ChannelCard
                                        channel={channel}
                                        index={index}
                                        onClick={() => handleChannelClick(channel, index)}
                                        onToggleFavorite={toggleFavorite}
                                        isSelected={selectedChannel?.id === channel.id}
                                        isFocused={focusArea === 'channels' && focusedIndex === index && isFocused}
                                    />
                                </div>
                            ))}
                        </div>
                    ) : (
                        <div style={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '8px',
                            paddingBottom: '24px',
                        }}>
                            {filteredChannels.map((channel, index) => (
                                <div key={channel.id} data-channel-item>
                                    <ChannelListItem
                                        channel={channel}
                                        index={index}
                                        onClick={() => handleChannelClick(channel, index)}
                                        onToggleFavorite={toggleFavorite}
                                        isSelected={selectedChannel?.id === channel.id}
                                        isFocused={focusArea === 'channels' && focusedIndex === index && isFocused}
                                        isPlaying={previewActive && previewChannel?.id === channel.id}
                                    />
                                </div>
                            ))}
                        </div>
                    )}
                </div>

                {/* Preview Player - Always visible, but only plays when activated */}
                {/* Uses previewChannel (not selectedChannel) so navigation doesn't change the preview */}
                <div style={{ overflow: 'hidden' }}>
                    {previewChannel && (
                        <StaticPreviewPlayer
                            key={previewChannel.id}
                            channel={previewChannel}
                            onClose={() => setPreviewChannel(null)}
                            onPlayFullscreen={handlePlayFullscreen}
                            onToggleFavorite={toggleFavorite}
                            autoPlay={previewActive}
                        />
                    )}
                </div>
            </div>

            {/* Number Input Overlay */}
            <AnimatePresence>
                {numberBuffer && (
                    <motion.div
                        initial={{ opacity: 0, scale: 0.5, y: -20 }}
                        animate={{ opacity: 1, scale: 1, y: 0 }}
                        exit={{ opacity: 0, scale: 0.5, y: -20 }}
                        style={{
                            position: 'fixed',
                            top: '32px',
                            right: '32px',
                            background: 'rgba(0, 0, 0, 0.85)',
                            backdropFilter: 'blur(12px)',
                            padding: '16px 24px',
                            borderRadius: '16px',
                            border: '1px solid rgba(255, 255, 255, 0.15)',
                            display: 'flex',
                            alignItems: 'center',
                            gap: '16px',
                            zIndex: 100,
                            boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)',
                            minWidth: isNotFound ? '240px' : '120px',
                        }}
                    >
                        {/* Channel Number */}
                        <div style={{ display: 'flex', alignItems: 'baseline', gap: '12px' }}>
                            <span style={{
                                fontSize: '48px',
                                fontWeight: 700,
                                color: isNotFound ? '#ef4444' : '#FFFFFF',
                                fontFamily: 'monospace',
                                letterSpacing: '2px',
                                lineHeight: 1,
                            }}>
                                {numberBuffer}
                            </span>
                            {isNotFound && (
                                <span style={{
                                    fontSize: '24px',
                                    fontWeight: 600,
                                    color: '#ef4444',
                                    textTransform: 'uppercase',
                                    letterSpacing: '1px'
                                }}>
                                    Not Found
                                </span>
                            )}
                        </div>

                        {/* Channel Name Preview */}
                        {(() => {
                            const match = channels.find(c => c.order === parseInt(numberBuffer, 10));
                            if (match) {
                                return (
                                    <div style={{
                                        display: 'flex',
                                        flexDirection: 'column',
                                        borderLeft: '2px solid rgba(255, 255, 255, 0.2)',
                                        paddingLeft: '16px',
                                        height: '48px',
                                        justifyContent: 'center',
                                    }}>
                                        <span style={{
                                            fontSize: '18px',
                                            fontWeight: 600,
                                            color: '#FFFFFF',
                                            whiteSpace: 'nowrap',
                                        }}>
                                            {match.name}
                                        </span>
                                        {match.category && (
                                            <span style={{
                                                fontSize: '12px',
                                                color: 'rgba(255, 255, 255, 0.6)',
                                                marginTop: '2px',
                                            }}>
                                                {match.category}
                                            </span>
                                        )}
                                    </div>
                                );
                            }
                            return null;
                        })()}
                    </motion.div>
                )}
            </AnimatePresence>
        </motion.div>
    );
}
