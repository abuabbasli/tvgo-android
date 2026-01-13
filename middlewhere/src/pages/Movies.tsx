import { useState, useEffect } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
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
    DialogHeader,
    DialogTitle,
    DialogFooter,
    DialogDescription,
} from "@/components/ui/dialog";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import { Loader2, Plus, Film, Trash2, Edit, Save, Upload, GripVertical } from "lucide-react";
import { toast } from "sonner";
import api, { Movie, resolveImageUrl, M3UChannelPreview } from "@/lib/api";

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
} from '@dnd-kit/sortable';
import { CSS } from '@dnd-kit/utilities';

interface SortableProps {
    movie: Movie;
    openEditDialog: (m: Movie) => void;
    handleDelete: (id: string) => void;
}

const SortableTableRow = ({ movie, openEditDialog, handleDelete }: SortableProps) => {
    const {
        attributes,
        listeners,
        setNodeRef,
        transform,
        transition,
        isDragging
    } = useSortable({ id: movie.id });

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
                <div className="h-16 w-12 bg-muted rounded overflow-hidden">
                    {movie.images?.poster ? (
                        <img
                            src={resolveImageUrl(movie.images.poster)}
                            alt={movie.title}
                            className="h-full w-full object-cover"
                            onError={(e) => ((e.target as HTMLImageElement).src = "")}
                        />
                    ) : (
                        <div className="h-full w-full flex items-center justify-center">
                            <Film className="h-4 w-4 text-muted-foreground" />
                        </div>
                    )}
                </div>
            </TableCell>
            <TableCell className="font-medium">{movie.title}</TableCell>
            <TableCell>{movie.year}</TableCell>
            <TableCell>
                <span className="text-sm text-muted-foreground font-mono">
                    {movie.order ?? "-"}
                </span>
            </TableCell>
            <TableCell className="text-right">
                <div className="flex justify-end gap-2">
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => openEditDialog(movie)}
                    >
                        <Edit className="h-4 w-4" />
                    </Button>
                    <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => handleDelete(movie.id)}
                        className="text-destructive hover:text-destructive"
                    >
                        <Trash2 className="h-4 w-4" />
                    </Button>
                </div>
            </TableCell>
        </TableRow>
    );
};

export default function Movies() {
    const [isLoading, setIsLoading] = useState(true);
    const [movies, setMovies] = useState<Movie[]>([]);
    const [isDialogOpen, setIsDialogOpen] = useState(false);

    // M3U Import State
    const [isImportDialogOpen, setIsImportDialogOpen] = useState(false);
    const [importUrl, setImportUrl] = useState("");
    const [isParsing, setIsParsing] = useState(false);
    const [parsedMovies, setParsedMovies] = useState<M3UChannelPreview[]>([]);
    const [selectedImportIds, setSelectedImportIds] = useState<Set<string>>(new Set());
    const [isImporting, setIsImporting] = useState(false);

    const [editingMovie, setEditingMovie] = useState<Movie | null>(null);
    const [formData, setFormData] = useState({
        id: "",
        title: "",
        synopsis: "",
        year: new Date().getFullYear(),
        rating: 0,
        streamUrl: "",
        posterUrl: "",
        landscapeUrl: "",
        order: "",
    });
    const [isSaving, setIsSaving] = useState(false);

    // DnD Sensors
    const sensors = useSensors(
        useSensor(PointerSensor),
        useSensor(KeyboardSensor, {
            coordinateGetter: sortableKeyboardCoordinates,
        })
    );

    const fetchMovies = async () => {
        try {
            const response = await api.movies.list({ limit: 1000 }); // Increase limit to ensure full list for ordering
            setMovies(response);
        } catch (error) {
            console.error("Failed to fetch movies:", error);
            toast.error("Failed to load movies");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchMovies();
    }, []);

    const handleOpenDialog = (movie?: Movie) => {
        if (movie) {
            setEditingMovie(movie);
            setFormData({
                id: movie.id,
                title: movie.title,
                synopsis: movie.synopsis || "",
                year: movie.year || new Date().getFullYear(),
                rating: movie.rating || 0,
                streamUrl: movie.media?.streamUrl || "",
                posterUrl: movie.images?.poster || "",
                landscapeUrl: movie.images?.landscape || "",
                order: movie.order?.toString() || ""
            });
        } else {
            setEditingMovie(null);
            setFormData({
                id: crypto.randomUUID(),
                title: "",
                synopsis: "",
                year: new Date().getFullYear(),
                rating: 0,
                streamUrl: "",
                posterUrl: "",
                landscapeUrl: "",
                order: ""
            });
        }
        setIsDialogOpen(true);
    };

    const handleSave = async () => {
        if (!formData.title || !formData.streamUrl) {
            toast.error("Title and Stream URL are required");
            return;
        }

        setIsSaving(true);
        try {
            const movieData = {
                id: formData.id,
                title: formData.title,
                synopsis: formData.synopsis,
                year: formData.year,
                rating: formData.rating,
                images: {
                    poster: formData.posterUrl,
                    landscape: formData.landscapeUrl,
                },
                media: {
                    streamUrl: formData.streamUrl,
                },
                order: formData.order ? parseInt(formData.order) : undefined,
            };

            if (editingMovie) {
                await api.movies.update(editingMovie.id, movieData);
                toast.success("Movie updated");
            } else {
                await api.movies.create(movieData);
                toast.success("Movie created");
            }
            fetchMovies();
            setIsDialogOpen(false);
        } catch (error) {
            console.error("Failed to save movie:", error);
            toast.error("Failed to save movie");
        } finally {
            setIsSaving(false);
        }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Are you sure you want to delete this movie?")) return;

        try {
            await api.movies.delete(id);
            toast.success("Movie deleted");
            // Optimistic update
            setMovies(movies.filter(m => m.id !== id));
        } catch (error) {
            console.error("Failed to delete movie:", error);
            toast.error("Failed to delete movie");
        }
    };

    // --- M3U Import Logic ---

    const handleParseM3U = async () => {
        if (!importUrl) return;
        setIsParsing(true);
        try {
            const result = await api.ingest.previewM3UUrl(importUrl);
            setParsedMovies(result.channels);
            // Select all by default
            setSelectedImportIds(new Set(result.channels.map(c => c.id)));
        } catch (error) {
            console.error(error);
            toast.error("Failed to parse M3U URL");
        } finally {
            setIsParsing(false);
        }
    };

    const handleParseFile = async (file: File) => {
        setIsParsing(true);
        try {
            const result = await api.ingest.previewM3UFile(file);
            setParsedMovies(result.channels);
            setSelectedImportIds(new Set(result.channels.map(c => c.id)));
        } catch (error) {
            console.error(error);
            toast.error("Failed to parse M3U file");
        } finally {
            setIsParsing(false);
        }
    };

    const handleImportSelected = async () => {
        setIsImporting(true);
        try {
            const toImport = parsedMovies.filter(m => selectedImportIds.has(m.id));

            // Build array of movies to import
            const moviesData = toImport.map((item, index) => ({
                id: item.id,
                title: item.name,
                year: new Date().getFullYear(),
                poster_url: item.logo_url || undefined,
                stream_url: item.stream_url,
                genres: item.group ? [item.group] : [],
                synopsis: `Imported from M3U (${item.group || 'Unknown'})`,
                order: index + 1,
            }));

            // Use batch API for fast import
            const result = await api.movies.batchCreate(moviesData);

            toast.success(`Imported ${result.created} movies (${result.skipped} skipped)`);
            setIsImportDialogOpen(false);
            setParsedMovies([]);
            setImportUrl("");
            fetchMovies();
        } catch (error) {
            console.error(error);
            toast.error("Import failed");
        } finally {
            setIsImporting(false);
        }
    };

    const toggleSelection = (id: string) => {
        const newSet = new Set(selectedImportIds);
        if (newSet.has(id)) {
            newSet.delete(id);
        } else {
            newSet.add(id);
        }
        setSelectedImportIds(newSet);
    };

    const toggleAll = () => {
        if (selectedImportIds.size === parsedMovies.length) {
            setSelectedImportIds(new Set());
        } else {
            setSelectedImportIds(new Set(parsedMovies.map(m => m.id)));
        }
    };

    // --- DnD Logic ---

    const handleDragEnd = (event: DragEndEvent) => {
        const { active, over } = event;

        if (active.id !== over?.id) {
            setMovies((items) => {
                const oldIndex = items.findIndex((i) => i.id === active.id);
                const newIndex = items.findIndex((i) => i.id === over?.id);

                const newOrder = arrayMove(items, oldIndex, newIndex);

                // Calculate new order values (1-based)
                const updates = newOrder.map((m, idx) => ({ id: m.id, order: idx + 1 }));

                // Optimistically update
                api.movies.reorder(updates).catch(err => {
                    console.error("Reorder failed", err);
                    toast.error("Failed to save order");
                });

                return newOrder.map((m, idx) => ({ ...m, order: idx + 1 }));
            });
        }
    };

    return (
        <DashboardLayout>
            <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-3xl font-bold tracking-tight">VOD Management</h1>
                        <p className="text-muted-foreground">Manage your movie library</p>
                    </div>
                    <div className="flex gap-2">
                        <Button variant="outline" onClick={() => setIsImportDialogOpen(true)}>
                            <Upload className="mr-2 h-4 w-4" />
                            Import M3U
                        </Button>
                        <Button
                            variant="outline"
                            onClick={async () => {
                                try {
                                    toast.info("Fetching metadata from TMDB... This may take a while.");
                                    const result = await api.movies.enrichAll();
                                    toast.success(`Enriched ${result.enriched} movies (${result.failed} failed)`);
                                    fetchMovies();
                                } catch (error) {
                                    toast.error("Failed to enrich movies. Make sure TMDB_API_KEY is set.");
                                }
                            }}
                        >
                            🎬 Fetch All Metadata
                        </Button>
                        <Button onClick={() => handleOpenDialog()}>
                            <Plus className="mr-2 h-4 w-4" />
                            Add Movie
                        </Button>
                    </div>
                </div>

                <div className="rounded-md border">
                    <Table>
                        <TableHeader>
                            <TableRow>
                                <TableHead className="w-[50px]"></TableHead>
                                <TableHead className="w-[80px]">Cover</TableHead>
                                <TableHead>Title</TableHead>
                                <TableHead>Year</TableHead>
                                <TableHead>Order</TableHead>
                                <TableHead className="text-right">Actions</TableHead>
                            </TableRow>
                        </TableHeader>
                        {isLoading ? (
                            <TableBody>
                                <TableRow>
                                    <TableCell colSpan={6} className="h-24 text-center">
                                        <Loader2 className="h-6 w-6 animate-spin mx-auto" />
                                    </TableCell>
                                </TableRow>
                            </TableBody>
                        ) : movies.length === 0 ? (
                            <TableBody>
                                <TableRow>
                                    <TableCell colSpan={6} className="h-24 text-center text-muted-foreground">
                                        No movies found. Add one to get started.
                                    </TableCell>
                                </TableRow>
                            </TableBody>
                        ) : (
                            <DndContext
                                sensors={sensors}
                                collisionDetection={closestCenter}
                                onDragEnd={handleDragEnd}
                            >
                                <SortableContext
                                    items={movies.map(m => m.id)}
                                    strategy={verticalListSortingStrategy}
                                >
                                    <TableBody>
                                        {movies.map((movie) => (
                                            <SortableTableRow
                                                key={movie.id}
                                                movie={movie}
                                                openEditDialog={handleOpenDialog}
                                                handleDelete={handleDelete}
                                            />
                                        ))}
                                    </TableBody>
                                </SortableContext>
                            </DndContext>
                        )}
                    </Table>
                </div>

                {/* Edit Dialog */}
                <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
                    <DialogContent className="sm:max-w-[600px]">
                        <DialogHeader>
                            <DialogTitle>{editingMovie ? "Edit Movie" : "Add Movie"}</DialogTitle>
                        </DialogHeader>
                        <div className="grid gap-4 py-4">
                            <div className="grid gap-2">
                                <Label htmlFor="title">Title</Label>
                                <Input
                                    id="title"
                                    value={formData.title}
                                    onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                                    placeholder="Movie Title"
                                />
                            </div>
                            <div className="grid grid-cols-2 gap-4">
                                <div className="grid gap-2">
                                    <Label htmlFor="year">Year</Label>
                                    <Input
                                        id="year"
                                        type="number"
                                        value={formData.year}
                                        onChange={(e) => setFormData({ ...formData, year: parseInt(e.target.value) })}
                                    />
                                </div>
                                <div className="grid gap-2">
                                    <Label htmlFor="rating">Rating (0-10)</Label>
                                    <Input
                                        id="rating"
                                        type="number"
                                        step="0.1"
                                        max="10"
                                        value={formData.rating}
                                        onChange={(e) => setFormData({ ...formData, rating: parseFloat(e.target.value) })}
                                    />
                                </div>
                            </div>
                            <div className="grid gap-2">
                                <Label htmlFor="synopsis">Synopsis</Label>
                                <Input
                                    id="synopsis"
                                    value={formData.synopsis}
                                    onChange={(e) => setFormData({ ...formData, synopsis: e.target.value })}
                                    placeholder="Movie description..."
                                />
                            </div>
                            <div className="grid gap-2">
                                <Label htmlFor="streamUrl">Stream URL</Label>
                                <Input
                                    id="streamUrl"
                                    value={formData.streamUrl}
                                    onChange={(e) => setFormData({ ...formData, streamUrl: e.target.value })}
                                    placeholder="http://example.com/movie.mp4"
                                />
                            </div>
                            <div className="grid gap-2">
                                <Label htmlFor="posterUrl">Poster URL</Label>
                                <Input
                                    id="posterUrl"
                                    value={formData.posterUrl}
                                    onChange={(e) => setFormData({ ...formData, posterUrl: e.target.value })}
                                    placeholder="http://example.com/poster.jpg"
                                />
                            </div>
                            <div className="grid gap-2">
                                <Label htmlFor="landscapeUrl">Landscape / Hero URL</Label>
                                <Input
                                    id="landscapeUrl"
                                    value={formData.landscapeUrl}
                                    onChange={(e) => setFormData({ ...formData, landscapeUrl: e.target.value })}
                                    placeholder="http://example.com/hero.jpg"
                                />
                            </div>
                            <div className="grid gap-2">
                                <Label htmlFor="order">Order Priority</Label>
                                <Input
                                    id="order"
                                    type="number"
                                    value={formData.order}
                                    onChange={(e) => setFormData({ ...formData, order: e.target.value })}
                                    placeholder="Optional sort order"
                                />
                            </div>
                        </div>
                        <DialogFooter>
                            <Button variant="outline" onClick={() => setIsDialogOpen(false)}>
                                Cancel
                            </Button>
                            <Button onClick={handleSave} disabled={isSaving}>
                                {isSaving && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                                Save Movie
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>

                {/* M3U Import Dialog */}
                <Dialog open={isImportDialogOpen} onOpenChange={setIsImportDialogOpen}>
                    <DialogContent className="sm:max-w-[800px] max-h-[80vh] flex flex-col">
                        <DialogHeader>
                            <DialogTitle>Import Movies from M3U</DialogTitle>
                            <DialogDescription>
                                Import movies from a URL or upload an M3U file.
                            </DialogDescription>
                        </DialogHeader>

                        <Tabs defaultValue="url" className="w-full">
                            <TabsList className="grid w-full grid-cols-2">
                                <TabsTrigger value="url">From URL</TabsTrigger>
                                <TabsTrigger value="file">Upload File</TabsTrigger>
                            </TabsList>
                            <TabsContent value="url" className="space-y-4 pt-4">
                                <div className="flex gap-2 items-end">
                                    <div className="grid gap-2 flex-1">
                                        <Label htmlFor="import-url">M3U URL</Label>
                                        <Input
                                            id="import-url"
                                            value={importUrl}
                                            onChange={(e) => setImportUrl(e.target.value)}
                                            placeholder="https://example.com/movies.m3u"
                                        />
                                    </div>
                                    <Button onClick={handleParseM3U} disabled={isParsing || !importUrl}>
                                        {isParsing && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                                        Parse URL
                                    </Button>
                                </div>
                            </TabsContent>
                            <TabsContent value="file" className="space-y-4 pt-4">
                                <div className="flex gap-2 items-end">
                                    <div className="grid gap-2 flex-1">
                                        <Label htmlFor="import-file">M3U File</Label>
                                        <Input
                                            id="import-file"
                                            type="file"
                                            accept=".m3u,.m3u8"
                                            onChange={(e) => {
                                                const file = e.target.files?.[0];
                                                if (file) handleParseFile(file);
                                            }}
                                        />
                                    </div>
                                </div>
                                {isParsing && (
                                    <div className="flex items-center justify-center p-4">
                                        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
                                        <span className="ml-2 text-muted-foreground">Parsing file...</span>
                                    </div>
                                )}
                            </TabsContent>
                        </Tabs>

                        {parsedMovies.length > 0 && (
                            <div className="flex-1 overflow-auto border rounded-md mt-4 min-h-[300px]">
                                <Table>
                                    <TableHeader>
                                        <TableRow>
                                            <TableCell className="w-[50px]">
                                                <Checkbox
                                                    checked={selectedImportIds.size === parsedMovies.length}
                                                    onCheckedChange={toggleAll}
                                                />
                                            </TableCell>
                                            <TableHead>Title</TableHead>
                                            <TableHead>Group</TableHead>
                                            <TableHead>URL</TableHead>
                                        </TableRow>
                                    </TableHeader>
                                    <TableBody>
                                        {parsedMovies.map((movie) => (
                                            <TableRow key={movie.id}>
                                                <TableCell>
                                                    <Checkbox
                                                        checked={selectedImportIds.has(movie.id)}
                                                        onCheckedChange={() => toggleSelection(movie.id)}
                                                    />
                                                </TableCell>
                                                <TableCell className="font-medium">{movie.name}</TableCell>
                                                <TableCell>{movie.group}</TableCell>
                                                <TableCell className="max-w-[200px] truncate text-xs text-muted-foreground">
                                                    {movie.stream_url}
                                                </TableCell>
                                            </TableRow>
                                        ))}
                                    </TableBody>
                                </Table>
                            </div>
                        )}

                        <DialogFooter className="mt-4">
                            <div className="mr-auto text-sm text-muted-foreground flex items-center">
                                {parsedMovies.length > 0 && `${selectedImportIds.size} selected`}
                            </div>
                            <Button variant="outline" onClick={() => setIsImportDialogOpen(false)}>
                                Cancel
                            </Button>
                            <Button onClick={handleImportSelected} disabled={isImporting || selectedImportIds.size === 0}>
                                {isImporting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                                Import Selected
                            </Button>
                        </DialogFooter>
                    </DialogContent>
                </Dialog>
            </div>
        </DashboardLayout>
    );
}
