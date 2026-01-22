import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import api, { type User, getAccessToken, clearTokens } from "../lib/api";

interface AuthContextType {
    user: User | null;
    isAuthenticated: boolean;
    isLoading: boolean;
    login: (username: string, password: string) => Promise<void>;
    signOut: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [user, setUser] = useState<User | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const navigate = useNavigate();
    const location = useLocation();

    useEffect(() => {
        const checkAuth = async () => {
            const token = getAccessToken();
            if (token) {
                try {
                    const userData = await api.auth.me();
                    setUser(userData);
                } catch {
                    clearTokens();
                    setUser(null);
                }
            }
            setIsLoading(false);
        };

        checkAuth();
    }, []);

    // Only redirect after loading is complete
    useEffect(() => {
        if (!isLoading && !user && location.pathname !== "/login") {
            navigate("/login");
        }
    }, [isLoading, user, location.pathname, navigate]);

    const login = async (username: string, password: string) => {
        const response = await api.auth.login(username, password);
        setUser(response.user);
        navigate("/");
    };

    const signOut = () => {
        api.auth.logout();
        setUser(null);
        navigate("/login");
    };

    // Show loading indicator while checking auth
    if (isLoading) {
        return (
            <div style={{
                minHeight: '100vh',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                background: '#0f172a',
                color: 'white',
            }}>
                <div style={{ textAlign: 'center' }}>
                    <div style={{
                        width: '48px',
                        height: '48px',
                        border: '4px solid #334155',
                        borderTopColor: '#0ea5e9',
                        borderRadius: '50%',
                        animation: 'spin 1s linear infinite',
                        margin: '0 auto 16px',
                    }}></div>
                    <p>Loading...</p>
                    <style>{`
            @keyframes spin {
              to { transform: rotate(360deg); }
            }
          `}</style>
                </div>
            </div>
        );
    }

    return (
        <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, signOut }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error("useAuth must be used within an AuthProvider");
    }
    return context;
};
