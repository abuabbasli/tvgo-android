import { useState, useEffect } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
    DialogFooter,
} from "@/components/ui/dialog";
import {
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
} from "@/components/ui/card";
import {
    Tabs,
    TabsContent,
    TabsList,
    TabsTrigger,
} from "@/components/ui/tabs";
import {
    AlertCircle,
    CheckCircle2,
    Download,
    ExternalLink,
    Link,
    Loader2,
    Plus,
    RefreshCw,
    Settings,
    Trash2,
    Tv,
    Unlink,
} from "lucide-react";
import { toast } from "sonner";
import api, { EPGSource, EPGChannel, EPGSyncResult, ChannelEPGMapping } from "@/lib/api";

export default function EPGSettings() {
    // EPG Sources
    const [sources, setSources] = useState<EPGSource[]>([]);
    const [isLoadingSources, setIsLoadingSources] = useState(true);
    const [isSyncing, setIsSyncing] = useState<string | null>(null);

    // Add Source Dialog
    const [addDialogOpen, setAddDialogOpen] = useState(false);
    const [newSource, setNewSource] = useState({
        name: "",
        url: "",
        description: "",
        priority: 1,
        enabled: true,
    });
    const [isAddingSource, setIsAddingSource] = useState(false);

    // Preview
    const [previewUrl, setPreviewUrl] = useState("");
    const [isPreviewLoading, setIsPreviewLoading] = useState(false);
    const [previewChannels, setPreviewChannels] = useState<EPGChannel[]>([]);
    const [previewProgramsCount, setPreviewProgramsCount] = useState(0);

    // Mappings
    const [mappings, setMappings] = useState<ChannelEPGMapping[]>([]);
    const [isLoadingMappings, setIsLoadingMappings] = useState(true);

    // Sync result
    const [lastSyncResult, setLastSyncResult] = useState<EPGSyncResult | null>(null);

    // Program Viewer Dialog
    const [programDialogOpen, setProgramDialogOpen] = useState(false);
    const [selectedMappingChannel, setSelectedMappingChannel] = useState<ChannelEPGMapping | null>(null);

    // Fetch EPG Sources
    const fetchSources = async () => {
        setIsLoadingSources(true);
        try {
            const response = await api.epg.listSources();
            setSources(response.sources);
        } catch (error) {
            console.error("Failed to fetch EPG sources:", error);
            // Don't show error - might just be empty
            setSources([]);
        } finally {
            setIsLoadingSources(false);
        }
    };

    // Fetch Mappings
    const fetchMappings = async () => {
        setIsLoadingMappings(true);
        try {
            const response = await api.epg.listMappings();
            setMappings(response.mappings);
        } catch (error) {
            console.error("Failed to fetch mappings:", error);
            setMappings([]);
        } finally {
            setIsLoadingMappings(false);
        }
    };

    useEffect(() => {
        fetchSources();
        fetchMappings();
    }, []);

    // Add new source
    const handleAddSource = async () => {
        if (!newSource.name || !newSource.url) {
            toast.error("Name and URL are required");
            return;
        }

        setIsAddingSource(true);
        try {
            await api.epg.createSource(newSource);
            toast.success("EPG source added");
            setAddDialogOpen(false);
            setNewSource({ name: "", url: "", description: "", priority: 1, enabled: true });
            fetchSources();
        } catch (error) {
            console.error("Failed to add source:", error);
            toast.error("Failed to add EPG source");
        } finally {
            setIsAddingSource(false);
        }
    };

    // Delete source
    const handleDeleteSource = async (id: string) => {
        try {
            await api.epg.deleteSource(id);
            toast.success("Source deleted");
            fetchSources();
        } catch (error) {
            console.error("Failed to delete source:", error);
            toast.error("Failed to delete source");
        }
    };

    // Sync EPG from source
    const handleSync = async (sourceId: string) => {
        setIsSyncing(sourceId);
        try {
            const result = await api.epg.sync({ sourceId, force: true });
            setLastSyncResult(result);
            toast.success(`Synced ${result.channels_parsed} channels, applied ${result.mappings_applied} mappings`);
            fetchSources();
            fetchMappings();
        } catch (error) {
            console.error("Sync failed:", error);
            toast.error("EPG sync failed");
        } finally {
            setIsSyncing(null);
        }
    };

    // Preview EPG URL
    const handlePreview = async () => {
        if (!previewUrl) {
            toast.error("Enter a URL to preview");
            return;
        }

        setIsPreviewLoading(true);
        try {
            const response = await api.epg.preview(previewUrl, true);
            setPreviewChannels(response.channels);
            setPreviewProgramsCount(response.programs_count);
            toast.success(`Found ${response.channels.length} channels`);
        } catch (error) {
            console.error("Preview failed:", error);
            toast.error("Failed to preview EPG URL");
        } finally {
            setIsPreviewLoading(false);
        }
    };

    // Sync from URL directly
    const handleSyncFromUrl = async () => {
        if (!previewUrl) {
            toast.error("Enter a URL first");
            return;
        }

        setIsSyncing("url");
        try {
            const result = await api.epg.sync({ url: previewUrl, force: true });
            setLastSyncResult(result);
            toast.success(`Synced ${result.channels_parsed} channels, applied ${result.mappings_applied} mappings`);
            fetchMappings();
        } catch (error) {
            console.error("Sync failed:", error);
            toast.error("EPG sync failed");
        } finally {
            setIsSyncing(null);
        }
    };

    const mappedCount = mappings.filter(m => m.has_mapping).length;

    return (
        <DashboardLayout>
            <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-3xl font-bold tracking-tight">EPG Settings</h1>
                        <p className="text-muted-foreground">
                            Manage Electronic Program Guide sources and channel mappings
                        </p>
                    </div>
                </div>

                <Tabs defaultValue="sources" className="space-y-4">
                    <TabsList>
                        <TabsTrigger value="sources">EPG Sources</TabsTrigger>
                        <TabsTrigger value="preview">Preview & Import</TabsTrigger>
                        <TabsTrigger value="mappings">
                            Channel Mappings
                            <Badge variant="secondary" className="ml-2">
                                {mappedCount}/{mappings.length}
                            </Badge>
                        </TabsTrigger>
                    </TabsList>

                    {/* EPG Sources Tab */}
                    <TabsContent value="sources" className="space-y-4">
                        <div className="flex justify-between items-center">
                            <p className="text-sm text-muted-foreground">
                                Configure EPG XML sources to automatically download and map program guide data.
                            </p>
                            <Button onClick={() => setAddDialogOpen(true)}>
                                <Plus className="mr-2 h-4 w-4" />
                                Add Source
                            </Button>
                        </div>

                        {isLoadingSources ? (
                            <div className="flex justify-center py-12">
                                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                            </div>
                        ) : sources.length > 0 ? (
                            <div className="grid gap-4">
                                {sources.map((source) => (
                                    <Card key={source.id}>
                                        <CardHeader className="pb-2">
                                            <div className="flex justify-between items-start">
                                                <div>
                                                    <CardTitle className="flex items-center gap-2">
                                                        {source.name}
                                                        {source.enabled ? (
                                                            <Badge variant="default">Active</Badge>
                                                        ) : (
                                                            <Badge variant="secondary">Disabled</Badge>
                                                        )}
                                                    </CardTitle>
                                                    <CardDescription className="font-mono text-xs truncate max-w-[400px]">
                                                        {source.url}
                                                    </CardDescription>
                                                </div>
                                                <div className="flex gap-2">
                                                    <Button
                                                        size="sm"
                                                        variant="outline"
                                                        onClick={() => handleSync(source.id)}
                                                        disabled={isSyncing === source.id}
                                                    >
                                                        {isSyncing === source.id ? (
                                                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                                        ) : (
                                                            <RefreshCw className="mr-2 h-4 w-4" />
                                                        )}
                                                        Sync Now
                                                    </Button>
                                                    <Button
                                                        size="sm"
                                                        variant="ghost"
                                                        className="text-destructive"
                                                        onClick={() => handleDeleteSource(source.id)}
                                                    >
                                                        <Trash2 className="h-4 w-4" />
                                                    </Button>
                                                </div>
                                            </div>
                                        </CardHeader>
                                        <CardContent>
                                            <div className="flex gap-6 text-sm">
                                                <div>
                                                    <span className="text-muted-foreground">Priority:</span>{" "}
                                                    <span className="font-medium">{source.priority}</span>
                                                </div>
                                                <div>
                                                    <span className="text-muted-foreground">Channels:</span>{" "}
                                                    <span className="font-medium">{source.channel_count || 0}</span>
                                                </div>
                                                {source.last_sync && (
                                                    <div>
                                                        <span className="text-muted-foreground">Last Sync:</span>{" "}
                                                        <span className="font-medium">
                                                            {new Date(source.last_sync).toLocaleString()}
                                                        </span>
                                                    </div>
                                                )}
                                            </div>
                                            {source.description && (
                                                <p className="text-sm text-muted-foreground mt-2">{source.description}</p>
                                            )}
                                        </CardContent>
                                    </Card>
                                ))}
                            </div>
                        ) : (
                            <Card>
                                <CardContent className="flex flex-col items-center justify-center py-12">
                                    <Settings className="h-12 w-12 text-muted-foreground mb-4" />
                                    <p className="text-muted-foreground mb-4">No EPG sources configured</p>
                                    <Button onClick={() => setAddDialogOpen(true)}>
                                        <Plus className="mr-2 h-4 w-4" />
                                        Add Your First Source
                                    </Button>
                                </CardContent>
                            </Card>
                        )}

                        {lastSyncResult && (
                            <Card className="border-green-200 bg-green-50">
                                <CardHeader className="pb-2">
                                    <CardTitle className="text-lg flex items-center gap-2">
                                        <CheckCircle2 className="h-5 w-5 text-green-600" />
                                        Last Sync Result
                                    </CardTitle>
                                </CardHeader>
                                <CardContent>
                                    <div className="grid grid-cols-3 gap-4 text-sm">
                                        <div>
                                            <span className="text-muted-foreground">Channels Parsed:</span>{" "}
                                            <span className="font-bold">{lastSyncResult.channels_parsed}</span>
                                        </div>
                                        <div>
                                            <span className="text-muted-foreground">Programs Parsed:</span>{" "}
                                            <span className="font-bold">{lastSyncResult.programs_parsed}</span>
                                        </div>
                                        <div>
                                            <span className="text-muted-foreground">Mappings Applied:</span>{" "}
                                            <span className="font-bold">{lastSyncResult.mappings_applied}</span>
                                        </div>
                                    </div>
                                    {lastSyncResult.errors.length > 0 && (
                                        <div className="mt-3 text-sm text-red-600">
                                            <p className="font-medium">Errors:</p>
                                            <ul className="list-disc list-inside">
                                                {lastSyncResult.errors.slice(0, 5).map((err, i) => (
                                                    <li key={i}>{err}</li>
                                                ))}
                                            </ul>
                                        </div>
                                    )}
                                </CardContent>
                            </Card>
                        )}
                    </TabsContent>

                    {/* Preview & Import Tab */}
                    <TabsContent value="preview" className="space-y-4">
                        <Card>
                            <CardHeader>
                                <CardTitle>Preview EPG URL</CardTitle>
                                <CardDescription>
                                    Enter an EPG XML URL to preview its channels before importing
                                </CardDescription>
                            </CardHeader>
                            <CardContent className="space-y-4">
                                <div className="flex gap-2">
                                    <Input
                                        placeholder="https://example.com/epg.xml"
                                        value={previewUrl}
                                        onChange={(e) => setPreviewUrl(e.target.value)}
                                        className="flex-1"
                                    />
                                    <Button onClick={handlePreview} disabled={isPreviewLoading}>
                                        {isPreviewLoading ? (
                                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        ) : (
                                            <Download className="mr-2 h-4 w-4" />
                                        )}
                                        Preview
                                    </Button>
                                    <Button
                                        onClick={handleSyncFromUrl}
                                        disabled={isSyncing === "url" || !previewUrl}
                                        variant="default"
                                    >
                                        {isSyncing === "url" ? (
                                            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                                        ) : (
                                            <RefreshCw className="mr-2 h-4 w-4" />
                                        )}
                                        Sync & Map
                                    </Button>
                                </div>

                                {previewChannels.length > 0 && (
                                    <div className="space-y-2">
                                        <div className="flex justify-between items-center">
                                            <p className="text-sm text-muted-foreground">
                                                Found {previewChannels.length} channels with {previewProgramsCount.toLocaleString()} programs
                                            </p>
                                        </div>
                                        <div className="border rounded-lg max-h-[400px] overflow-auto">
                                            <Table>
                                                <TableHeader>
                                                    <TableRow>
                                                        <TableHead className="w-[60px]">Logo</TableHead>
                                                        <TableHead>EPG ID</TableHead>
                                                        <TableHead>Channel Name</TableHead>
                                                        <TableHead>Language</TableHead>
                                                    </TableRow>
                                                </TableHeader>
                                                <TableBody>
                                                    {previewChannels.map((channel) => (
                                                        <TableRow key={channel.id}>
                                                            <TableCell>
                                                                <div className="h-8 w-8 rounded bg-muted flex items-center justify-center overflow-hidden">
                                                                    {channel.icon_url ? (
                                                                        <img
                                                                            src={channel.icon_url}
                                                                            alt=""
                                                                            className="h-full w-full object-cover"
                                                                        />
                                                                    ) : (
                                                                        <Tv className="h-4 w-4 text-muted-foreground" />
                                                                    )}
                                                                </div>
                                                            </TableCell>
                                                            <TableCell className="font-mono text-xs">{channel.id}</TableCell>
                                                            <TableCell className="font-medium">{channel.display_name}</TableCell>
                                                            <TableCell>
                                                                <Badge variant="outline">{channel.lang}</Badge>
                                                            </TableCell>
                                                        </TableRow>
                                                    ))}
                                                </TableBody>
                                            </Table>
                                        </div>
                                    </div>
                                )}
                            </CardContent>
                        </Card>
                    </TabsContent>

                    {/* Mappings Tab */}
                    <TabsContent value="mappings" className="space-y-4">
                        <div className="flex justify-between items-center">
                            <div>
                                <p className="text-sm text-muted-foreground">
                                    {mappedCount} of {mappings.length} channels have EPG mappings.
                                    Click a mapped channel to view its programs.
                                </p>
                            </div>
                            <Button variant="outline" onClick={fetchMappings}>
                                <RefreshCw className="mr-2 h-4 w-4" />
                                Refresh
                            </Button>
                        </div>

                        {isLoadingMappings ? (
                            <div className="flex justify-center py-12">
                                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                            </div>
                        ) : (
                            <div className="border rounded-lg">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead>Channel Name</TableHead>
                                            <TableHead>Channel ID</TableHead>
                                            <TableHead>EPG ID</TableHead>
                                            <TableHead>Status</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {mappings.slice(0, 100).map((mapping) => (
                                            <TableRow
                                                key={mapping.channel_id}
                                                className={mapping.has_mapping ? "cursor-pointer hover:bg-muted/50" : ""}
                                                onClick={() => {
                                                    if (mapping.has_mapping) {
                                                        setSelectedMappingChannel(mapping);
                                                        setProgramDialogOpen(true);
                                                    }
                                                }}
                                            >
                                                <TableCell className="font-medium">{mapping.channel_name}</TableCell>
                                                <TableCell className="font-mono text-xs">{mapping.channel_id}</TableCell>
                                                <TableCell className="font-mono text-xs">
                                                    {mapping.epg_id || <span className="text-muted-foreground">—</span>}
                                                </TableCell>
                                                <TableCell>
                                                    {mapping.has_mapping ? (
                                                        <Badge variant="default" className="gap-1">
                                                            <Link className="h-3 w-3" />
                                                            Mapped
                                                        </Badge>
                                                    ) : (
                                                        <Badge variant="secondary" className="gap-1">
                                                            <Unlink className="h-3 w-3" />
                                                            Not Mapped
                                                        </Badge>
                                                    )}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                                {mappings.length > 100 && (
                                    <div className="p-3 text-center text-sm text-muted-foreground border-t">
                                        Showing first 100 of {mappings.length} channels
                                    </div>
                                )}
                            </div>
                        )}
                    </TabsContent>
                </Tabs>

                {/* Program Viewer Dialog */}
                <ProgramViewerDialog
                    open={programDialogOpen}
                    onOpenChange={setProgramDialogOpen}
                    channelMapping={selectedMappingChannel}
                />

                {/* Add Source Dialog */}
                <Dialog open={addDialogOpen} onOpenChange={setAddDialogOpen}>
                    <DialogContent>
                        <DialogHeader>
                            <DialogTitle>Add EPG Source</DialogTitle>
                            <DialogDescription>
                                Add a new EPG XML source URL for automatic program guide data
                            </DialogDescription>
                        </DialogHeader>
                        <div className="space-y-4">
                            <div className="space-y-2">
                                <Label htmlFor="source-name">Source Name</Label>
                                <Input
                                    id="source-name"
                                    placeholder="e.g., EPG Service RU"
                                    value={newSource.name}
                                    onChange={(e) => setNewSource({ ...newSource, name: e.target.value })}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="source-url">EPG URL</Label>
                                <Input
                                    id="source-url"
                                    placeholder="https://example.com/epg.xml"
                                    value={newSource.url}
                                    onChange={(e) => setNewSource({ ...newSource, url: e.target.value })}
                                />
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="source-desc">Description (optional)</Label>
                                <Input
                                    id="source-desc"
                                    placeholder="Description of this EPG source"
                                    value={newSource.description}
                                    onChange={(e) => setNewSource({ ...newSource, description: e.target.value })}
                                />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="space-y-2">
                                    <Label htmlFor="source-priority">Priority</Label>
                                    <Input
                                        id="source-priority"
                                        type="number"
                                        min={1}
                                        value={newSource.priority}
                                        onChange={(e) => setNewSource({ ...newSource, priority: parseInt(e.target.value) || 1 })}
                                    />
                                </div>
                                <div className="flex items-center space-x-2 pt-6">
                                    <Switch
                                        id="source-enabled"
                                        checked={newSource.enabled}
                                        onCheckedChange={(checked) => setNewSource({ ...newSource, enabled: checked })}
                                    />
                                    <Label htmlFor="source-enabled">Enabled</Label>
                                </div>
                            </div>
                        </div>
                        <DialogFooter>
                            <Button variant="outline" onClick={() => setAddDialogOpen(false)}>
                                Cancel
                            </Button>
                            <Button onClick={handleAddSource} disabled={isAddingSource}>
                                {isAddingSource && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                                Add Source
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>
            </div>
        </DashboardLayout>
    );
}

// Program Viewer Dialog Component
interface EPGProgram {
    id: string;
    title: string;
    start: string;
    end: string;
    description?: string;
    category?: string;
    isLive?: boolean;
}

function ProgramViewerDialog({
    open,
    onOpenChange,
    channelMapping,
}: {
    open: boolean;
    onOpenChange: (open: boolean) => void;
    channelMapping: ChannelEPGMapping | null;
}) {
    const [programs, setPrograms] = useState<EPGProgram[]>([]);
    const [isLoading, setIsLoading] = useState(false);

    useEffect(() => {
        if (open && channelMapping?.channel_id) {
            fetchPrograms();
        }
    }, [open, channelMapping]);

    const fetchPrograms = async () => {
        if (!channelMapping) return;

        setIsLoading(true);
        try {
            // Fetch EPG for this channel
            const response = await fetch(
                `${import.meta.env.VITE_API_URL || 'http://localhost:8000'}/api/channels/${channelMapping.channel_id}/epg`,
                {
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("accessToken")}`,
                    },
                }
            );

            if (response.ok) {
                const data = await response.json();
                setPrograms(data.items || []);
            } else {
                setPrograms([]);
            }
        } catch (error) {
            console.error("Failed to fetch programs:", error);
            setPrograms([]);
        } finally {
            setIsLoading(false);
        }
    };

    const formatTime = (isoTime: string): string => {
        try {
            const date = new Date(isoTime);
            return date.toLocaleTimeString("en-US", {
                hour: "2-digit",
                minute: "2-digit",
            });
        } catch {
            return "--:--";
        }
    };

    const calculateDuration = (start: string, end: string): string => {
        try {
            const startDate = new Date(start);
            const endDate = new Date(end);
            const diffMs = endDate.getTime() - startDate.getTime();
            const diffMins = Math.round(diffMs / 60000);
            const hours = Math.floor(diffMins / 60);
            const mins = diffMins % 60;
            if (hours > 0 && mins > 0) return `${hours}h ${mins}m`;
            if (hours > 0) return `${hours}h`;
            return `${mins}m`;
        } catch {
            return "";
        }
    };

    const isCurrentlyLive = (start: string, end: string): boolean => {
        try {
            const now = new Date();
            const startDate = new Date(start);
            const endDate = new Date(end);
            return now >= startDate && now < endDate;
        } catch {
            return false;
        }
    };

    return (
        <Dialog open={open} onOpenChange={onOpenChange}>
            <DialogContent className="max-w-2xl max-h-[80vh]">
                <DialogHeader>
                    <DialogTitle className="flex items-center gap-2">
                        <Tv className="h-5 w-5" />
                        {channelMapping?.channel_name} - Programs
                    </DialogTitle>
                    <DialogDescription>
                        EPG ID: {channelMapping?.epg_id || "Unknown"}
                    </DialogDescription>
                </DialogHeader>

                <div className="overflow-auto max-h-[55vh]">
                    {isLoading ? (
                        <div className="flex justify-center py-12">
                            <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                        </div>
                    ) : programs.length === 0 ? (
                        <div className="flex flex-col items-center justify-center py-12 text-muted-foreground">
                            <AlertCircle className="h-12 w-12 mb-4" />
                            <p>No programs found for this channel</p>
                        </div>
                    ) : (
                        <div className="space-y-2">
                            {programs.map((program, idx) => {
                                const isLive = isCurrentlyLive(program.start, program.end);
                                return (
                                    <div
                                        key={program.id || idx}
                                        className={`flex items-center gap-3 p-3 rounded-lg ${isLive ? "bg-blue-500/10 border border-blue-500/30" : "bg-muted/30"
                                            }`}
                                    >
                                        {/* Time */}
                                        <div className={`text-sm font-mono min-w-[80px] ${isLive ? "text-blue-400" : "text-muted-foreground"}`}>
                                            {formatTime(program.start)}
                                        </div>

                                        {/* Title and Duration */}
                                        <div className="flex-1">
                                            <p className="font-medium">{program.title}</p>
                                            <p className="text-xs text-muted-foreground">
                                                {calculateDuration(program.start, program.end)}
                                                {program.category && ` • ${program.category}`}
                                            </p>
                                        </div>

                                        {/* LIVE badge */}
                                        {isLive && (
                                            <Badge variant="destructive" className="gap-1">
                                                <span className="h-2 w-2 rounded-full bg-white animate-pulse" />
                                                LIVE
                                            </Badge>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                <DialogFooter>
                    <Button variant="outline" onClick={() => onOpenChange(false)}>
                        Close
                    </Button>
                </DialogFooter>
            </DialogContent>
        </Dialog>
    );
}
