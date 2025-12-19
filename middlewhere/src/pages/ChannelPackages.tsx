import { useState, useEffect, useMemo, useCallback } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
    DialogFooter,
} from "@/components/ui/dialog";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Search, Plus, Trash2, Loader2, Package, Tv, Edit } from "lucide-react";
import { toast } from "sonner";
import api, { Package as PackageType, Channel, resolveImageUrl } from "@/lib/api";

export default function ChannelPackages() {
    const [packages, setPackages] = useState<PackageType[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [editingPackage, setEditingPackage] = useState<PackageType | null>(null);
    const [isSaving, setIsSaving] = useState(false);
    const [deletingId, setDeletingId] = useState<string | null>(null);

    // Form state
    const [packageName, setPackageName] = useState("");
    const [packageDescription, setPackageDescription] = useState("");
    const [selectedChannels, setSelectedChannels] = useState<string[]>([]);

    // Channels are loaded lazily when dialog opens
    const [channels, setChannels] = useState<Channel[]>([]);
    const [isLoadingChannels, setIsLoadingChannels] = useState(false);
    const [channelsLoaded, setChannelsLoaded] = useState(false);

    // Filters for channel selection
    const [channelSearch, setChannelSearch] = useState("");
    const [streamerFilter, setStreamerFilter] = useState("all");
    const [categoryFilter, setCategoryFilter] = useState("all");

    // Fetch packages on mount (lightweight)
    const fetchPackages = async () => {
        try {
            const packagesRes = await api.packages.list();
            setPackages(packagesRes.packages);
        } catch (error) {
            console.error("Failed to fetch packages:", error);
            toast.error("Failed to load packages");
        } finally {
            setIsLoading(false);
        }
    };

    // Fetch channels lazily when dialog opens
    const fetchChannels = useCallback(async () => {
        if (channelsLoaded) return; // Already loaded

        setIsLoadingChannels(true);
        try {
            const channelsRes = await api.channels.list({ limit: 1000 });
            setChannels(channelsRes.items);
            setChannelsLoaded(true);
        } catch (error) {
            console.error("Failed to fetch channels:", error);
            toast.error("Failed to load channels");
        } finally {
            setIsLoadingChannels(false);
        }
    }, [channelsLoaded]);

    useEffect(() => {
        fetchPackages();
    }, []);

    // Load channels when dialog opens
    useEffect(() => {
        if (dialogOpen && !channelsLoaded) {
            fetchChannels();
        }
    }, [dialogOpen, channelsLoaded, fetchChannels]);

    // Extract unique streamers and categories from channels
    const streamerNames = useMemo(() => {
        const names = [...new Set(channels.map(ch => ch.streamerName).filter(Boolean))] as string[];
        return names.sort();
    }, [channels]);

    const categories = useMemo(() => {
        const cats = [...new Set(channels.map(ch => ch.group).filter(Boolean))] as string[];
        return cats.sort();
    }, [channels]);

    // Filter channels for selection - memoized with limited results
    const filteredChannels = useMemo(() => {
        let filtered = channels;

        // Apply filters
        if (channelSearch) {
            const search = channelSearch.toLowerCase();
            filtered = filtered.filter(channel =>
                channel.name.toLowerCase().includes(search) ||
                channel.id.toLowerCase().includes(search)
            );
        }
        if (streamerFilter !== "all") {
            filtered = filtered.filter(ch => ch.streamerName === streamerFilter);
        }
        if (categoryFilter !== "all") {
            filtered = filtered.filter(ch => ch.group === categoryFilter);
        }

        return filtered;
    }, [channels, channelSearch, streamerFilter, categoryFilter]);

    // Limit visible channels for performance (show first 50)
    const visibleChannels = useMemo(() => {
        return filteredChannels.slice(0, 100);
    }, [filteredChannels]);

    const openCreateDialog = () => {
        setEditingPackage(null);
        setPackageName("");
        setPackageDescription("");
        setSelectedChannels([]);
        setChannelSearch("");
        setStreamerFilter("all");
        setCategoryFilter("all");
        setDialogOpen(true);
    };

    const openEditDialog = (pkg: PackageType) => {
        setEditingPackage(pkg);
        setPackageName(pkg.name);
        setPackageDescription(pkg.description || "");
        setSelectedChannels(pkg.channel_ids || []);
        setChannelSearch("");
        setStreamerFilter("all");
        setCategoryFilter("all");
        setDialogOpen(true);
    };

    const toggleChannel = (channelId: string) => {
        setSelectedChannels(prev =>
            prev.includes(channelId)
                ? prev.filter(id => id !== channelId)
                : [...prev, channelId]
        );
    };

    const selectAllFiltered = () => {
        const filteredIds = filteredChannels.map(ch => ch.id);
        setSelectedChannels(prev => [...new Set([...prev, ...filteredIds])]);
    };

    const deselectAllFiltered = () => {
        const filteredIds = new Set(filteredChannels.map(ch => ch.id));
        setSelectedChannels(prev => prev.filter(id => !filteredIds.has(id)));
    };

    const handleSave = async () => {
        if (!packageName.trim()) {
            toast.error("Please enter a package name");
            return;
        }

        setIsSaving(true);
        try {
            const data = {
                name: packageName,
                description: packageDescription,
                channel_ids: selectedChannels,
            };

            if (editingPackage) {
                await api.packages.update(editingPackage.id, data);
                toast.success("Package updated");
            } else {
                await api.packages.create(data);
                toast.success("Package created");
            }
            setDialogOpen(false);
            fetchPackages();
        } catch (error) {
            console.error("Failed to save:", error);
            toast.error("Failed to save package");
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async (packageId: string) => {
        setDeletingId(packageId);
        try {
            await api.packages.delete(packageId);
            toast.success("Package deleted");
            setPackages(packages.filter(p => p.id !== packageId));
        } catch (error) {
            console.error("Failed to delete:", error);
            toast.error("Failed to delete package");
        } finally {
            setDeletingId(null);
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
                        <h1 className="text-3xl font-bold tracking-tight">Channel Packages</h1>
                        <p className="text-muted-foreground">
                            Create and manage channel packages for your subscribers
                        </p>
                    </div>
                    <Button onClick={openCreateDialog} className="gap-2">
                        <Plus className="h-4 w-4" />
                        New Package
                    </Button>
                </div>

                {packages.length > 0 ? (
                    <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
                        {packages.map((pkg) => (
                            <Card key={pkg.id}>
                                <CardHeader>
                                    <div className="flex items-start justify-between">
                                        <div>
                                            <CardTitle className="flex items-center gap-2">
                                                <Package className="h-5 w-5" />
                                                {pkg.name}
                                            </CardTitle>
                                            <CardDescription>{pkg.description || "No description"}</CardDescription>
                                        </div>
                                        <Badge variant="secondary">
                                            {pkg.channel_ids?.length || 0} channels
                                        </Badge>
                                    </div>
                                </CardHeader>
                                <CardContent>
                                    <div className="flex gap-2">
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            className="flex-1"
                                            onClick={() => openEditDialog(pkg)}
                                        >
                                            <Edit className="h-4 w-4 mr-2" />
                                            Edit
                                        </Button>
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            className="text-destructive"
                                            onClick={() => handleDelete(pkg.id)}
                                            disabled={deletingId === pkg.id}
                                        >
                                            {deletingId === pkg.id ? (
                                                <Loader2 className="h-4 w-4 animate-spin" />
                                            ) : (
                                                <Trash2 className="h-4 w-4" />
                                            )}
                                        </Button>
                                    </div>
                                </CardContent>
                            </Card>
                        ))}
                    </div>
                ) : (
                    <div className="text-center py-12">
                        <Package className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
                        <h3 className="text-lg font-medium mb-2">No packages yet</h3>
                        <p className="text-muted-foreground mb-4">
                            Create your first channel package to organize channels for subscribers.
                        </p>
                        <Button onClick={openCreateDialog}>
                            <Plus className="h-4 w-4 mr-2" />
                            Create Package
                        </Button>
                    </div>
                )}

                {/* Create/Edit Dialog */}
                <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
                    <DialogContent className="max-w-4xl max-h-[90vh] overflow-hidden flex flex-col">
                        <DialogHeader>
                            <DialogTitle>
                                {editingPackage ? "Edit Package" : "Create New Package"}
                            </DialogTitle>
                            <DialogDescription>
                                {editingPackage
                                    ? "Update the package details and channel selection."
                                    : "Create a new channel package by selecting channels to include."}
                            </DialogDescription>
                        </DialogHeader>

                        <div className="flex-1 overflow-auto space-y-4 py-4">
                            {/* Package Details */}
                            <div className="grid gap-4 md:grid-cols-2">
                                <div className="space-y-2">
                                    <Label htmlFor="pkg-name">Package Name</Label>
                                    <Input
                                        id="pkg-name"
                                        placeholder="e.g., Premium Sports"
                                        value={packageName}
                                        onChange={(e) => setPackageName(e.target.value)}
                                    />
                                </div>
                                <div className="space-y-2">
                                    <Label htmlFor="pkg-desc">Description</Label>
                                    <Input
                                        id="pkg-desc"
                                        placeholder="e.g., All sports channels"
                                        value={packageDescription}
                                        onChange={(e) => setPackageDescription(e.target.value)}
                                    />
                                </div>
                            </div>

                            {/* Channel Selection */}
                            <div className="space-y-4">
                                <div className="flex items-center justify-between">
                                    <Label>Select Channels ({selectedChannels.length} selected)</Label>
                                    <div className="flex gap-2">
                                        <Button variant="outline" size="sm" onClick={selectAllFiltered}>
                                            Select All ({filteredChannels.length})
                                        </Button>
                                        <Button variant="outline" size="sm" onClick={deselectAllFiltered}>
                                            Deselect All
                                        </Button>
                                    </div>
                                </div>

                                {/* Filters Row */}
                                <div className="flex gap-4 flex-wrap">
                                    <div className="relative flex-1 min-w-[200px]">
                                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                                        <Input
                                            placeholder="Search channels..."
                                            className="pl-10"
                                            value={channelSearch}
                                            onChange={(e) => setChannelSearch(e.target.value)}
                                        />
                                    </div>
                                    <Select value={streamerFilter} onValueChange={setStreamerFilter}>
                                        <SelectTrigger className="w-[180px]">
                                            <SelectValue placeholder="Filter by streamer" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="all">All Streamers</SelectItem>
                                            {streamerNames.map(name => (
                                                <SelectItem key={name} value={name}>{name}</SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                    <Select value={categoryFilter} onValueChange={setCategoryFilter}>
                                        <SelectTrigger className="w-[180px]">
                                            <SelectValue placeholder="Filter by category" />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="all">All Categories</SelectItem>
                                            {categories.map(cat => (
                                                <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                                            ))}
                                        </SelectContent>
                                    </Select>
                                </div>

                                {/* Channel List */}
                                {isLoadingChannels ? (
                                    <div className="flex items-center justify-center py-12 border rounded-lg">
                                        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground mr-2" />
                                        <span className="text-muted-foreground">Loading channels...</span>
                                    </div>
                                ) : (
                                    <div className="border rounded-lg max-h-[300px] overflow-auto">
                                        <div className="grid gap-2 p-3 grid-cols-1 md:grid-cols-2 lg:grid-cols-3">
                                            {visibleChannels.map((channel) => (
                                                <div
                                                    key={channel.id}
                                                    className={`flex items-center gap-2 p-2 rounded cursor-pointer transition-colors ${selectedChannels.includes(channel.id)
                                                            ? "bg-primary/10 border border-primary"
                                                            : "bg-muted/50 hover:bg-muted"
                                                        }`}
                                                    onClick={() => toggleChannel(channel.id)}
                                                >
                                                    <Checkbox
                                                        checked={selectedChannels.includes(channel.id)}
                                                        onCheckedChange={() => toggleChannel(channel.id)}
                                                        onClick={(e) => e.stopPropagation()}
                                                    />
                                                    <div className="h-6 w-6 rounded bg-background flex items-center justify-center overflow-hidden flex-shrink-0">
                                                        {channel.logo ? (
                                                            <img
                                                                src={resolveImageUrl(channel.logo)}
                                                                alt=""
                                                                className="h-full w-full object-cover"
                                                                loading="lazy"
                                                            />
                                                        ) : (
                                                            <Tv className="h-3 w-3 text-muted-foreground" />
                                                        )}
                                                    </div>
                                                    <span className="text-sm truncate flex-1">{channel.name}</span>
                                                </div>
                                            ))}
                                        </div>
                                        {filteredChannels.length === 0 && (
                                            <div className="text-center text-muted-foreground py-8">
                                                No channels match your filters
                                            </div>
                                        )}
                                    </div>
                                )}

                                <p className="text-sm text-muted-foreground">
                                    Showing {visibleChannels.length} of {filteredChannels.length} channels
                                    {filteredChannels.length < channels.length && ` (${channels.length} total)`}
                                    {filteredChannels.length > 100 && " - use filters to narrow down"}
                                </p>
                            </div>
                        </div>

                        <DialogFooter>
                            <Button variant="outline" onClick={() => setDialogOpen(false)}>
                                Cancel
                            </Button>
                            <Button onClick={handleSave} disabled={isSaving}>
                                {isSaving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                                {editingPackage ? "Update Package" : "Create Package"}
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>
            </div>
        </DashboardLayout>
    );
}
