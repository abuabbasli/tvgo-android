import { useState, useEffect, useMemo, useRef } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Search, Plus, Trash2, Loader2, Tv, Edit, Save, Upload,
  LayoutGrid, List, GripVertical, ImageIcon
} from "lucide-react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { MoreVertical } from "lucide-react";
import { toast } from "sonner";
import api, { Channel, Streamer, resolveImageUrl } from "@/lib/api";

// DnD Imports
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from '@dnd-kit/core';
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
  rectSortingStrategy,
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

// --- Sortable Components ---

interface SortableProps {
  channel: Channel;
  openEditDialog: (c: Channel) => void;
  handleDelete: (id: string) => void;
  deletingId: string | null;
}

const SortableTableRow = ({ channel, openEditDialog, handleDelete, deletingId }: SortableProps) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id: channel.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    zIndex: isDragging ? 10 : 1,
    opacity: isDragging ? 0.8 : 1,
  };

  return (
    <TableRow ref={setNodeRef} style={style}>
      <TableCell className="w-[50px]">
        <div {...attributes} {...listeners} className="cursor-grab hover:text-primary">
          <GripVertical className="h-4 w-4 text-muted-foreground" />
        </div>
      </TableCell>
      <TableCell>
        <div className="h-10 w-10 rounded bg-muted flex items-center justify-center overflow-hidden">
          {channel.logo ? (
            <img
              src={resolveImageUrl(channel.logo)}
              alt={channel.name}
              className="h-full w-full object-cover"
              loading="lazy"
              onError={(e) => {
                e.currentTarget.style.display = 'none';
              }}
            />) : (
            <Tv className="h-5 w-5 text-muted-foreground" />
          )}
        </div>
      </TableCell>
      <TableCell>
        <div>
          <p className="font-medium">{channel.name}</p>
          <p className="text-xs text-muted-foreground font-mono truncate max-w-[150px]">{channel.id}</p>
        </div>
      </TableCell>
      <TableCell>
        {channel.group && (
          <Badge variant="secondary">{channel.group}</Badge>
        )}
      </TableCell>
      <TableCell>
        {channel.streamerName && (
          <Badge variant="outline">{channel.streamerName}</Badge>
        )}
      </TableCell>
      <TableCell>
        <span className="text-sm text-muted-foreground font-mono">
          {channel.order ?? "-"}
        </span>
      </TableCell>
      <TableCell>
        <div className="flex gap-1 flex-wrap">
          {channel.badges?.map((badge) => (
            <Badge key={badge} variant="outline" className="text-xs">{badge}</Badge>
          ))}
        </div>
      </TableCell>
      <TableCell className="text-right">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon">
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => openEditDialog(channel)}>
              <Edit className="mr-2 h-4 w-4" />
              Edit
            </DropdownMenuItem>
            <DropdownMenuItem
              className="text-destructive"
              onClick={() => handleDelete(channel.id)}
              disabled={deletingId === channel.id}
            >
              {deletingId === channel.id ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <Trash2 className="mr-2 h-4 w-4" />
              )}
              Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </TableCell>
    </TableRow>
  );
};

const SortableGridCard = ({ channel, openEditDialog, handleDelete, deletingId }: SortableProps) => {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging
  } = useSortable({ id: channel.id });

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    zIndex: isDragging ? 10 : 1,
    opacity: isDragging ? 0.8 : 1,
  };

  return (
    <div ref={setNodeRef} style={style} className="group relative bg-card border rounded-lg overflow-hidden shadow-sm hover:shadow-md transition-all">
      <div className="absolute top-2 right-2 z-10 opacity-0 group-hover:opacity-100 transition-opacity">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="secondary" size="icon" className="h-8 w-8">
              <MoreVertical className="h-4 w-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onClick={() => openEditDialog(channel)}>
              <Edit className="mr-2 h-4 w-4" /> Edit
            </DropdownMenuItem>
            <DropdownMenuItem onClick={() => handleDelete(channel.id)} className="text-destructive">
              <Trash2 className="mr-2 h-4 w-4" /> Delete
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      {/* Drag Handle */}
      <div {...attributes} {...listeners} className="absolute top-2 left-2 z-10 cursor-grab p-1 bg-background/80 backdrop-blur rounded opacity-0 group-hover:opacity-100 transition-opacity hover:bg-background">
        <GripVertical className="h-4 w-4" />
      </div>

      <div className="aspect-video bg-muted flex items-center justify-center relative">
        {channel.logo ? (
          <img src={resolveImageUrl(channel.logo)} alt={channel.name} className="h-full w-full object-cover" />
        ) : (
          <Tv className="h-8 w-8 text-muted-foreground" />
        )}
        {channel.badges && channel.badges.length > 0 && (
          <div className="absolute bottom-2 left-2 flex gap-1">
            {channel.badges.slice(0, 2).map(b => (
              <Badge key={b} variant="secondary" className="text-[10px] h-5 px-1">{b}</Badge>
            ))}
          </div>
        )}
      </div>

      <div className="p-3">
        <div className="flex justify-between items-start mb-1">
          <h3 className="font-semibold truncate pr-2" title={channel.name}>{channel.name}</h3>
          <span className="text-xs font-mono text-muted-foreground bg-muted px-1.5 py-0.5 rounded">#{channel.order || '-'}</span>
        </div>
        <div className="flex items-center gap-2 text-xs text-muted-foreground mb-2">
          <span className="truncate max-w-[100px]">{channel.group || 'No Group'}</span>
          <span>•</span>
          <span className="truncate max-w-[100px]">{channel.streamerName || 'Unknown'}</span>
        </div>
      </div>
    </div>
  );
}

export default function Channels() {
  const [channels, setChannels] = useState<Channel[]>([]);
  const [streamers, setStreamers] = useState<Streamer[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [groupFilter, setGroupFilter] = useState("all");
  const [streamerFilter, setStreamerFilter] = useState("all");
  const [groups, setGroups] = useState<string[]>([]);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<"list" | "grid">("list");

  // Edit dialog state
  const [editDialogOpen, setEditDialogOpen] = useState(false);
  const [editingChannel, setEditingChannel] = useState<Channel | null>(null);
  const [editForm, setEditForm] = useState({
    name: "",
    group: "",
    logo: "",
    order: "",
    country: "",
    badges: "",
  });
  const [isSaving, setIsSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // DnD Sensors
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    })
  );

  const fetchData = async () => {
    try {
      const [channelsRes, streamersRes] = await Promise.all([
        api.channels.list({ limit: 1000 }), // Increased limit to ensure full ordering context
        api.streamers.list(),
      ]);
      setChannels(channelsRes.items);
      setStreamers(streamersRes.items);

      // Extract unique groups
      const uniqueGroups = [...new Set(channelsRes.items.map(ch => ch.group).filter(Boolean))] as string[];
      setGroups(uniqueGroups.sort());
    } catch (error) {
      console.error("Failed to fetch data:", error);
      toast.error("Failed to load channels");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  // Get unique streamer names from channels
  const streamerNames = useMemo(() => {
    const names = [...new Set(channels.map(ch => ch.streamerName).filter(Boolean))] as string[];
    return names.sort();
  }, [channels]);

  const handleDelete = async (channelId: string) => {
    setDeletingId(channelId);
    try {
      await api.channels.delete(channelId);
      toast.success("Channel deleted");
      setChannels(channels.filter(ch => ch.id !== channelId));
    } catch (error) {
      console.error("Failed to delete:", error);
      toast.error("Failed to delete channel");
    } finally {
      setDeletingId(null);
    }
  };

  const openEditDialog = (channel: Channel) => {
    setEditingChannel(channel);
    setEditForm({
      name: channel.name || "",
      group: channel.group || "",
      logo: channel.logo || "",
      order: channel.order?.toString() || "",
      country: channel.country || "",
      badges: channel.badges?.join(", ") || "",
    });
    setEditDialogOpen(true);
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      toast.error("Please select an image file");
      return;
    }

    if (file.size > 5 * 1024 * 1024) {
      toast.error("Image must be smaller than 5MB");
      return;
    }

    setIsUploading(true);
    try {
      const result = await api.upload.image(file);
      setEditForm({ ...editForm, logo: result.url });
      toast.success("Image uploaded successfully");
    } catch (error) {
      console.error("Upload failed:", error);
      toast.error("Failed to upload image");
    } finally {
      setIsUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const handleSave = async () => {
    if (!editingChannel) return;

    setIsSaving(true);
    try {
      const updateData: Record<string, any> = {
        name: editForm.name || undefined,
        group: editForm.group || undefined,
        logo_url: editForm.logo || undefined,
        order: editForm.order ? parseInt(editForm.order) : undefined,
        country: editForm.country || undefined,
        badges: editForm.badges ? editForm.badges.split(",").map(b => b.trim()).filter(Boolean) : undefined,
      };

      await api.channels.update(editingChannel.id, updateData);
      toast.success("Channel updated");
      setEditDialogOpen(false);
      fetchData();
    } catch (error) {
      console.error("Failed to update:", error);
      toast.error("Failed to update channel");
    } finally {
      setIsSaving(false);
    }
  };

  // Filter Logic
  const filteredChannels = useMemo(() => {
    return channels.filter(channel => {
      const matchesSearch = !search ||
        channel.name.toLowerCase().includes(search.toLowerCase()) ||
        channel.id.toLowerCase().includes(search.toLowerCase());
      const matchesGroup = groupFilter === "all" || channel.group === groupFilter;
      const matchesStreamer = streamerFilter === "all" || channel.streamerName === streamerFilter;
      return matchesSearch && matchesGroup && matchesStreamer;
    });
  }, [channels, search, groupFilter, streamerFilter]);

  const isFiltered = search !== "" || groupFilter !== "all" || streamerFilter !== "all";

  // Drag End Handler
  const handleDragEnd = async (event: DragEndEvent) => {
    const { active, over } = event;

    if (isFiltered) {
      toast.error("Cannot reorder while filtered");
      return;
    }

    if (active.id !== over?.id) {
      setChannels((items) => {
        const oldIndex = items.findIndex((i) => i.id === active.id);
        const newIndex = items.findIndex((i) => i.id === over?.id);

        const newOrder = arrayMove(items, oldIndex, newIndex);

        // Calculate new order values based on index + 1
        const updates = newOrder.map((ch, idx) => ({ id: ch.id, order: idx + 1 }));

        // Optimistically update
        api.channels.reorder(updates).catch(err => {
          console.error("Reorder failed", err);
          toast.error("Failed to save order");
          // Revert on failure? (Complex, skipping for now)
        });

        // Return new state with updated order properties
        return newOrder.map((ch, idx) => ({ ...ch, order: idx + 1 }));
      });
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
            <h1 className="text-3xl font-bold tracking-tight">Channel Management</h1>
            <p className="text-muted-foreground">
              Manage your channel lineup ({channels.length} total)
            </p>
          </div>
          <div className="flex gap-2">
            <div className="flex items-center border rounded-md bg-muted/20">
              <Button
                variant={viewMode === "list" ? "secondary" : "ghost"}
                size="icon"
                onClick={() => setViewMode("list")}
                className="h-9 w-9"
              >
                <List className="h-4 w-4" />
              </Button>
              <Button
                variant={viewMode === "grid" ? "secondary" : "ghost"}
                size="icon"
                onClick={() => setViewMode("grid")}
                className="h-9 w-9"
              >
                <LayoutGrid className="h-4 w-4" />
              </Button>
            </div>
            <Button className="gap-2" disabled>
              <Plus className="h-4 w-4" />
              Add Channel
            </Button>
          </div>
        </div>

        <div className="flex items-center gap-4 flex-wrap">
          <div className="relative flex-1 min-w-[200px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              placeholder="Search channels..."
              className="pl-10"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <Select value={groupFilter} onValueChange={setGroupFilter}>
            <SelectTrigger className="w-[180px]">
              <SelectValue placeholder="Filter by group" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Categories</SelectItem>
              {groups.map(group => (
                <SelectItem key={group} value={group}>{group}</SelectItem>
              ))}
            </SelectContent>
          </Select>
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
        </div>

        {isFiltered && (
          <div className="bg-yellow-50 text-yellow-800 p-2 rounded text-sm px-4 border border-yellow-200">
            Drag and drop reordering is disabled while filters are active. Clear filters to reorder.
          </div>
        )}

        {filteredChannels.length > 0 ? (
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
          >
            <SortableContext
              items={filteredChannels.map(c => c.id)}
              strategy={viewMode === "list" ? verticalListSortingStrategy : rectSortingStrategy}
              disabled={isFiltered}
            >
              {viewMode === "list" ? (
                <div className="border rounded-lg">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-[50px]"></TableHead>
                        <TableHead className="w-[60px]">Logo</TableHead>
                        <TableHead>Name</TableHead>
                        <TableHead>Category</TableHead>
                        <TableHead>Streamer</TableHead>
                        <TableHead className="w-[80px]">Order</TableHead>
                        <TableHead>Badges</TableHead>
                        <TableHead className="text-right">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredChannels.map((channel) => (
                        <SortableTableRow
                          key={channel.id}
                          channel={channel}
                          openEditDialog={openEditDialog}
                          handleDelete={handleDelete}
                          deletingId={deletingId}
                        />
                      ))}
                    </TableBody>
                  </Table>
                </div>
              ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                  {filteredChannels.map((channel) => (
                    <SortableGridCard
                      key={channel.id}
                      channel={channel}
                      openEditDialog={openEditDialog}
                      handleDelete={handleDelete}
                      deletingId={deletingId}
                    />
                  ))}
                </div>
              )}
            </SortableContext>
          </DndContext>
        ) : (
          <div className="text-center py-12">
            <Tv className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">
              {channels.length === 0
                ? "No channels imported yet. Add a streamer to import channels."
                : "No channels match your search criteria."}
            </p>
          </div>
        )}

        <div className="flex items-center justify-between text-sm text-muted-foreground">
          <div>Showing {filteredChannels.length} of {channels.length} channels</div>
        </div>

        {/* Edit Dialog */}
        <Dialog open={editDialogOpen} onOpenChange={setEditDialogOpen}>
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>Edit Channel</DialogTitle>
              <DialogDescription>
                Update channel details. Changes will be saved to the database.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="edit-name">Channel Name</Label>
                <Input
                  id="edit-name"
                  value={editForm.name}
                  onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
                  placeholder="Channel name"
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="edit-group">Category / Group</Label>
                  <Input
                    id="edit-group"
                    value={editForm.group}
                    onChange={(e) => setEditForm({ ...editForm, group: e.target.value })}
                    placeholder="e.g., Sports, News"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="edit-order">Order Number</Label>
                  <Input
                    id="edit-order"
                    type="number"
                    value={editForm.order}
                    onChange={(e) => setEditForm({ ...editForm, order: e.target.value })}
                    placeholder="e.g., 1, 2, 3"
                  />
                </div>
              </div>

              {/* Logo Upload Section */}
              <div className="space-y-2">
                <Label>Channel Logo</Label>
                <div className="flex items-center gap-4">
                  <div className="h-16 w-16 border rounded bg-muted flex items-center justify-center overflow-hidden shrink-0">
                    {editForm.logo ? (
                      <img
                        src={resolveImageUrl(editForm.logo)}
                        alt="Preview"
                        className="h-full w-full object-cover"
                      />
                    ) : (
                      <ImageIcon className="h-8 w-8 text-muted-foreground" />
                    )}
                  </div>
                  <div className="flex-1 space-y-2">
                    <Input
                      id="edit-logo"
                      value={editForm.logo}
                      onChange={(e) => setEditForm({ ...editForm, logo: e.target.value })}
                      placeholder="Logo URL"
                    />
                    <div className="flex items-center gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="w-full"
                        disabled={isUploading}
                        onClick={() => fileInputRef.current?.click()}
                      >
                        {isUploading ? (
                          <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        ) : (
                          <Upload className="mr-2 h-4 w-4" />
                        )}
                        Upload Image
                      </Button>
                      <input
                        type="file"
                        ref={fileInputRef}
                        className="hidden"
                        accept="image/*"
                        onChange={handleImageUpload}
                      />
                    </div>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="edit-country">Country</Label>
                  <Input
                    id="edit-country"
                    value={editForm.country}
                    onChange={(e) => setEditForm({ ...editForm, country: e.target.value })}
                    placeholder="e.g., US, UK"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="edit-badges">Badges (comma split)</Label>
                  <Input
                    id="edit-badges"
                    value={editForm.badges}
                    onChange={(e) => setEditForm({ ...editForm, badges: e.target.value })}
                    placeholder="HD, 4K, LIVE"
                  />
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-2">
                <Button variant="outline" onClick={() => setEditDialogOpen(false)} disabled={isSaving}>
                  Cancel
                </Button>
                <Button onClick={handleSave} disabled={isSaving}>
                  {isSaving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  <Save className="mr-2 h-4 w-4" />
                  Save Changes
                </Button>
              </div>
            </div>
          </DialogContent>
        </Dialog>
      </div>
    </DashboardLayout>
  );
}
