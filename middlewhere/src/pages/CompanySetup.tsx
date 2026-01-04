import { useState, useEffect, useRef } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch"; // Import Switch
import { Loader2, Save, Building2, Upload, ImageIcon, Tv, Film } from "lucide-react"; // Added icons
import { toast } from "sonner";
import api, { BrandConfig, Features, resolveImageUrl } from "@/lib/api"; // Added Features type

export default function CompanySetup() {
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isUploading, setIsUploading] = useState(false);
    const fileInputRef = useRef<HTMLInputElement>(null);
    const [config, setConfig] = useState<BrandConfig>({
        appName: "",
        logoUrl: "",
        accentColor: "#3B82F6",
        backgroundColor: "#0F172A",
    });
    // Add features state
    const [features, setFeatures] = useState<Features>({
        enableFavorites: true,
        enableSearch: true,
        autoplayPreview: true,
        enableLiveTv: true,
        enableVod: true,
    });

    const fetchConfig = async () => {
        try {
            const response = await api.config.getAdmin() as { brand: BrandConfig; features: Features };
            if (response.brand) {
                setConfig({
                    appName: response.brand.appName || "",
                    logoUrl: response.brand.logoUrl || "",
                    accentColor: response.brand.accentColor || "#3B82F6",
                    backgroundColor: response.brand.backgroundColor || "#0F172A",
                });
            }
            if (response.features) {
                setFeatures(response.features);
            }
        } catch (error) {
            console.error("Failed to fetch config:", error);
            toast.error("Failed to load configuration");
        } finally {
            setIsLoading(false);
        }
    };

    useEffect(() => {
        fetchConfig();
    }, []);

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
            setConfig({ ...config, logoUrl: result.url });
            toast.success("Logo uploaded successfully");
        } catch (error) {
            console.error("Upload failed:", error);
            toast.error("Failed to upload logo");
        } finally {
            setIsUploading(false);
            if (fileInputRef.current) {
                fileInputRef.current.value = "";
            }
        }
    };

    const handleSave = async () => {
        setIsSaving(true);
        try {
            // Save both brand config and features
            await Promise.all([
                api.config.updateBrand(config),
                api.config.updateFeatures(features)
            ]);
            toast.success("Configuration saved");
        } catch (error) {
            console.error("Failed to save:", error);
            toast.error("Failed to save configuration");
        } finally {
            setIsSaving(false);
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
                        <h1 className="text-3xl font-bold tracking-tight">Company Setup</h1>
                        <p className="text-muted-foreground">
                            Configure your application branding and services
                        </p>
                    </div>
                    <Button onClick={handleSave} disabled={isSaving || isUploading}>
                        {isSaving ? (
                            <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                        ) : (
                            <Save className="h-4 w-4 mr-2" />
                        )}
                        Save Changes
                    </Button>
                </div>

                <div className="grid gap-6 md:grid-cols-2">
                    {/* Service Configuration */}
                    <Card className="md:col-span-2">
                        <CardHeader>
                            <CardTitle>Service Configuration</CardTitle>
                            <CardDescription>
                                Enable or disable services for your customers
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="grid gap-6 md:grid-cols-2">
                            <div className="flex items-center justify-between space-x-2 rounded-lg border p-4">
                                <div className="flex items-center space-x-4">
                                    <div className="bg-primary/10 p-2 rounded-full">
                                        <Tv className="h-6 w-6 text-primary" />
                                    </div>
                                    <div className="space-y-0.5">
                                        <Label className="text-base">Live TV</Label>
                                        <p className="text-sm text-muted-foreground">
                                            Enable Live TV channels and EPG
                                        </p>
                                    </div>
                                </div>
                                <Switch
                                    checked={features.enableLiveTv}
                                    onCheckedChange={(checked) => setFeatures({ ...features, enableLiveTv: checked })}
                                />
                            </div>
                            <div className="flex items-center justify-between space-x-2 rounded-lg border p-4">
                                <div className="flex items-center space-x-4">
                                    <div className="bg-primary/10 p-2 rounded-full">
                                        <Film className="h-6 w-6 text-primary" />
                                    </div>
                                    <div className="space-y-0.5">
                                        <Label className="text-base">VOD / Movies</Label>
                                        <p className="text-sm text-muted-foreground">
                                            Enable Movies and Video on Demand
                                        </p>
                                    </div>
                                </div>
                                <Switch
                                    checked={features.enableVod}
                                    onCheckedChange={(checked) => setFeatures({ ...features, enableVod: checked })}
                                />
                            </div>
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader>
                            <CardTitle className="flex items-center gap-2">
                                <Building2 className="h-5 w-5" />
                                Branding
                            </CardTitle>
                            <CardDescription>
                                Customize your application appearance
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="space-y-2">
                                <Label htmlFor="app-name">Application Name</Label>
                                <Input
                                    id="app-name"
                                    value={config.appName}
                                    onChange={(e) => setConfig({ ...config, appName: e.target.value })}
                                    placeholder="e.g., My IPTV Service"
                                />
                            </div>

                            {/* Logo Upload Section */}
                            <div className="space-y-2">
                                <Label>Company Logo</Label>
                                <div className="flex gap-4 items-start">
                                    {/* Preview */}
                                    <div className="h-20 w-20 rounded-lg bg-muted flex items-center justify-center overflow-hidden border-2 border-dashed border-muted-foreground/25 flex-shrink-0">
                                        {config.logoUrl ? (
                                            <img
                                                src={resolveImageUrl(config.logoUrl)}
                                                alt="Logo preview"
                                                className="h-full w-full object-contain"
                                                onError={(e) => {
                                                    (e.target as HTMLImageElement).style.display = 'none';
                                                }}
                                            />
                                        ) : (
                                            <ImageIcon className="h-8 w-8 text-muted-foreground/50" />
                                        )}
                                    </div>

                                    {/* Upload Controls */}
                                    <div className="flex-1 space-y-2">
                                        <input
                                            ref={fileInputRef}
                                            type="file"
                                            accept="image/*"
                                            onChange={handleImageUpload}
                                            className="hidden"
                                            id="logo-upload"
                                        />
                                        <Button
                                            type="button"
                                            variant="outline"
                                            size="sm"
                                            className="w-full"
                                            onClick={() => fileInputRef.current?.click()}
                                            disabled={isUploading}
                                        >
                                            {isUploading ? (
                                                <Loader2 className="h-4 w-4 mr-2 animate-spin" />
                                            ) : (
                                                <Upload className="h-4 w-4 mr-2" />
                                            )}
                                            {isUploading ? "Uploading..." : "Upload Logo"}
                                        </Button>
                                        <div className="text-xs text-muted-foreground">
                                            or enter URL:
                                        </div>
                                        <Input
                                            value={config.logoUrl}
                                            onChange={(e) => setConfig({ ...config, logoUrl: e.target.value })}
                                            placeholder="https://example.com/logo.png"
                                            className="text-xs"
                                        />
                                    </div>
                                </div>
                            </div>
                        </CardContent>
                    </Card>

                    <Card>
                        <CardHeader>
                            <CardTitle>Colors</CardTitle>
                            <CardDescription>
                                Define your brand colors
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="space-y-4">
                            <div className="space-y-2">
                                <Label htmlFor="accent-color">Accent Color</Label>
                                <div className="flex gap-2">
                                    <Input
                                        id="accent-color"
                                        type="color"
                                        value={config.accentColor}
                                        onChange={(e) => setConfig({ ...config, accentColor: e.target.value })}
                                        className="w-16 h-10 p-1"
                                    />
                                    <Input
                                        value={config.accentColor}
                                        onChange={(e) => setConfig({ ...config, accentColor: e.target.value })}
                                        placeholder="#3B82F6"
                                        className="flex-1"
                                    />
                                </div>
                            </div>
                            <div className="space-y-2">
                                <Label htmlFor="bg-color">Background Color</Label>
                                <div className="flex gap-2">
                                    <Input
                                        id="bg-color"
                                        type="color"
                                        value={config.backgroundColor}
                                        onChange={(e) => setConfig({ ...config, backgroundColor: e.target.value })}
                                        className="w-16 h-10 p-1"
                                    />
                                    <Input
                                        value={config.backgroundColor}
                                        onChange={(e) => setConfig({ ...config, backgroundColor: e.target.value })}
                                        placeholder="#0F172A"
                                        className="flex-1"
                                    />
                                </div>
                            </div>
                        </CardContent>
                    </Card>
                </div>

                {/* Preview */}
                <Card>
                    <CardHeader>
                        <CardTitle>Preview</CardTitle>
                        <CardDescription>
                            See how your branding will appear
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <div
                            className="rounded-lg p-6 flex items-center gap-4"
                            style={{ backgroundColor: config.backgroundColor }}
                        >
                            {config.logoUrl && (
                                <img
                                    src={resolveImageUrl(config.logoUrl)}
                                    alt="Logo"
                                    className="h-12 object-contain"
                                    onError={(e) => {
                                        e.currentTarget.style.display = 'none';
                                    }}
                                />
                            )}
                            <span
                                className="text-2xl font-bold"
                                style={{ color: config.accentColor }}
                            >
                                {config.appName || "Your App Name"}
                            </span>
                        </div>
                    </CardContent>
                </Card>
            </div>
        </DashboardLayout>
    );
}
