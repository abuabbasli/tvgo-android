import { useState, useEffect, useRef } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
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
    DialogHeader,
    DialogTitle,
    DialogDescription,
} from "@/components/ui/dialog";
import {
    Table,
    TableBody,
    TableCell,
    TableHead,
    TableHeader,
    TableRow,
} from "@/components/ui/table";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { toast } from "sonner";
import {
    Plus,
    Pencil,
    Trash2,
    Search,
    Grid,
    List,
    Upload,
    GripVertical,
    Gamepad2,
    ExternalLink,
} from "lucide-react";
import api, { Game, GameCreate, GameUpdate, resolveImageUrl } from "@/lib/api";

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
    game: Game;
    openEditDialog: (g: Game) => void;
    handleDelete: (id: string) => void;
    deletingId: string | null;
}

function SortableTableRow({ game, openEditDialog, handleDelete, deletingId }: SortableProps) {
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        transition,
        isDragging,
    } = useSortable({ id: game.id });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
    };

    return (
        <TableRow ref={setNodeRef} style={style} className="group">
            <TableCell className="w-10">
                <div {...attributes} {...listeners} className="cursor-grab active:cursor-grabbing p-1">
                    <GripVertical className="h-4 w-4 text-muted-foreground" />
                </div>
            </TableCell>
            <TableCell className="w-16">
                {game.imageUrl ? (
                    <img
                        src={resolveImageUrl(game.imageUrl)}
                        alt={game.name}
                        className="w-12 h-12 rounded object-cover"
                    />
                ) : (
                    <div className="w-12 h-12 rounded bg-muted flex items-center justify-center">
                        <Gamepad2 className="w-6 h-6 text-muted-foreground" />
                    </div>
                )}
            </TableCell>
            <TableCell className="font-medium">{game.name}</TableCell>
            <TableCell>{game.category || "-"}</TableCell>
            <TableCell>
                <Badge variant={game.isActive ? "default" : "secondary"}>
                    {game.isActive ? "Active" : "Inactive"}
                </Badge>
            </TableCell>
            <TableCell className="max-w-xs truncate">
                <a href={game.gameUrl} target="_blank" rel="noopener noreferrer" className="text-blue-500 hover:underline flex items-center gap-1">
                    <span className="truncate max-w-[200px]">{game.gameUrl}</span>
                    <ExternalLink className="w-3 h-3 flex-shrink-0" />
                </a>
            </TableCell>
            <TableCell className="text-right">
                <div className="flex gap-2 justify-end">
                    <Button variant="ghost" size="icon" onClick={() => openEditDialog(game)}>
                        <Pencil className="h-4 w-4" />
                    </Button>
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleDelete(game.id)}
                        disabled={deletingId === game.id}
                    >
                        <Trash2 className="h-4 w-4 text-destructive" />
                    </Button>
                </div>
            </TableCell>
        </TableRow>
    );
}

function SortableGridCard({ game, openEditDialog, handleDelete, deletingId }: SortableProps) {
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        transition,
        isDragging,
    } = useSortable({ id: game.id });

    const style = {
        transform: CSS.Transform.toString(transform),
        transition,
        opacity: isDragging ? 0.5 : 1,
    };

    return (
        <Card ref={setNodeRef} style={style} className="overflow-hidden group relative">
            <div {...attributes} {...listeners} className="absolute top-2 left-2 z-10 cursor-grab active:cursor-grabbing bg-black/50 rounded p-1">
                <GripVertical className="h-4 w-4 text-white" />
            </div>
            <div className="aspect-square relative bg-muted">
                {game.imageUrl ? (
                    <img
                        src={resolveImageUrl(game.imageUrl)}
                        alt={game.name}
                        className="w-full h-full object-cover"
                    />
                ) : (
                    <div className="w-full h-full flex items-center justify-center">
                        <Gamepad2 className="w-16 h-16 text-muted-foreground" />
                    </div>
                )}
                {!game.isActive && (
                    <div className="absolute inset-0 bg-black/50 flex items-center justify-center">
                        <Badge variant="secondary">Inactive</Badge>
                    </div>
                )}
            </div>
            <CardContent className="p-3">
                <h3 className="font-medium truncate">{game.name}</h3>
                <p className="text-sm text-muted-foreground">{game.category || "No category"}</p>
                <div className="flex gap-2 mt-2 opacity-0 group-hover:opacity-100 transition-opacity">
                    <Button variant="outline" size="sm" onClick={() => openEditDialog(game)}>
                        <Pencil className="h-3 w-3 mr-1" /> Edit
                    </Button>
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleDelete(game.id)}
                        disabled={deletingId === game.id}
                        className="text-destructive"
                    >
                        <Trash2 className="h-3 w-3" />
                    </Button>
                </div>
            </CardContent>
        </Card>
    );
}

// --- Main Component ---

export default function Games() {
    const [games, setGames] = useState<Game[]>([]);
    const [categories, setCategories] = useState<string[]>([]);
    const [loading, setLoading] = useState(true);
    const [dialogOpen, setDialogOpen] = useState(false);
    const [editingGame, setEditingGame] = useState<Game | null>(null);
    const [saving, setSaving] = useState(false);
    const [deletingId, setDeletingId] = useState<string | null>(null);
    const [viewMode, setViewMode] = useState<"grid" | "list">("grid");
    const [searchTerm, setSearchTerm] = useState("");
    const [filterCategory, setFilterCategory] = useState<string>("all");

    // Form state
    const [formData, setFormData] = useState({
        id: "",
        name: "",
        description: "",
        image_url: "",
        game_url: "",
        category: "",
        is_active: true,
    });

    const fileInputRef = useRef<HTMLInputElement>(null);

    // DnD sensors
    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        })
    );

    // Fetch data
    useEffect(() => {
        fetchData();
    }, []);

    async function fetchData() {
        try {
            setLoading(true);
            const [gamesRes, categoriesRes] = await Promise.all([
                (api as any).games.list(),
                (api as any).games.getCategories(),
            ]);
            setGames(gamesRes.items);
            setCategories(categoriesRes);
        } catch (error) {
            console.error("Failed to fetch games:", error);
            toast.error("Failed to load games");
        } finally {
            setLoading(false);
        }
    }

    // Filter games
    const filteredGames = games.filter((game) => {
        const matchesSearch = game.name.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesCategory = filterCategory === "all" || game.category === filterCategory;
        return matchesSearch && matchesCategory;
    });

    // Open add dialog
    function openAddDialog() {
        setEditingGame(null);
        setFormData({
            id: "",
            name: "",
            description: "",
            image_url: "",
            game_url: "",
            category: "",
            is_active: true,
        });
        setDialogOpen(true);
    }

    // Open edit dialog
    function openEditDialog(game: Game) {
        setEditingGame(game);
        setFormData({
            id: game.id,
            name: game.name,
            description: game.description || "",
            image_url: game.imageUrl || "",
            game_url: game.gameUrl,
            category: game.category || "",
            is_active: game.isActive,
        });
        setDialogOpen(true);
    }

    // Handle image upload
    async function handleImageUpload(e: React.ChangeEvent<HTMLInputElement>) {
        const file = e.target.files?.[0];
        if (!file) return;

        try {
            const formDataUpload = new FormData();
            formDataUpload.append("file", file);

            const response = await fetch(
                `${import.meta.env.VITE_API_URL || 'http://localhost:8000'}/api/admin/upload-image`,
                {
                    method: "POST",
                    headers: {
                        Authorization: `Bearer ${localStorage.getItem("accessToken")}`,
                    },
                    body: formDataUpload,
                }
            );

            if (!response.ok) throw new Error("Upload failed");

            const data = await response.json();
            setFormData((prev) => ({ ...prev, image_url: data.url }));
            toast.success("Image uploaded");
        } catch (error) {
            console.error("Upload error:", error);
            toast.error("Failed to upload image");
        }
    }

    // Save game
    async function handleSave() {
        if (!formData.name.trim() || !formData.game_url.trim()) {
            toast.error("Name and Game URL are required");
            return;
        }

        try {
            setSaving(true);

            if (editingGame) {
                // Update
                const updateData: GameUpdate = {
                    name: formData.name,
                    description: formData.description || undefined,
                    image_url: formData.image_url || undefined,
                    game_url: formData.game_url,
                    category: formData.category || undefined,
                    is_active: formData.is_active,
                };
                await (api as any).games.update(editingGame.id, updateData);
                toast.success("Game updated");
            } else {
                // Create
                const createData: GameCreate = {
                    id: formData.id || formData.name.toLowerCase().replace(/[^a-z0-9]+/g, "-"),
                    name: formData.name,
                    description: formData.description || undefined,
                    image_url: formData.image_url || undefined,
                    game_url: formData.game_url,
                    category: formData.category || undefined,
                    is_active: formData.is_active,
                    order: games.length,
                };
                await (api as any).games.create(createData);
                toast.success("Game created");
            }

            setDialogOpen(false);
            fetchData();
        } catch (error) {
            console.error("Save error:", error);
            toast.error("Failed to save game");
        } finally {
            setSaving(false);
        }
    }

    // Delete game
    async function handleDelete(id: string) {
        if (!confirm("Are you sure you want to delete this game?")) return;

        try {
            setDeletingId(id);
            await (api as any).games.delete(id);
            toast.success("Game deleted");
            fetchData();
        } catch (error) {
            console.error("Delete error:", error);
            toast.error("Failed to delete game");
        } finally {
            setDeletingId(null);
        }
    }

    // Handle drag end
    async function handleDragEnd(event: DragEndEvent) {
        const { active, over } = event;

        if (!over || active.id === over.id) return;

        const oldIndex = games.findIndex((g) => g.id === active.id);
        const newIndex = games.findIndex((g) => g.id === over.id);

        const reordered = arrayMove(games, oldIndex, newIndex);
        setGames(reordered);

        // Save new order
        try {
            const items = reordered.map((g, idx) => ({ id: g.id, order: idx }));
            await (api as any).games.reorder(items);
            toast.success("Order saved");
        } catch (error) {
            console.error("Reorder error:", error);
            toast.error("Failed to save order");
            fetchData(); // Revert
        }
    }

    return (
        <DashboardLayout>
            <div className="space-y-6">
                {/* Header */}
                <div className="flex justify-between items-center">
                    <div>
                        <h1 className="text-2xl font-bold flex items-center gap-2">
                            <Gamepad2 className="h-6 w-6" />
                            Games
                        </h1>
                        <p className="text-muted-foreground">
                            Manage games for the Android TV app
                        </p>
                    </div>
                    <Button onClick={openAddDialog}>
                        <Plus className="mr-2 h-4 w-4" /> Add Game
                    </Button>
                </div>

                {/* Filters */}
                <div className="flex flex-wrap gap-4 items-center">
                    <div className="relative flex-1 min-w-[200px] max-w-sm">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                        <Input
                            placeholder="Search games..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="pl-9"
                        />
                    </div>

                    <Select value={filterCategory} onValueChange={setFilterCategory}>
                        <SelectTrigger className="w-[180px]">
                            <SelectValue placeholder="All Categories" />
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="all">All Categories</SelectItem>
                            {categories.map((cat) => (
                                <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                            ))}
                        </SelectContent>
                    </Select>

                    <Tabs value={viewMode} onValueChange={(v) => setViewMode(v as "grid" | "list")}>
                        <TabsList>
                            <TabsTrigger value="grid">
                                <Grid className="h-4 w-4" />
                            </TabsTrigger>
                            <TabsTrigger value="list">
                                <List className="h-4 w-4" />
                            </TabsTrigger>
                        </TabsList>
                    </Tabs>
                </div>

                {/* Games List */}
                {loading ? (
                    <div className="text-center py-12 text-muted-foreground">Loading...</div>
                ) : filteredGames.length === 0 ? (
                    <div className="text-center py-12 text-muted-foreground">
                        No games found. Add your first game!
                    </div>
                ) : viewMode === "grid" ? (
                    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                        <SortableContext items={filteredGames.map(g => g.id)} strategy={rectSortingStrategy}>
                            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5 gap-4">
                                {filteredGames.map((game) => (
                                    <SortableGridCard
                                        key={game.id}
                                        game={game}
                                        openEditDialog={openEditDialog}
                                        handleDelete={handleDelete}
                                        deletingId={deletingId}
                                    />
                                ))}
                            </div>
                        </SortableContext>
                    </DndContext>
                ) : (
                    <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
                        <SortableContext items={filteredGames.map(g => g.id)} strategy={verticalListSortingStrategy}>
                            <div className="border rounded-lg">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableHead className="w-10"></TableHead>
                                            <TableHead className="w-16">Image</TableHead>
                                            <TableHead>Name</TableHead>
                                            <TableHead>Category</TableHead>
                                            <TableHead>Status</TableHead>
                                            <TableHead>Game URL</TableHead>
                                            <TableHead className="text-right">Actions</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {filteredGames.map((game) => (
                                            <SortableTableRow
                                                key={game.id}
                                                game={game}
                                                openEditDialog={openEditDialog}
                                                handleDelete={handleDelete}
                                                deletingId={deletingId}
                                            />
                                        ))}
                                    </TableBody>
                                </Table>
                            </div>
                        </SortableContext>
                    </DndContext>
                )}

                {/* Add/Edit Dialog */}
                <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
                    <DialogContent className="max-w-lg">
                        <DialogHeader>
                            <DialogTitle>{editingGame ? "Edit Game" : "Add Game"}</DialogTitle>
                            <DialogDescription>
                                {editingGame ? "Update game details" : "Add a new game to the catalog"}
                            </DialogDescription>
                        </DialogHeader>

                        <div className="space-y-4 py-4">
                            {!editingGame && (
                                <div className="space-y-2">
                                    <Label htmlFor="id">Game ID</Label>
                                    <Input
                                        id="id"
                                        placeholder="unique-game-id (auto-generated if empty)"
                                        value={formData.id}
                                        onChange={(e) => setFormData({ ...formData, id: e.target.value })}
                                    />
                                </div>
                            )}

                            <div className="space-y-2">
                                <Label htmlFor="name">Name *</Label>
                                <Input
                                    id="name"
                                    placeholder="Game name"
                                    value={formData.name}
                                    onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                                />
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="description">Description</Label>
                                <Textarea
                                    id="description"
                                    placeholder="Game description"
                                    value={formData.description}
                                    onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                                    rows={3}
                                />
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="game_url">Game URL *</Label>
                                <Input
                                    id="game_url"
                                    placeholder="https://poki.com/en/g/game-name"
                                    value={formData.game_url}
                                    onChange={(e) => setFormData({ ...formData, game_url: e.target.value })}
                                />
                            </div>

                            <div className="space-y-2">
                                <Label htmlFor="category">Category</Label>
                                <Select
                                    value={formData.category}
                                    onValueChange={(v) => setFormData({ ...formData, category: v })}
                                >
                                    <SelectTrigger>
                                        <SelectValue placeholder="Select category" />
                                    </SelectTrigger>
                                    <SelectContent>
                                        {categories.map((cat) => (
                                            <SelectItem key={cat} value={cat}>{cat}</SelectItem>
                                        ))}
                                    </SelectContent>
                                </Select>
                            </div>

                            <div className="space-y-2">
                                <Label>Image</Label>
                                <div className="flex gap-4 items-start">
                                    {formData.image_url && (
                                        <img
                                            src={resolveImageUrl(formData.image_url)}
                                            alt="Preview"
                                            className="w-20 h-20 rounded object-cover"
                                        />
                                    )}
                                    <div className="flex-1 space-y-2">
                                        <Input
                                            placeholder="Image URL"
                                            value={formData.image_url}
                                            onChange={(e) => setFormData({ ...formData, image_url: e.target.value })}
                                        />
                                        <input
                                            type="file"
                                            ref={fileInputRef}
                                            className="hidden"
                                            accept="image/*"
                                            onChange={handleImageUpload}
                                        />
                                        <Button
                                            type="button"
                                            variant="outline"
                                            size="sm"
                                            onClick={() => fileInputRef.current?.click()}
                                        >
                                            <Upload className="h-4 w-4 mr-2" /> Upload
                                        </Button>
                                    </div>
                                </div>
                            </div>

                            <div className="flex items-center justify-between">
                                <Label htmlFor="is_active">Active</Label>
                                <Switch
                                    id="is_active"
                                    checked={formData.is_active}
                                    onCheckedChange={(v) => setFormData({ ...formData, is_active: v })}
                                />
                            </div>
                        </div>

                        <div className="flex justify-end gap-2">
                            <Button variant="outline" onClick={() => setDialogOpen(false)}>
                                Cancel
                            </Button>
                            <Button onClick={handleSave} disabled={saving}>
                                {saving ? "Saving..." : editingGame ? "Update" : "Create"}
                            </Button>
                        </div>
                    </DialogContent>
                </Dialog>
            </div>
        </DashboardLayout>
    );
}
