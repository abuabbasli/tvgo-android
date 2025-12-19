import { useState, useEffect } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Plus, Radio, RefreshCw, Trash2, Loader2 } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import api, { Streamer } from "@/lib/api";

export default function Streamers() {
  const navigate = useNavigate();
  const [streamers, setStreamers] = useState<Streamer[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [syncingId, setSyncingId] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const fetchStreamers = async () => {
    try {
      const response = await api.streamers.list();
      setStreamers(response.items);
    } catch (error) {
      console.error("Failed to fetch streamers:", error);
      toast.error("Failed to load streamers");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchStreamers();
  }, []);

  const handleSync = async (streamer: Streamer) => {
    setSyncingId(streamer.id);
    try {
      // Ingest channels from the streamer URL
      const result = await api.ingest.ingestM3UUrl(streamer.url, streamer.name);
      // Update the streamer's sync timestamp
      await api.streamers.sync(streamer.id);
      toast.success(`Synced ${result.total} channels (${result.created} new, ${result.updated} updated)`);
      fetchStreamers(); // Refresh the list
    } catch (error) {
      console.error("Failed to sync:", error);
      toast.error("Failed to sync channels");
    } finally {
      setSyncingId(null);
    }
  };

  const handleDelete = async (streamerId: string) => {
    setDeletingId(streamerId);
    try {
      await api.streamers.delete(streamerId);
      toast.success("Streamer deleted");
      setStreamers(streamers.filter(s => s.id !== streamerId));
    } catch (error) {
      console.error("Failed to delete:", error);
      toast.error("Failed to delete streamer");
    } finally {
      setDeletingId(null);
    }
  };

  const formatLastSync = (lastSync?: string) => {
    if (!lastSync) return "Never";
    const date = new Date(lastSync);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / 60000);

    if (diffMins < 1) return "Just now";
    if (diffMins < 60) return `${diffMins} mins ago`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours} hours ago`;
    return date.toLocaleDateString();
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
            <h1 className="text-3xl font-bold tracking-tight">Streamer Management</h1>
            <p className="text-muted-foreground">
              Connect and manage your streaming sources
            </p>
          </div>
          <Button className="gap-2" onClick={() => navigate("/streamers/add")}>
            <Plus className="h-4 w-4" />
            Add Streamer
          </Button>
        </div>

        {streamers.length > 0 && (
          <div className="grid gap-4 md:grid-cols-2">
            {streamers.map((streamer) => (
              <Card key={streamer.id}>
                <CardHeader>
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-3">
                      <div className="h-10 w-10 rounded-lg bg-primary/10 flex items-center justify-center">
                        <Radio className="h-5 w-5 text-primary" />
                      </div>
                      <div>
                        <CardTitle className="text-lg">{streamer.name}</CardTitle>
                        <CardDescription className="text-xs truncate max-w-[200px]">
                          {streamer.url}
                        </CardDescription>
                      </div>
                    </div>
                    <Badge variant={streamer.status === "connected" ? "default" : "destructive"}>
                      {streamer.status}
                    </Badge>
                  </div>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">Channels</span>
                    <span className="font-medium">{streamer.channel_count}</span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span className="text-muted-foreground">Last Sync</span>
                    <span className="font-medium">{formatLastSync(streamer.last_sync)}</span>
                  </div>
                  <div className="flex gap-2 pt-2">
                    <Button
                      variant="outline"
                      size="sm"
                      className="flex-1 gap-2"
                      onClick={() => handleSync(streamer)}
                      disabled={syncingId === streamer.id}
                    >
                      {syncingId === streamer.id ? (
                        <Loader2 className="h-3 w-3 animate-spin" />
                      ) : (
                        <RefreshCw className="h-3 w-3" />
                      )}
                      Fetch Channels
                    </Button>
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleDelete(streamer.id)}
                      disabled={deletingId === streamer.id}
                    >
                      {deletingId === streamer.id ? (
                        <Loader2 className="h-3 w-3 animate-spin" />
                      ) : (
                        <Trash2 className="h-3 w-3" />
                      )}
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        )}

        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-12 text-center">
            <div className="h-12 w-12 rounded-full bg-muted flex items-center justify-center mb-4">
              <Plus className="h-6 w-6 text-muted-foreground" />
            </div>
            <h3 className="font-semibold mb-2">
              {streamers.length === 0 ? "Add Your First Streamer" : "Add Another Streamer"}
            </h3>
            <p className="text-sm text-muted-foreground mb-4 max-w-sm">
              Connect to a streaming backend to start importing channels
            </p>
            <Button onClick={() => navigate("/streamers/add")}>Add Streamer</Button>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
}

