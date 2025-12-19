import { useState, useMemo } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { ArrowLeft, Tv, Search, Loader2, Download, ChevronDown, ChevronRight, FolderOpen } from "lucide-react";
import { useNavigate } from "react-router-dom";
import { toast } from "sonner";
import api, { M3UChannelPreview } from "@/lib/api";

export default function AddStreamer() {
  const navigate = useNavigate();
  const [selectedChannels, setSelectedChannels] = useState<string[]>([]);
  const [search, setSearch] = useState("");
  const [streamerName, setStreamerName] = useState("");
  const [streamerUrl, setStreamerUrl] = useState("");
  const [channels, setChannels] = useState<M3UChannelPreview[]>([]);
  const [isFetching, setIsFetching] = useState(false);
  const [isImporting, setIsImporting] = useState(false);
  const [hasFetched, setHasFetched] = useState(false);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  const filteredChannels = channels.filter(
    (channel) =>
      channel.name.toLowerCase().includes(search.toLowerCase()) ||
      (channel.group?.toLowerCase() || "").includes(search.toLowerCase())
  );

  // Group channels by category
  const groupedChannels = useMemo(() => {
    const groups: Record<string, M3UChannelPreview[]> = {};

    filteredChannels.forEach((channel) => {
      const groupName = channel.group || "Uncategorized";
      if (!groups[groupName]) {
        groups[groupName] = [];
      }
      groups[groupName].push(channel);
    });

    // Sort groups alphabetically, but put "Uncategorized" at the end
    return Object.entries(groups).sort(([a], [b]) => {
      if (a === "Uncategorized") return 1;
      if (b === "Uncategorized") return -1;
      return a.localeCompare(b);
    });
  }, [filteredChannels]);

  const toggleChannel = (id: string) => {
    setSelectedChannels((prev) =>
      prev.includes(id) ? prev.filter((cId) => cId !== id) : [...prev, id]
    );
  };

  const toggleGroup = (groupName: string) => {
    setExpandedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(groupName)) {
        next.delete(groupName);
      } else {
        next.add(groupName);
      }
      return next;
    });
  };

  const toggleAllInGroup = (groupChannels: M3UChannelPreview[]) => {
    const groupIds = groupChannels.map((c) => c.id);
    const allSelected = groupIds.every((id) => selectedChannels.includes(id));

    if (allSelected) {
      setSelectedChannels((prev) => prev.filter((id) => !groupIds.includes(id)));
    } else {
      setSelectedChannels((prev) => [...new Set([...prev, ...groupIds])]);
    }
  };

  const toggleAll = () => {
    if (selectedChannels.length === filteredChannels.length) {
      setSelectedChannels([]);
    } else {
      setSelectedChannels(filteredChannels.map((c) => c.id));
    }
  };

  const expandAll = () => {
    setExpandedGroups(new Set(groupedChannels.map(([name]) => name)));
  };

  const collapseAll = () => {
    setExpandedGroups(new Set());
  };

  const handleFetchChannels = async () => {
    if (!streamerUrl) {
      toast.error("Please enter a M3U URL");
      return;
    }

    setIsFetching(true);
    setChannels([]);
    setSelectedChannels([]);
    setHasFetched(false);
    setExpandedGroups(new Set());

    try {
      const response = await api.ingest.previewM3UUrl(streamerUrl, streamerName);
      setChannels(response.channels);
      setHasFetched(true);

      // Auto-expand all groups
      const groups = new Set(response.channels.map((c) => c.group || "Uncategorized"));
      setExpandedGroups(groups);

      toast.success(`Found ${response.total} channels in ${groups.size} categories`);
    } catch (error) {
      console.error("Failed to fetch M3U:", error);
      const message = error instanceof Error ? error.message : "Failed to fetch M3U";
      toast.error(message);
    } finally {
      setIsFetching(false);
    }
  };

  const handleSubmit = async () => {
    if (!streamerName) {
      toast.error("Please enter a streamer name");
      return;
    }

    setIsImporting(true);

    try {
      // Create the streamer first
      await api.streamers.create({ name: streamerName, url: streamerUrl });

      // Then ingest the selected channels (or all if none selected)
      const channelIds = selectedChannels.length > 0 ? selectedChannels : undefined;
      const result = await api.ingest.ingestM3UUrl(streamerUrl, streamerName, channelIds);

      toast.success(`Imported ${result.total} channels successfully!`);
      navigate("/streamers");
    } catch (error) {
      console.error("Failed to import:", error);
      const message = error instanceof Error ? error.message : "Failed to import channels";
      toast.error(message);
    } finally {
      setIsImporting(false);
    }
  };

  const isGroupSelected = (groupChannels: M3UChannelPreview[]) => {
    const groupIds = groupChannels.map((c) => c.id);
    return groupIds.every((id) => selectedChannels.includes(id));
  };

  const isGroupPartiallySelected = (groupChannels: M3UChannelPreview[]) => {
    const groupIds = groupChannels.map((c) => c.id);
    const selectedCount = groupIds.filter((id) => selectedChannels.includes(id)).length;
    return selectedCount > 0 && selectedCount < groupIds.length;
  };

  return (
    <DashboardLayout>
      <div className="p-6 space-y-6">
        <div className="flex items-center gap-4">
          <Button variant="ghost" size="icon" onClick={() => navigate("/streamers")}>
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Add Streamer</h1>
            <p className="text-muted-foreground">
              Connect a M3U streaming source and import channels
            </p>
          </div>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Streamer Configuration</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid gap-4 md:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="streamer-name">Streamer Name</Label>
                <Input
                  id="streamer-name"
                  placeholder="e.g., StreamCo"
                  value={streamerName}
                  onChange={(e) => setStreamerName(e.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="streamer-url">M3U URL</Label>
                <Input
                  id="streamer-url"
                  placeholder="https://example.com/playlist.m3u or http://bit.ly/..."
                  value={streamerUrl}
                  onChange={(e) => setStreamerUrl(e.target.value)}
                />
              </div>
            </div>
            <Button
              variant="outline"
              className="w-full gap-2"
              onClick={handleFetchChannels}
              disabled={isFetching || !streamerUrl}
            >
              {isFetching ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Download className="h-4 w-4" />
              )}
              {isFetching ? "Fetching Channels..." : "Fetch Channels from URL"}
            </Button>
          </CardContent>
        </Card>

        {hasFetched && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-xl font-semibold">
                Available Channels ({filteredChannels.length}) in {groupedChannels.length} categories
              </h2>
              <div className="flex items-center gap-2">
                <div className="relative">
                  <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder="Search channels..."
                    className="pl-8 w-64"
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                  />
                </div>
                <Button variant="outline" size="sm" onClick={expandAll}>
                  Expand All
                </Button>
                <Button variant="outline" size="sm" onClick={collapseAll}>
                  Collapse All
                </Button>
                <Button variant="outline" onClick={toggleAll}>
                  {selectedChannels.length === filteredChannels.length ? "Deselect All" : "Select All"}
                </Button>
              </div>
            </div>

            <div className="bg-primary/10 border border-primary/20 rounded-lg p-4 flex items-center justify-between">
              <span className="text-sm font-medium">
                {selectedChannels.length > 0
                  ? `${selectedChannels.length} channel${selectedChannels.length !== 1 ? "s" : ""} selected`
                  : `All ${channels.length} channels will be imported`}
              </span>
              <Button onClick={handleSubmit} disabled={isImporting || !streamerName}>
                {isImporting ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Importing...
                  </>
                ) : (
                  "Import Channels"
                )}
              </Button>
            </div>

            <div className="space-y-2">
              {groupedChannels.map(([groupName, groupChannels]) => (
                <Collapsible
                  key={groupName}
                  open={expandedGroups.has(groupName)}
                  onOpenChange={() => toggleGroup(groupName)}
                >
                  <div className="border rounded-lg">
                    <CollapsibleTrigger asChild>
                      <div className="flex items-center justify-between p-4 cursor-pointer hover:bg-muted/50">
                        <div className="flex items-center gap-3">
                          {expandedGroups.has(groupName) ? (
                            <ChevronDown className="h-4 w-4" />
                          ) : (
                            <ChevronRight className="h-4 w-4" />
                          )}
                          <FolderOpen className="h-4 w-4 text-primary" />
                          <span className="font-medium">{groupName}</span>
                          <Badge variant="secondary">{groupChannels.length} channels</Badge>
                        </div>
                        <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                          <Checkbox
                            checked={isGroupSelected(groupChannels)}
                            ref={(el) => {
                              if (el && isGroupPartiallySelected(groupChannels)) {
                                el.dataset.state = "indeterminate";
                              }
                            }}
                            onCheckedChange={() => toggleAllInGroup(groupChannels)}
                          />
                          <span className="text-sm text-muted-foreground">Select All</span>
                        </div>
                      </div>
                    </CollapsibleTrigger>
                    <CollapsibleContent>
                      <div className="grid gap-3 p-4 pt-0 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                        {groupChannels.map((channel) => (
                          <Card
                            key={channel.id}
                            className={`cursor-pointer transition-all ${selectedChannels.includes(channel.id)
                                ? "border-primary bg-primary/5"
                                : "hover:border-primary/50"
                              }`}
                            onClick={() => toggleChannel(channel.id)}
                          >
                            <CardContent className="p-3">
                              <div className="flex items-center gap-3">
                                <Checkbox
                                  checked={selectedChannels.includes(channel.id)}
                                  onCheckedChange={() => toggleChannel(channel.id)}
                                  onClick={(e) => e.stopPropagation()}
                                />
                                <div className="h-10 w-10 rounded bg-muted flex items-center justify-center overflow-hidden flex-shrink-0">
                                  {channel.logo_url ? (
                                    <img
                                      src={channel.logo_url}
                                      alt={channel.name}
                                      className="h-full w-full object-cover"
                                      onError={(e) => {
                                        e.currentTarget.style.display = "none";
                                      }}
                                    />
                                  ) : (
                                    <Tv className="h-5 w-5 text-muted-foreground" />
                                  )}
                                </div>
                                <div className="flex-1 min-w-0">
                                  <p className="font-medium text-sm truncate">{channel.name}</p>
                                  <Badge variant="outline" className="text-xs">
                                    {channel.group || "Uncategorized"}
                                  </Badge>
                                </div>
                              </div>
                            </CardContent>
                          </Card>
                        ))}
                      </div>
                    </CollapsibleContent>
                  </div>
                </Collapsible>
              ))}
            </div>

            {filteredChannels.length === 0 && (
              <div className="text-center py-12">
                <Tv className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
                <p className="text-muted-foreground">No channels found</p>
              </div>
            )}
          </div>
        )}

        {!hasFetched && (
          <div className="text-center py-12 text-muted-foreground">
            <Tv className="h-16 w-16 mx-auto mb-4 opacity-50" />
            <p>Enter a M3U URL and click "Fetch Channels" to preview available channels</p>
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}
