import { NavLink } from "react-router-dom";
import {
  LayoutDashboard,
  Building2,
  Radio,
  Tv,
  Package,
  Users,
  UsersRound,
  Key,
  Shield,
  Settings,
  LogOut,
  Film,
  List,
  MessageSquare,
  Gamepad2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/contexts/AuthContext";
import tvGoLogo from "@/assets/tvgo-logo.png";
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";

// Define menu items with service requirements
interface MenuItem {
  title: string;
  url: string;
  icon: React.ComponentType<{ className?: string }>;
  requiresService?: 'vod' | 'channels' | 'games' | 'messaging';
}

const allMenuItems: MenuItem[] = [
  { title: "Dashboard", url: "/", icon: LayoutDashboard },
  { title: "Company Setup", url: "/company", icon: Building2 },
  { title: "VOD Management", url: "/movies", icon: Film, requiresService: 'vod' },
  { title: "Streamers", url: "/streamers", icon: Radio, requiresService: 'channels' },
  { title: "Channels", url: "/channels", icon: Tv, requiresService: 'channels' },
  { title: "EPG Settings", url: "/channels/epg", icon: List, requiresService: 'channels' },
  { title: "Games", url: "/games", icon: Gamepad2, requiresService: 'games' },
  { title: "Channel Packages", url: "/packages", icon: Package, requiresService: 'channels' },
  { title: "Messages", url: "/messages", icon: MessageSquare, requiresService: 'messaging' },
  { title: "Users", url: "/users", icon: Users },
  { title: "User Groups", url: "/user-groups", icon: UsersRound },
  { title: "API Management", url: "/api", icon: Key },
  { title: "Tokens", url: "/tokens", icon: Shield },
  { title: "Settings", url: "/settings", icon: Settings },
];

export function AppSidebar() {
  const { open } = useSidebar();
  const { signOut, canAccessVod, canAccessChannels, canAccessGames, canAccessMessaging, company } = useAuth();

  // Filter menu items based on company services
  const filteredItems = allMenuItems.filter((item) => {
    if (!item.requiresService) return true;

    switch (item.requiresService) {
      case 'vod':
        return canAccessVod;
      case 'channels':
        return canAccessChannels;
      case 'games':
        return canAccessGames;
      case 'messaging':
        return canAccessMessaging;
      default:
        return true;
    }
  });

  return (
    <Sidebar collapsible="icon">
      <SidebarContent>
        <div className="px-6 py-5">
          <div className="flex flex-col items-center justify-center">
            <img
              src={tvGoLogo}
              alt="tvGO Logo"
              className="h-10 object-contain"
            />
            {open && company && company.name !== "Admin" && (
              <span className="text-xs text-muted-foreground mt-1">{company.name}</span>
            )}
          </div>
        </div>

        <SidebarGroup>
          <SidebarGroupLabel>Main Menu</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {filteredItems.map((item) => (
                <SidebarMenuItem key={item.title}>
                  <SidebarMenuButton asChild tooltip={item.title}>
                    <NavLink
                      to={item.url}
                      end={item.url === "/"}
                      className={({ isActive }) =>
                        isActive
                          ? "bg-sidebar-accent text-sidebar-accent-foreground"
                          : ""
                      }
                    >
                      <item.icon className="h-4 w-4" />
                      <span>{item.title}</span>
                    </NavLink>
                  </SidebarMenuButton>
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>

        <div className="mt-auto p-4 border-t border-sidebar-border">
          <Button
            variant="ghost"
            className="w-full justify-start"
            onClick={signOut}
          >
            <LogOut className="h-4 w-4 mr-2" />
            {open && <span>Sign Out</span>}
          </Button>
        </div>
      </SidebarContent>
    </Sidebar>
  );
}
