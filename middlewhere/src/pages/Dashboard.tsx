import { DashboardLayout } from "@/components/DashboardLayout";
import { MetricCard } from "@/components/MetricCard";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tv, Radio, Package, Shield, Plus, PlayCircle, Loader2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import api, { DashboardStats } from "@/lib/api";

export default function Dashboard() {
  const navigate = useNavigate();
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
    try {
      const data = await api.config.getStats();
      setStats(data);
    } catch (error) {
      console.error("Failed to load stats:", error);
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading) {
    return (
      <DashboardLayout>
        <div className="flex items-center justify-center h-96">
          <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
        </div>
      </DashboardLayout>
    );
  }

  return (
    <DashboardLayout>
      <div className="p-6 space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Dashboard</h1>
            <p className="text-muted-foreground">
              Welcome back! Here's what's happening with your TV service.
            </p>
          </div>
          <Button className="gap-2" onClick={() => navigate("/streamers/add")}>
            <Plus className="h-4 w-4" />
            Add Streamer
          </Button>
        </div>

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          <MetricCard
            title="Total Channels"
            value={stats?.total_channels?.toString() || "0"}
            description={`${stats?.active_channels || 0} active, ${stats?.inactive_channels || 0} inactive`}
            icon={Tv}
          />
          <MetricCard
            title="Streamers"
            value={stats?.total_streamers?.toString() || "0"}
            description="M3U sources configured"
            icon={Radio}
          />
          <MetricCard
            title="Packages"
            value={stats?.total_packages?.toString() || "0"}
            description="Channel bundles"
            icon={Package}
          />
          <MetricCard
            title="Status"
            value="Online"
            description="All services running"
            icon={Shield}
          />
        </div>

        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>Getting Started</CardTitle>
              <CardDescription>Quick steps to set up your TV middleware</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="space-y-4">
                <div className="flex items-start gap-3">
                  <div className={`h-6 w-6 rounded-full flex items-center justify-center text-xs font-bold ${stats?.total_streamers ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'}`}>
                    {stats?.total_streamers ? '✓' : '1'}
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-medium">Add a Streamer</p>
                    <p className="text-xs text-muted-foreground">Import channels from an M3U URL</p>
                  </div>
                  {!stats?.total_streamers && (
                    <Button size="sm" variant="outline" onClick={() => navigate("/streamers/add")}>
                      Add Now
                    </Button>
                  )}
                </div>
                <div className="flex items-start gap-3">
                  <div className={`h-6 w-6 rounded-full flex items-center justify-center text-xs font-bold ${stats?.total_channels ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'}`}>
                    {stats?.total_channels ? '✓' : '2'}
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-medium">Review Channels</p>
                    <p className="text-xs text-muted-foreground">Organize and manage your imported channels</p>
                  </div>
                  {stats?.total_streamers && !stats?.total_channels && (
                    <Button size="sm" variant="outline" onClick={() => navigate("/channels")}>
                      View
                    </Button>
                  )}
                </div>
                <div className="flex items-start gap-3">
                  <div className={`h-6 w-6 rounded-full flex items-center justify-center text-xs font-bold ${stats?.total_packages ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'}`}>
                    {stats?.total_packages ? '✓' : '3'}
                  </div>
                  <div className="flex-1">
                    <p className="text-sm font-medium">Create Packages</p>
                    <p className="text-xs text-muted-foreground">Bundle channels into subscription packages</p>
                  </div>
                  {stats?.total_channels && !stats?.total_packages && (
                    <Button size="sm" variant="outline" onClick={() => navigate("/packages")}>
                      Create
                    </Button>
                  )}
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Quick Actions</CardTitle>
              <CardDescription>Common tasks and operations</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button variant="outline" className="w-full justify-start gap-2" onClick={() => navigate("/streamers/add")}>
                <Plus className="h-4 w-4" />
                Add New Streamer
              </Button>
              <Button variant="outline" className="w-full justify-start gap-2" onClick={() => navigate("/channels")}>
                <Tv className="h-4 w-4" />
                Manage Channels
              </Button>
              <Button variant="outline" className="w-full justify-start gap-2" onClick={() => navigate("/packages")}>
                <Package className="h-4 w-4" />
                Create Package
              </Button>
              <Button variant="outline" className="w-full justify-start gap-2" onClick={() => navigate("/company")}>
                <PlayCircle className="h-4 w-4" />
                Configure Branding
              </Button>
            </CardContent>
          </Card>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>System Status</CardTitle>
              <CardDescription>Current service health</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="flex items-center gap-2 text-sm">
                <div className="h-2 w-2 rounded-full bg-green-500" />
                <span>API Server: Online</span>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <div className="h-2 w-2 rounded-full bg-green-500" />
                <span>Database: Connected</span>
              </div>
              <div className="flex items-center gap-2 text-sm">
                <div className={`h-2 w-2 rounded-full ${stats?.total_streamers ? 'bg-green-500' : 'bg-yellow-500'}`} />
                <span>Streamers: {stats?.total_streamers ? `${stats.total_streamers} configured` : 'None configured'}</span>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Resources</CardTitle>
              <CardDescription>Helpful links and documentation</CardDescription>
            </CardHeader>
            <CardContent className="space-y-2">
              <Button variant="link" className="h-auto p-0 text-sm" asChild>
                <a href="http://localhost:8000/docs" target="_blank" rel="noopener noreferrer">
                  API Documentation (Swagger)
                </a>
              </Button>
              <p className="text-xs text-muted-foreground">
                View the complete API reference at /docs
              </p>
            </CardContent>
          </Card>
        </div>
      </div>
    </DashboardLayout>
  );
}
