import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import api, { User, getAccessToken, clearTokens, setTokens } from "@/lib/api";

interface AuthContextType {
  user: User | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    // Check for existing session on mount
    const checkAuth = async () => {
      const token = getAccessToken();
      if (token) {
        try {
          const userData = await api.auth.me();
          setUser(userData);
        } catch {
          // Token is invalid, clear it
          clearTokens();
          setUser(null);
        }
      }
      setIsLoading(false);
    };

    checkAuth();
  }, []);

  // Redirect to login if not authenticated (except on auth page)
  useEffect(() => {
    if (!isLoading && !user && location.pathname !== "/auth") {
      navigate("/auth");
    }
  }, [isLoading, user, location.pathname, navigate]);

  const login = async (username: string, password: string) => {
    const response = await api.auth.login(username, password);
    setUser(response.user);
    navigate("/");
  };

  const signOut = async () => {
    api.auth.logout();
    setUser(null);
    navigate("/auth");
  };

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

