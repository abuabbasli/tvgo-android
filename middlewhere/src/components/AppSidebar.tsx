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

const mainItems = [
  { title: "Dashboard", url: "/", icon: LayoutDashboard },
  { title: "Company Setup", url: "/company", icon: Building2 },
  { title: "VOD Management", url: "/movies", icon: Film },
  { title: "Streamers", url: "/streamers", icon: Radio },
  { title: "Channels", url: "/channels", icon: Tv },
  { title: "EPG Settings", url: "/channels/epg", icon: List },
  { title: "Games", url: "/games", icon: Gamepad2 },
  { title: "Channel Packages", url: "/packages", icon: Package },
  { title: "Messages", url: "/messages", icon: MessageSquare },
  { title: "Users", url: "/users", icon: Users },
  { title: "User Groups", url: "/user-groups", icon: UsersRound },
  { title: "API Management", url: "/api", icon: Key },
  { title: "Tokens", url: "/tokens", icon: Shield },
  { title: "Settings", url: "/settings", icon: Settings },
];

export function AppSidebar() {
  const { open } = useSidebar();
  const { signOut } = useAuth();

  return (
    <Sidebar collapsible="icon">
      <SidebarContent>
        <div className="px-6 py-5">
          <div className="flex items-center justify-center">
            <img
              src={tvGoLogo}
              alt="tvGO Logo"
              className="h-10 object-contain"
            />
          </div>
        </div>

        <SidebarGroup>
          <SidebarGroupLabel>Main Menu</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {mainItems.map((item) => (
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
