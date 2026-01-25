import { useEffect, useRef, useCallback, useState } from 'react';
import shaka from 'shaka-player/dist/shaka-player.ui';
import 'shaka-player/dist/controls.css';
import { TIZEN_KEYS, isBackKey } from '../utils/tizenKeys';
import { Channel } from '../types';
import { motion, AnimatePresence } from 'framer-motion';
import { Play, Pause, Volume2, VolumeX, SkipBack, SkipForward, ArrowLeft, Info } from 'lucide-react';
import '../styles/video-player.css';

export interface VideoPlayerProps {
    url: string;
    title: string;
    onClose: () => void;
    onNextChannel?: () => void;
    onPrevChannel?: () => void;
    onChannelSelect?: (channel: Channel) => void;
    variant?: 'tv' | 'movie';
    channel?: Channel;
    channels?: Channel[];
    program?: { title: string; startTime: Date; endTime: Date };
}

// Format time helper
const formatTime = (seconds: number) => {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = Math.floor(seconds % 60);
    if (h > 0) return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
    return `${m}:${s.toString().padStart(2, '0')}`;
};

export default function VideoPlayer({
    url,
    title,
    onClose,
    onNextChannel,
    onPrevChannel,
    onChannelSelect,
    variant = 'movie',
    channel,
    channels = [],
    program
}: VideoPlayerProps) {
    const videoRef = useRef<HTMLVideoElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const playerRef = useRef<shaka.Player | null>(null);

    // UI State
    const [isPlaying, setIsPlaying] = useState(true);
    const [currentTime, setCurrentTime] = useState(0);
    const [duration, setDuration] = useState(0);
    const [isMuted, setIsMuted] = useState(false);
    const [showControls, setShowControls] = useState(true);
    const [isBuffering, setIsBuffering] = useState(true);
    const [numberBuffer, setNumberBuffer] = useState('');
    const [isNotFound, setIsNotFound] = useState(false);

    const controlsTimeoutRef = useRef<NodeJS.Timeout>();
    const numberTimeoutRef = useRef<NodeJS.Timeout>();

    // Activity handler to show/hide controls
    const handleActivity = useCallback(() => {
        setShowControls(true);
        if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
        controlsTimeoutRef.current = setTimeout(() => {
            if (variant === 'movie' && isPlaying) {
                setShowControls(false);
            } else if (variant === 'tv') {
                setShowControls(false);
            }
        }, 5000);
    }, [variant, isPlaying]);

    useEffect(() => {
        window.addEventListener('mousemove', handleActivity);
        window.addEventListener('keydown', handleActivity);
        return () => {
            window.removeEventListener('mousemove', handleActivity);
            window.removeEventListener('keydown', handleActivity);
            if (controlsTimeoutRef.current) clearTimeout(controlsTimeoutRef.current);
        };
    }, [handleActivity]);


    // Memoize onClose to prevent effect re-runs
    const handleClose = useCallback(() => {
        onClose();
    }, [onClose]);

    useEffect(() => {
        const initPlayer = async () => {
            if (!videoRef.current || !containerRef.current) return;

            const player = new shaka.Player(videoRef.current);
            // We use Player directly without UI overlay to allow custom UI
            playerRef.current = player;

            player.addEventListener('buffering', (event: any) => {
                setIsBuffering(event.buffering);
            });

            try {
                await player.load(url);
                videoRef.current.play();
                setIsPlaying(true);
                handleActivity(); // Show controls on start
            } catch (error) {
                console.error('Error loading video:', error);
            }
        };

        const timer = setTimeout(initPlayer, 100);

        return () => {
            clearTimeout(timer);
            if (playerRef.current) {
                playerRef.current.destroy();
            }
        };
    }, [url]);

    // Update time and duration
    const onTimeUpdate = () => {
        if (videoRef.current) {
            setCurrentTime(videoRef.current.currentTime);
            setDuration(videoRef.current.duration);
        }
    };

    // Toggle Play/Pause
    const togglePlay = useCallback(() => {
        if (videoRef.current) {
            if (videoRef.current.paused) {
                videoRef.current.play();
                setIsPlaying(true);
            } else {
                videoRef.current.pause();
                setIsPlaying(false);
                setShowControls(true); // Always show controls when paused
            }
        }
    }, []);

    // Seek
    const handleSeek = useCallback((time: number) => {
        if (videoRef.current) {
            videoRef.current.currentTime = time;
            setCurrentTime(time);
            handleActivity();
        }
    }, [handleActivity]);

    // Keyboard Handler
    useEffect(() => {
        const handleKeyDown = (e: KeyboardEvent) => {
            if (e.key === 'Escape' || e.key === 'Back' || e.key === 'Backspace' || isBackKey(e.keyCode)) {
                e.preventDefault();
                e.stopPropagation();
                handleClose();
                return;
            }

            if (videoRef.current) {
                switch (e.keyCode) {
                    // Channel Switching
                    case TIZEN_KEYS.CHANNEL_UP:
                    case TIZEN_KEYS.ARROW_DOWN:
                    case 40:
                        if (onNextChannel) {
                            e.preventDefault();
                            onNextChannel();
                            handleActivity();
                        }
                        break;

                    case TIZEN_KEYS.CHANNEL_DOWN:
                    case TIZEN_KEYS.ARROW_UP:
                    case 38:
                        if (onPrevChannel) {
                            e.preventDefault();
                            onPrevChannel();
                            handleActivity();
                        }
                        break;

                    case TIZEN_KEYS.VOLUME_UP:
                    case 175:
                        if (videoRef.current.volume < 1) {
                            const newVol = Math.min(1, videoRef.current.volume + 0.1);
                            videoRef.current.volume = newVol;
                            handleActivity();
                        }
                        break;

                    case TIZEN_KEYS.VOLUME_DOWN:
                    case 174:
                        if (videoRef.current.volume > 0) {
                            const newVol = Math.max(0, videoRef.current.volume - 0.1);
                            videoRef.current.volume = newVol;
                            handleActivity();
                        }
                        break;

                    case TIZEN_KEYS.MUTE:
                    case 173:
                        videoRef.current.muted = !videoRef.current.muted;
                        setIsMuted(videoRef.current.muted);
                        handleActivity();
                        break;

                    case TIZEN_KEYS.PLAY:
                    case TIZEN_KEYS.PLAY_PAUSE:
                    case TIZEN_KEYS.ENTER: // Enter toggles play in movie mode?
                        if (e.keyCode === TIZEN_KEYS.ENTER && variant === 'tv') {
                            // If we have a number buffer, try to switch channel
                            if (numberBuffer) {
                                const targetOrder = parseInt(numberBuffer, 10);
                                const matchedChannel = channels.find(ch => ch.order === targetOrder);
                                if (matchedChannel && onChannelSelect) {
                                    onChannelSelect(matchedChannel);
                                    setNumberBuffer('');
                                    setIsNotFound(false);
                                    if (numberTimeoutRef.current) clearTimeout(numberTimeoutRef.current);
                                } else {
                                    // Make it show not found immediately
                                    setIsNotFound(true);
                                    if (numberTimeoutRef.current) clearTimeout(numberTimeoutRef.current);
                                    numberTimeoutRef.current = setTimeout(() => {
                                        setNumberBuffer('');
                                        setIsNotFound(false);
                                    }, 2000);
                                }
                                break;
                            }
                        }
                        togglePlay();
                        handleActivity();
                        break;

                    case TIZEN_KEYS.PAUSE:
                    case TIZEN_KEYS.STOP:
                        if (videoRef.current) {
                            videoRef.current.pause();
                            setIsPlaying(false);
                            handleActivity();
                        }
                        break;

                    case TIZEN_KEYS.REWIND:
                    case TIZEN_KEYS.ARROW_LEFT:
                    case 37:
                        handleSeek(Math.max(0, videoRef.current.currentTime - 10));
                        break;

                    case TIZEN_KEYS.FAST_FORWARD:
                    case TIZEN_KEYS.ARROW_RIGHT:
                    case 39:
                        handleSeek(Math.min(videoRef.current.duration, videoRef.current.currentTime + 10));
                        break;
                }
            }

            // Handle number keys (0-9) for direct channel navigation
            if (variant === 'tv' && channels.length > 0) {
                const isNumberKey = (e.keyCode >= 48 && e.keyCode <= 57) || (e.keyCode >= 96 && e.keyCode <= 105);
                if (isNumberKey) {
                    e.preventDefault();
                    // Get the number pressed
                    const num = e.keyCode >= 96 ? e.keyCode - 96 : e.keyCode - 48;

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

                    // Clear buffer/Switch channel after 2 seconds of no input
                    numberTimeoutRef.current = setTimeout(() => {
                        setNumberBuffer(currentBuffer => {
                            const targetOrder = parseInt(currentBuffer, 10);
                            const matchedChannel = channels.find(ch => ch.order === targetOrder);

                            if (matchedChannel && onChannelSelect) {
                                onChannelSelect(matchedChannel);
                                handleActivity();
                                return ''; // Clear buffer on success
                            }

                            if (!matchedChannel && currentBuffer !== '') {
                                setIsNotFound(true);
                                // Keep the buffer visible for 2 more seconds to show "Not Found"
                                numberTimeoutRef.current = setTimeout(() => {
                                    setNumberBuffer('');
                                    setIsNotFound(false);
                                }, 2000);
                                return currentBuffer;
                            }

                            return '';
                        });
                    }, 2000); // Increased to 2s to give more time to type

                    return;
                }
            }
        };

        window.addEventListener('keydown', handleKeyDown, true);
        return () => window.removeEventListener('keydown', handleKeyDown, true);
    }, [onClose, onNextChannel, onPrevChannel, variant, togglePlay, handleSeek, handleActivity]);

    return (
        <div
            ref={containerRef}
            className="video-container"
            style={{
                position: 'fixed',
                inset: 0,
                zIndex: 10000,
                backgroundColor: '#000',
                overflow: 'hidden'
            }}
        >
            <video
                ref={videoRef}
                style={{ width: '100%', height: '100%' }}
                autoPlay
                onTimeUpdate={onTimeUpdate}
            />

            {/* Buffering Indicator */}
            {isBuffering && (
                <div style={{
                    position: 'absolute',
                    top: '50%',
                    left: '50%',
                    transform: 'translate(-50%, -50%)',
                    zIndex: 20
                }}>
                    <motion.div
                        animate={{ rotate: 360 }}
                        transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
                        style={{
                            width: '48px',
                            height: '48px',
                            border: '4px solid rgba(255, 255, 255, 0.3)',
                            borderTopColor: '#fff',
                            borderRadius: '50%',
                        }}
                    />
                </div>
            )}

            {/* Custom UI Overlay */}
            <AnimatePresence>
                {showControls && (
                    <motion.div
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 0.3 }}
                        style={{
                            position: 'absolute',
                            inset: 0,
                            zIndex: 30,
                            pointerEvents: 'none'
                        }}
                    >
                        {/* Gradient Overlays */}
                        <div style={{
                            position: 'absolute',
                            top: 0,
                            left: 0,
                            right: 0,
                            height: '160px',
                            background: 'linear-gradient(to bottom, rgba(0,0,0,0.8), transparent)',
                        }} />
                        <div style={{
                            position: 'absolute',
                            bottom: 0,
                            left: 0,
                            right: 0,
                            height: '240px',
                            background: 'linear-gradient(to top, rgba(0,0,0,0.9), transparent)',
                        }} />

                        {/* Top Bar */}
                        <div style={{
                            position: 'absolute',
                            top: '32px',
                            left: '32px',
                            right: '32px',
                            display: 'flex',
                            justifyContent: 'space-between',
                            alignItems: 'flex-start',
                            pointerEvents: 'auto'
                        }}>
                            {/* Back Button & Title */}
                            <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
                                <button
                                    onClick={handleClose}
                                    style={{
                                        background: 'transparent',
                                        border: 'none',
                                        color: '#fff',
                                        cursor: 'pointer',
                                        padding: '8px',
                                        display: 'flex',
                                        alignItems: 'center',
                                        gap: '8px'
                                    }}
                                >
                                    <ArrowLeft size={32} />
                                    {variant === 'movie' && <span style={{ fontSize: '18px', fontWeight: 500 }}>Back</span>}
                                </button>

                                {/* TV Mode Channel Info */}
                                {variant === 'tv' && channel && (
                                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                                        {channel.logo && (
                                            <img src={channel.logo} alt={channel.name} style={{ height: '48px', objectFit: 'contain' }} />
                                        )}
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                                <span style={{ fontSize: '24px', fontWeight: 700, color: '#fff' }}>
                                                    {channel.order ? `${channel.order}. ` : ''}{channel.name}
                                                </span>
                                                <span style={{
                                                    background: '#ef4444',
                                                    color: 'white',
                                                    padding: '2px 8px',
                                                    borderRadius: '4px',
                                                    fontWeight: 700,
                                                    fontSize: '12px'
                                                }}>LIVE</span>
                                            </div>
                                            {/* Show current program if available */}
                                            {(() => {
                                                const currentProgram = channel.schedule?.find(p => p.isLive);
                                                if (currentProgram) {
                                                    return (
                                                        <span style={{ fontSize: '16px', color: 'rgba(255,255,255,0.8)' }}>
                                                            {currentProgram.title}
                                                        </span>
                                                    );
                                                }
                                                return channel.category ? (
                                                    <span style={{ fontSize: '14px', color: 'rgba(255,255,255,0.7)' }}>{channel.category}</span>
                                                ) : null;
                                            })()}
                                        </div>
                                    </div>
                                )}
                                {/* Movie Mode Title */}
                                {variant === 'movie' && (
                                    <h1 style={{ fontSize: '24px', fontWeight: 700, margin: 0, color: '#fff' }}>{title}</h1>
                                )}
                            </div>
                        </div>

                        {/* Center Controls (Play/Pause) for Movie Mode */}
                        {variant === 'movie' && (
                            <div style={{
                                position: 'absolute',
                                top: '50%',
                                left: '50%',
                                transform: 'translate(-50%, -50%)',
                                pointerEvents: 'auto'
                            }}>
                                <button
                                    onClick={togglePlay}
                                    style={{
                                        background: 'rgba(0, 0, 0, 0.5)',
                                        border: '2px solid rgba(255, 255, 255, 0.2)',
                                        borderRadius: '50%',
                                        width: '80px',
                                        height: '80px',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        color: '#fff',
                                        cursor: 'pointer',
                                        backdropFilter: 'blur(4px)',
                                        transition: 'transform 0.2s, background 0.2s'
                                    }}
                                >
                                    {isPlaying ? <Pause size={40} fill="#fff" /> : <Play size={40} fill="#fff" />}
                                </button>
                            </div>
                        )}

                        {/* Bottom Bar */}
                        <div style={{
                            position: 'absolute',
                            bottom: '48px',
                            left: '48px',
                            right: '48px',
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '16px',
                            pointerEvents: 'auto'
                        }}>

                            {/* Movie Mode Scrubber & Controls */}
                            {variant === 'movie' && (
                                <>
                                    {/* Scrubber */}
                                    <div style={{ position: 'relative', width: '100%', height: '6px', background: 'rgba(255,255,255,0.2)', borderRadius: '3px', cursor: 'pointer' }}
                                        onClick={(e) => {
                                            const rect = e.currentTarget.getBoundingClientRect();
                                            const x = e.clientX - rect.left;
                                            const percent = x / rect.width;
                                            handleSeek(percent * duration);
                                        }}
                                    >
                                        <div style={{
                                            width: `${(currentTime / duration) * 100}%`,
                                            height: '100%',
                                            background: '#e50914',
                                            borderRadius: '3px',
                                            position: 'relative'
                                        }}>
                                            <div style={{
                                                position: 'absolute',
                                                right: '-6px',
                                                top: '-4px',
                                                width: '14px',
                                                height: '14px',
                                                background: '#e50914',
                                                borderRadius: '50%',
                                                boxShadow: '0 2px 4px rgba(0,0,0,0.4)'
                                            }} />
                                        </div>
                                    </div>

                                    {/* Control Row */}
                                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '24px' }}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                {isPlaying ? <Pause size={24} onClick={togglePlay} style={{ cursor: 'pointer' }} /> : <Play size={24} onClick={togglePlay} style={{ cursor: 'pointer' }} />}
                                                <SkipBack size={20} style={{ cursor: 'pointer' }} onClick={() => handleSeek(currentTime - 10)} />
                                                <SkipForward size={20} style={{ cursor: 'pointer' }} onClick={() => handleSeek(currentTime + 10)} />
                                            </div>

                                            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                                                {isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}
                                            </div>

                                            <span style={{ fontSize: '14px', fontWeight: 500 }}>
                                                {formatTime(currentTime)} / {formatTime(duration)}
                                            </span>
                                        </div>

                                        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                                            <span style={{ fontSize: '16px', fontWeight: 600 }}>{title}</span>
                                            <Info size={20} />
                                        </div>
                                    </div>
                                </>
                            )}
                        </div>
                    </motion.div>
                )}
            </AnimatePresence>
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
                            zIndex: 10001, // Higher than video container (10000)
                            boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)',
                            minWidth: isNotFound ? '240px' : '120px',
                            pointerEvents: 'none'
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
        </div>
    );
}
