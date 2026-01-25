import { useState, useRef, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Play, Pause, Maximize2, Volume2, VolumeX } from 'lucide-react';
import { Channel } from '../types';
import shaka from 'shaka-player';

interface StaticPreviewPlayerProps {
    channel: Channel;
    onClose: () => void;
    onPlayFullscreen: (channel: Channel) => void;
    onToggleFavorite?: (channelId: string) => void;
    autoPlay?: boolean;
    isFocused?: boolean;
}

export default function StaticPreviewPlayer({
    channel,
    onClose: _onClose,
    onPlayFullscreen,
    onToggleFavorite,
    autoPlay = false,
    isFocused = false
}: StaticPreviewPlayerProps) {
    void _onClose; // Suppress unused variable warning - kept for API compatibility
    void onToggleFavorite; // Suppress unused variable warning - UI simplified
    const [isPlaying, setIsPlaying] = useState(false);
    const [isMuted, setIsMuted] = useState(true);
    const [isLoading, setIsLoading] = useState(false);
    const [playerReady, setPlayerReady] = useState(false);
    const videoRef = useRef<HTMLVideoElement>(null);
    const playerRef = useRef<shaka.Player | null>(null);

    // Initialize Shaka Player
    useEffect(() => {
        if (!videoRef.current) return;

        // Install polyfills for older browsers
        shaka.polyfill.installAll();

        if (!shaka.Player.isBrowserSupported()) {
            console.error('Browser not supported for Shaka Player');
            return;
        }

        const player = new shaka.Player(videoRef.current);
        playerRef.current = player;

        // Configure for ultra-fast startup
        player.configure({
            streaming: {
                lowLatencyMode: true,
                bufferingGoal: 0.5,        // Start playing with just 0.5s buffer
                rebufferingGoal: 0.3,      // Resume quickly after stall
                bufferBehind: 5,           // Keep less buffer behind
                jumpLargeGaps: true,       // Skip gaps to maintain playback
                stallEnabled: false,       // Don't pause for minor issues
                retryParameters: {
                    maxAttempts: 2,
                    baseDelay: 200,
                    backoffFactor: 1.2,
                    timeout: 5000
                }
            },
            abr: {
                enabled: true,
                defaultBandwidthEstimate: 8000000, // Start with 8 Mbps estimate for faster initial quality
                switchInterval: 2,                  // Allow faster quality switches
                bandwidthDowngradeTarget: 0.8
            },
            manifest: {
                retryParameters: {
                    maxAttempts: 2,
                    baseDelay: 100,
                    timeout: 3000
                }
            }
        });

        player.addEventListener('error', (event: any) => {
            console.error('Shaka Player error:', event.detail);
            setIsLoading(false);
        });

        setPlayerReady(true);

        return () => {
            setPlayerReady(false);
            if (playerRef.current) {
                playerRef.current.destroy();
                playerRef.current = null;
            }
        };
    }, []);

    // Handle autoPlay changes and channel changes
    useEffect(() => {
        // Wait for player to be ready
        if (!playerReady) return;

        const player = playerRef.current;
        const video = videoRef.current;

        if (!player || !video) return;

        let cancelled = false;

        // Stop playback and unload when autoPlay is disabled
        if (!autoPlay) {
            video.pause();
            player.unload().catch(() => {});
            setIsLoading(false);
            setIsPlaying(false);
            return;
        }

        // autoPlay is true - load and play the stream
        const loadStream = async () => {
            if (!channel.streamUrl || cancelled) return;

            setIsLoading(true);
            setIsPlaying(false);

            try {
                await player.load(channel.streamUrl);

                if (cancelled) return;

                await video.play();
                if (!cancelled) {
                    setIsPlaying(true);
                    setIsLoading(false);
                }
            } catch (error: any) {
                if (!cancelled) {
                    // Ignore LOAD_INTERRUPTED errors (normal when switching fast)
                    if (error?.code !== 7000) {
                        console.error('Error loading/playing stream:', error);
                    }
                    setIsLoading(false);
                }
            }
        };

        loadStream();

        return () => {
            cancelled = true;
            video.pause();
        };
    }, [channel.id, channel.streamUrl, autoPlay, playerReady]);

    // Handle play/pause
    const togglePlayback = async () => {
        if (!videoRef.current || !playerRef.current) return;

        if (isPlaying) {
            videoRef.current.pause();
            setIsPlaying(false);
        } else {
            setIsLoading(true);
            try {
                // Load if not loaded
                if (!playerRef.current.getAssetUri()) {
                    await playerRef.current.load(channel.streamUrl);
                }
                await videoRef.current.play();
                setIsPlaying(true);
            } catch (error) {
                console.error('Error playing:', error);
            }
            setIsLoading(false);
        }
    };

    // Handle mute toggle
    const toggleMute = () => {
        if (videoRef.current) {
            videoRef.current.muted = !isMuted;
            setIsMuted(!isMuted);
        }
    };

    return (
        <motion.div
            initial={{ opacity: 0, x: 20 }}
            animate={{ opacity: 1, x: 0 }}
            exit={{ opacity: 0, x: 20 }}
            transition={{ duration: 0.3, ease: [0.2, 0, 0, 1] }}
            style={{
                width: '100%',
                display: 'flex',
                flexDirection: 'column',
                borderRadius: 'var(--md3-border-radius)',
                overflow: 'hidden',
                background: 'var(--md3-bg-secondary)',
                border: isFocused ? '2px solid var(--md3-surface-active)' : '1px solid var(--md3-border-color)',
                boxShadow: isFocused ? '0 0 20px rgba(var(--md3-surface-active-rgb, 229, 9, 20), 0.3)' : 'none',
                transition: 'border-color 0.2s ease, box-shadow 0.2s ease',
            }}
        >
            {/* Video Container */}
            <div style={{
                position: 'relative',
                aspectRatio: '16/9',
                background: 'linear-gradient(135deg, #1a1a1a 0%, #0a0a0a 100%)',
            }}>
                {/* Logo placeholder - always visible as background, fades out when playing */}
                <div style={{
                    position: 'absolute',
                    inset: 0,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    zIndex: 1,
                    transition: 'opacity 0.4s ease',
                    opacity: isPlaying ? 0 : 1,
                }}>
                    <img
                        src={channel.logo}
                        alt={channel.name}
                        style={{
                            maxWidth: '50%',
                            maxHeight: '50%',
                            objectFit: 'contain',
                            filter: 'drop-shadow(0 4px 16px rgba(0, 0, 0, 0.5))',
                        }}
                        onError={(e) => {
                            (e.target as HTMLImageElement).style.display = 'none';
                        }}
                    />
                </div>

                {/* Video element - fades in over logo */}
                <video
                    ref={videoRef}
                    muted={isMuted}
                    playsInline
                    style={{
                        position: 'absolute',
                        inset: 0,
                        width: '100%',
                        height: '100%',
                        objectFit: 'contain',
                        zIndex: 2,
                        opacity: isPlaying ? 1 : 0,
                        transition: 'opacity 0.4s ease-in-out',
                    }}
                />

                {/* Subtle loading indicator in corner */}
                {isLoading && (
                    <div style={{
                        position: 'absolute',
                        bottom: '16px',
                        right: '16px',
                        zIndex: 5,
                    }}>
                        <motion.div
                            animate={{ rotate: 360 }}
                            transition={{ duration: 0.8, repeat: Infinity, ease: 'linear' }}
                            style={{
                                width: '24px',
                                height: '24px',
                                border: '2px solid rgba(255, 255, 255, 0.2)',
                                borderTopColor: 'rgba(255, 255, 255, 0.8)',
                                borderRadius: '50%',
                            }}
                        />
                    </div>
                )}

                {/* Overlay Controls */}
                <div style={{
                    position: 'absolute',
                    inset: 0,
                    background: isPlaying
                        ? 'linear-gradient(180deg, rgba(0,0,0,0.3) 0%, transparent 30%, transparent 70%, rgba(0,0,0,0.5) 100%)'
                        : 'linear-gradient(180deg, rgba(0,0,0,0.5) 0%, transparent 30%, transparent 70%, rgba(0,0,0,0.8) 100%)',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                    padding: '16px',
                }}>
                    {/* Top Bar */}
                    <div style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'flex-start',
                    }}>
                        {/* Channel Logo - top left */}
                        <div
                            style={{
                                padding: '8px 12px',
                                borderRadius: '8px',
                                backdropFilter: 'blur(16px)',
                                background: 'rgba(255,255,255,0.15)',
                                border: '1px solid rgba(255,255,255,0.25)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                maxWidth: '120px',
                                minHeight: '40px',
                            }}
                        >
                            <img
                                src={channel.logo}
                                alt={channel.name}
                                style={{
                                    maxWidth: '100%',
                                    maxHeight: '32px',
                                    objectFit: 'contain',
                                    filter: 'drop-shadow(0 2px 8px rgba(0, 0, 0, 0.5))',
                                }}
                                onError={(e) => {
                                    // Fallback to channel name if logo fails
                                    const parent = (e.target as HTMLImageElement).parentElement;
                                    if (parent) {
                                        parent.innerHTML = `<span style="color: white; font-size: 14px; font-weight: 600;">${channel.name.slice(0, 3)}</span>`;
                                    }
                                }}
                            />
                        </div>

                        {/* Live indicator - top right */}
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            gap: '6px',
                            padding: '6px 10px',
                            borderRadius: '20px',
                            backdropFilter: 'blur(16px)',
                            background: 'rgba(229, 9, 20, 0.9)',
                            border: '1px solid rgba(239, 68, 68, 0.5)',
                        }}>
                            <div style={{
                                width: '6px',
                                height: '6px',
                                background: '#fff',
                                borderRadius: '50%',
                                animation: 'pulse 2s infinite',
                            }} />
                            <span style={{
                                color: 'white',
                                fontSize: '11px',
                                fontWeight: 600,
                                fontFamily: "'Inter', 'Roboto', sans-serif",
                            }}>LIVE</span>
                        </div>
                    </div>

                    {/* Center Play Button */}
                    <div style={{
                        position: 'absolute',
                        top: '50%',
                        left: '50%',
                        transform: 'translate(-50%, -50%)',
                    }}>
                        <motion.button
                            onClick={togglePlayback}
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.95 }}
                            disabled={isLoading}
                            style={{
                                width: '64px',
                                height: '64px',
                                borderRadius: '50%',
                                background: 'rgba(255, 255, 255, 0.95)',
                                border: 'none',
                                cursor: isLoading ? 'wait' : 'pointer',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center',
                                boxShadow: '0 4px 24px rgba(0, 0, 0, 0.5)',
                                opacity: isLoading ? 0.7 : 1,
                            }}
                        >
                            {isPlaying ? (
                                <Pause size={28} fill="black" color="black" />
                            ) : (
                                <Play size={28} fill="black" color="black" style={{ marginLeft: '4px' }} />
                            )}
                        </motion.button>
                    </div>

                    {/* Bottom Bar */}
                    <div style={{
                        display: 'flex',
                        justifyContent: 'space-between',
                        alignItems: 'center',
                    }}>
                        {/* Mute Button */}
                        <motion.button
                            onClick={toggleMute}
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.95 }}
                            style={{
                                padding: '8px',
                                borderRadius: '8px',
                                background: 'rgba(0, 0, 0, 0.5)',
                                border: 'none',
                                cursor: 'pointer',
                                color: 'white',
                                display: 'flex',
                                opacity: isPlaying ? 1 : 0.5,
                            }}
                        >
                            {isMuted ? <VolumeX size={20} /> : <Volume2 size={20} />}
                        </motion.button>

                        {/* Fullscreen Button */}
                        <motion.button
                            onClick={() => onPlayFullscreen(channel)}
                            whileHover={{ scale: 1.1 }}
                            whileTap={{ scale: 0.95 }}
                            style={{
                                padding: '8px',
                                borderRadius: '8px',
                                background: 'rgba(0, 0, 0, 0.5)',
                                border: 'none',
                                cursor: 'pointer',
                                color: 'white',
                                display: 'flex',
                            }}
                        >
                            <Maximize2 size={20} />
                        </motion.button>
                    </div>

                </div>
            </div>

            {/* Program Schedule - Material Design 3 style matching Figma */}
            {channel.schedule && channel.schedule.length > 0 && (
                <motion.div
                    initial={{ opacity: 0, y: 16 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{
                        delay: 0.15,
                        duration: 0.3,
                        ease: [0, 0, 0, 1]
                    }}
                    style={{
                        background: 'var(--md3-bg-secondary)',
                        border: '1px solid var(--md3-border-color)',
                        borderRadius: 'var(--md3-border-radius)',
                        marginTop: '12px',
                    }}
                >
                    <div style={{ padding: '16px' }}>
                        <h4 style={{
                            color: 'var(--md3-text-primary)',
                            fontSize: '16px',
                            fontWeight: 600,
                            marginBottom: '12px',
                            fontFamily: "'Inter', 'Roboto', sans-serif",
                        }}>
                            Program Schedule
                        </h4>
                        <div style={{
                            display: 'flex',
                            flexDirection: 'column',
                            gap: '2px',
                            maxHeight: '280px',
                            overflowY: 'auto',
                        }}>
                            {channel.schedule.map((program, index) => (
                                <div
                                    key={index}
                                    style={{
                                        display: 'flex',
                                        alignItems: 'flex-start',
                                        gap: '12px',
                                        padding: '10px 0',
                                        borderBottom: index < channel.schedule!.length - 1 ? '1px solid var(--md3-border-color)' : 'none',
                                    }}
                                >
                                    {/* Time badge */}
                                    <div style={{ flexShrink: 0 }}>
                                        <div style={{
                                            fontSize: '12px',
                                            color: program.isLive ? 'var(--md3-text-on-surface)' : 'var(--md3-text-secondary)',
                                            background: program.isLive ? 'var(--md3-surface-active)' : 'rgba(255, 255, 255, 0.08)',
                                            padding: '4px 10px',
                                            borderRadius: '6px',
                                            minWidth: '60px',
                                            textAlign: 'center',
                                            fontWeight: 500,
                                            fontFamily: "'Inter', 'Roboto', sans-serif",
                                        }}>
                                            {program.time}
                                        </div>
                                    </div>

                                    {/* Title and duration */}
                                    <div style={{ flex: 1, minWidth: 0 }}>
                                        <h5 style={{
                                            fontSize: '14px',
                                            lineHeight: 1.4,
                                            color: 'var(--md3-text-primary)',
                                            marginBottom: '2px',
                                            fontWeight: program.isLive ? 600 : 400,
                                            fontFamily: "'Inter', 'Roboto', sans-serif",
                                            overflow: 'hidden',
                                            textOverflow: 'ellipsis',
                                            whiteSpace: 'nowrap',
                                            margin: 0,
                                        }}>
                                            {program.title}
                                        </h5>
                                        <p style={{
                                            fontSize: '12px',
                                            color: 'var(--md3-text-secondary)',
                                            fontFamily: "'Inter', 'Roboto', sans-serif",
                                            margin: 0,
                                        }}>
                                            {program.duration}
                                        </p>
                                    </div>

                                    {/* LIVE badge - only for actually live programs */}
                                    {program.isLive && (
                                        <div style={{
                                            flexShrink: 0,
                                            fontSize: '11px',
                                            fontWeight: 700,
                                            color: 'var(--md3-text-on-surface)',
                                            background: 'var(--md3-surface-active)',
                                            padding: '4px 10px',
                                            borderRadius: '6px',
                                            fontFamily: "'Inter', 'Roboto', sans-serif",
                                        }}>
                                            LIVE
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                </motion.div>
            )}

            <style>{`
                @keyframes pulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.5; }
                }
            `}</style>
        </motion.div>
    );
}
