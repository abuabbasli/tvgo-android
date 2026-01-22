import { createContext, useContext, useEffect, useState, ReactNode } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import api, { User, getAccessToken, clearTokens, setTokens } from "@/lib/api";

// Company services that determine what features are visible
export interface CompanyServices {
  enable_vod: boolean;
  enable_channels: boolean;
  enable_games: boolean;
  enable_messaging: boolean;
}

// Company info returned from company login
export interface Company {
  id: string;
  name: string;
  slug: string;
  username: string;
  is_active: boolean;
  services: CompanyServices;
}

interface AuthContextType {
  user: User | null;
  company: Company | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  signOut: () => Promise<void>;
  // Service visibility helpers
  canAccessChannels: boolean;
  canAccessVod: boolean;
  canAccessGames: boolean;
  canAccessMessaging: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

// Storage keys
const COMPANY_KEY = "company_data";

export const AuthProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<User | null>(null);
  const [company, setCompany] = useState<Company | null>(() => {
    // Try to restore company from session storage
    const stored = sessionStorage.getItem(COMPANY_KEY);
    return stored ? JSON.parse(stored) : null;
  });
  const [isLoading, setIsLoading] = useState(true);
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    // Check for existing session on mount
    const checkAuth = async () => {
      const token = getAccessToken();
      if (token) {
        try {
          // Try to get user info - this still works for both admin and company tokens
          const userData = await api.auth.me();
          setUser(userData);
        } catch {
          // Token is invalid, clear it
          clearTokens();
          setUser(null);
          setCompany(null);
          sessionStorage.removeItem(COMPANY_KEY);
        }
      }
      setIsLoading(false);
    };

    checkAuth();
  }, []);

  // Redirect to login if not authenticated (except on auth page)
  useEffect(() => {
    if (!isLoading && !user && !company && location.pathname !== "/auth") {
      navigate("/auth");
    }
  }, [isLoading, user, company, location.pathname, navigate]);

  const login = async (username: string, password: string) => {
    // Try company login first (for multi-tenant middleware)
    try {
      const response = await api.auth.companyLogin(username, password);
      setCompany(response.company);
      sessionStorage.setItem(COMPANY_KEY, JSON.stringify(response.company));
      // Also create a pseudo-user for compatibility
      setUser({
        id: response.company.id,
        username: response.company.username,
        displayName: response.company.name,
      });
      navigate("/");
      return;
    } catch {
      // Fall back to regular admin login
      const response = await api.auth.login(username, password);
      setUser(response.user);
      // For admin users, enable all services
      const adminCompany: Company = {
        id: "admin",
        name: "Admin",
        slug: "admin",
        username: response.user.username,
        is_active: true,
        services: {
          enable_vod: true,
          enable_channels: true,
          enable_games: true,
          enable_messaging: true,
        },
      };
      setCompany(adminCompany);
      sessionStorage.setItem(COMPANY_KEY, JSON.stringify(adminCompany));
      navigate("/");
    }
  };

  const signOut = async () => {
    api.auth.logout();
    setUser(null);
    setCompany(null);
    sessionStorage.removeItem(COMPANY_KEY);
    navigate("/auth");
  };

  // Service visibility based on company services
  const canAccessChannels = company?.services?.enable_channels ?? true;
  const canAccessVod = company?.services?.enable_vod ?? true;
  const canAccessGames = company?.services?.enable_games ?? false;
  const canAccessMessaging = company?.services?.enable_messaging ?? false;

  return (
    <AuthContext.Provider
      value={{
        user,
        company,
        isAuthenticated: !!(user || company),
        isLoading,
        login,
        signOut,
        canAccessChannels,
        canAccessVod,
        canAccessGames,
        canAccessMessaging,
      }}
    >
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
