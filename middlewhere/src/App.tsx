import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./contexts/AuthContext";
import Auth from "./pages/Auth";
import Dashboard from "./pages/Dashboard";
import CompanySetup from "./pages/CompanySetup";
import Streamers from "./pages/Streamers";
import AddStreamer from "./pages/AddStreamer";
import Channels from "./pages/Channels";
import ChannelPackages from "./pages/ChannelPackages";
import Users from "./pages/Users";
import UserGroups from "./pages/UserGroups";
import ApiManagement from "./pages/ApiManagement";
import Tokens from "./pages/Tokens";
import Settings from "./pages/Settings";
import Movies from "./pages/Movies";
import EPGSettings from "./pages/EPGSettings";
import Messages from "./pages/Messages";
import Games from "./pages/Games";
import NotFound from "./pages/NotFound";

const queryClient = new QueryClient();

const App = () => (
  <QueryClientProvider client={queryClient}>
    <TooltipProvider>
      <Toaster />
      <Sonner />
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/auth" element={<Auth />} />
            <Route path="/" element={<Dashboard />} />
            <Route path="/company" element={<CompanySetup />} />
            <Route path="/movies" element={<Movies />} />
            <Route path="/streamers" element={<Streamers />} />
            <Route path="/streamers/add" element={<AddStreamer />} />
            <Route path="/channels" element={<Channels />} />
            <Route path="/channels/epg" element={<EPGSettings />} />
            <Route path="/packages" element={<ChannelPackages />} />
            <Route path="/users" element={<Users />} />
            <Route path="/user-groups" element={<UserGroups />} />
            <Route path="/api" element={<ApiManagement />} />
            <Route path="/tokens" element={<Tokens />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/messages" element={<Messages />} />
            <Route path="/games" element={<Games />} />
            <Route path="*" element={<NotFound />} />
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </TooltipProvider>
  </QueryClientProvider>
);

export default App;

