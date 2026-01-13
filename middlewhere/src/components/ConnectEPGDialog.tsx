import { useState, useRef } from "react";
import { Button } from "@/components/ui/button";
import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Upload, Link, FileText, Loader2, CheckCircle2 } from "lucide-react";
import { toast } from "sonner";
import api from "@/lib/api";

export function ConnectEPGDialog() {
    const [open, setOpen] = useState(false);
    const [activeTab, setActiveTab] = useState("url");
    const [isLoading, setIsLoading] = useState(false);

    // URL State
    const [url, setUrl] = useState("");

    // File State
    const [file, setFile] = useState<File | null>(null);
    const fileInputRef = useRef<HTMLInputElement>(null);

    const handleUrlSync = async () => {
        if (!url) {
            toast.error("Please enter a URL");
            return;
        }

        setIsLoading(true);
        try {
            const result = await api.epg.sync({ url, force: true });
            toast.success(`Synced ${result.channels_parsed} channels from URL`);
            setOpen(false);
        } catch (error) {
            console.error(error);
            toast.error("Failed to sync from URL");
        } finally {
            setIsLoading(false);
        }
    };

    const handleFileUpload = async () => {
        if (!file) {
            toast.error("Please select a file");
            return;
        }

        setIsLoading(true);
        try {
            // Assuming api.ts has epg.upload method (we need to add it!)
            // For now, let's just use the sync method if we had file support, 
            // but we need to update api.ts first.

            // Since we just added the backend endpoint, let's implement the call here manually 
            // or update api.ts. Updating api.ts is cleaner.

            const formData = new FormData();
            formData.append('file', file);

            // Direct fetch for now if api.ts isn't updated yet in this specific context
            // But we should update api.ts.
            // I'll assume api.ts update comes next.
            const response = await api.epg.upload(file);

            toast.success(`Uploaded EPG file. ${response.channels} channels found.`);
            setOpen(false);
        } catch (error) {
            console.error(error);
            toast.error("Failed to upload file");
        } finally {
            setIsLoading(false);
        }
    };

    return (
        <Dialog open={open} onOpenChange={setOpen}>
            <DialogTrigger asChild>
                <Button variant="outline" className="gap-2">
                    <Link className="h-4 w-4" />
                    Connect EPG
                </Button>
            </DialogTrigger>
            <DialogContent className="sm:max-w-[500px]">
                <DialogHeader>
                    <DialogTitle>Connect EPG</DialogTitle>
                    <DialogDescription>
                        Import Electronic Program Guide data from a URL or XML file.
                    </DialogDescription>
                </DialogHeader>

                <Tabs defaultValue="url" onValueChange={setActiveTab}>
                    <TabsList className="grid w-full grid-cols-2">
                        <TabsTrigger value="url">URL Link</TabsTrigger>
                        <TabsTrigger value="file">Upload File</TabsTrigger>
                    </TabsList>

                    <TabsContent value="url" className="space-y-4 py-4">
                        <div className="space-y-2">
                            <Label htmlFor="epg-url">EPG XML URL</Label>
                            <Input
                                id="epg-url"
                                placeholder="https://example.com/epg.xml"
                                value={url}
                                onChange={(e) => setUrl(e.target.value)}
                            />
                            <p className="text-xs text-muted-foreground">
                                The system will download and parse channels/programs from this URL.
                            </p>
                        </div>
                        <DialogFooter>
                            <Button onClick={handleUrlSync} disabled={isLoading}>
                                {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                                Sync URL
                            </Button>
                        </DialogFooter>
                    </TabsContent>

                    <TabsContent value="file" className="space-y-4 py-4">
                        <div
                            className="border-2 border-dashed rounded-lg p-8 text-center hover:bg-muted/50 cursor-pointer transition-colors"
                            onClick={() => fileInputRef.current?.click()}
                        >
                            <input
                                type="file"
                                ref={fileInputRef}
                                className="hidden"
                                accept=".xml"
                                onChange={(e) => setFile(e.target.files?.[0] || null)}
                            />
                            {file ? (
                                <div className="flex flex-col items-center gap-2 text-primary">
                                    <FileText className="h-8 w-8" />
                                    <span className="font-medium">{file.name}</span>
                                    <span className="text-xs text-muted-foreground">{(file.size / 1024 / 1024).toFixed(2)} MB</span>
                                </div>
                            ) : (
                                <div className="flex flex-col items-center gap-2 text-muted-foreground">
                                    <Upload className="h-8 w-8" />
                                    <span className="font-medium">Click to upload XML file</span>
                                    <span className="text-xs">Supports XMLTV format</span>
                                </div>
                            )}
                        </div>
                        <DialogFooter>
                            <Button onClick={handleFileUpload} disabled={isLoading || !file}>
                                {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                                Upload File
                            </Button>
                        </DialogFooter>
                    </TabsContent>
                </Tabs>
            </DialogContent>
        </Dialog>
    );
}
