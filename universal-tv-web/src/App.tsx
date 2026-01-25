import { useState, useEffect } from 'react';
import Sidebar from './components/Sidebar';
import MobileBottomNav from './components/MobileBottomNav';
import HomePage from './pages/Home';
import ChannelsPage from './pages/Channels';
import MoviesPage from './pages/Movies';
import GamesPage from './pages/Games';
import MessagesPage from './pages/Messages';
import SettingsPage from './pages/Settings';
import { NavigationProvider, useNavigation } from './context/NavigationContext';
import { ConfigProvider } from './context/ConfigContext';

type ViewType = 'home' | 'channels' | 'movies' | 'games' | 'messages' | 'settings';

function AppContent() {
    const [activeTab, setActiveTab] = useState<ViewType>('home');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('list');
    const [sidebarCollapsed, setSidebarCollapsed] = useState(true);
    const [isMobile, setIsMobile] = useState(false);

    const { isSidebarFocused, isContentFocused, setFocusZone } = useNavigation();

    // Check device type
    useEffect(() => {
        const checkDevice = () => {
            setIsMobile(window.innerWidth < 768);
        };

        checkDevice();
        window.addEventListener('resize', checkDevice);
        return () => window.removeEventListener('resize', checkDevice);
    }, []);

    const handleTabChange = (tab: ViewType | string) => {
        setActiveTab(tab as ViewType);
        // After selecting a tab, move focus to content
        setFocusZone('content');
    };

    return (
        <div style={{
            minHeight: '100vh',
            background: 'var(--md3-bg-primary)',
            color: 'var(--md3-text-primary)',
            display: 'flex',
        }}>
            {/* Desktop Sidebar */}
            <Sidebar
                activeTab={activeTab}
                onTabChange={(tab) => handleTabChange(tab as ViewType)}
                viewMode={viewMode}
                onViewModeChange={setViewMode}
                isCollapsed={sidebarCollapsed}
                onToggleCollapse={() => setSidebarCollapsed(!sidebarCollapsed)}
                isFocused={isSidebarFocused}
            />

            {/* Mobile Bottom Navigation */}
            <MobileBottomNav
                activeView={activeTab}
                onViewChange={setActiveTab}
            />

            {/* Main Content */}
            <main style={{
                flex: 1,
                marginLeft: isMobile ? 0 : (sidebarCollapsed ? '64px' : '224px'),
                transition: 'margin-left 0.3s cubic-bezier(0.4, 0, 0.2, 1)',
                height: '100vh',
                overflow: 'auto',
                paddingBottom: isMobile ? '88px' : 0, // Space for mobile bottom nav
            }}>
                {activeTab === 'home' && (
                    <HomePage
                        onNavigateToChannels={() => setActiveTab('channels')}
                        onNavigateToMovies={() => setActiveTab('movies')}
                        isFocused={isContentFocused}
                    />
                )}
                {activeTab === 'channels' && (
                    <ChannelsPage viewMode={viewMode} isFocused={isContentFocused} />
                )}
                {activeTab === 'movies' && (
                    <MoviesPage isFocused={isContentFocused} />
                )}
                {activeTab === 'games' && (
                    <GamesPage isFocused={isContentFocused} />
                )}
                {activeTab === 'messages' && (
                    <MessagesPage isFocused={isContentFocused} />
                )}
                {activeTab === 'settings' && (
                    <SettingsPage isFocused={isContentFocused} />
                )}
            </main>
        </div>
    );
}

export default function App() {
    return (
        <ConfigProvider>
            <NavigationProvider>
                <AppContent />
            </NavigationProvider>
        </ConfigProvider>
    );
}
