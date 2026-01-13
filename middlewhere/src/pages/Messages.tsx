import { useState, useEffect } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Switch } from "@/components/ui/switch";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger, DialogFooter, DialogDescription } from "@/components/ui/dialog";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Checkbox } from "@/components/ui/checkbox";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useToast } from "@/hooks/use-toast";
import { api, Message, MessageCreate, MessageTargetType, UserGroup, SubscriberResponse } from "@/lib/api";
import { Send, Trash2, Link, Users, Globe, QrCode, Loader2, MessageSquare, Search } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";

export default function Messages() {
    const [messages, setMessages] = useState<Message[]>([]);
    const [groups, setGroups] = useState<UserGroup[]>([]);
    const [users, setUsers] = useState<SubscriberResponse[]>([]);
    const [loading, setLoading] = useState(true);
    const [sending, setSending] = useState(false);
    const [dialogOpen, setDialogOpen] = useState(false);
    const { toast } = useToast();

    // Form state
    const [title, setTitle] = useState("");
    const [body, setBody] = useState("");
    const [url, setUrl] = useState("");
    const [showUrl, setShowUrl] = useState(false);
    const [targetType, setTargetType] = useState<MessageTargetType>("all");
    const [selectedGroups, setSelectedGroups] = useState<string[]>([]);
    const [selectedUsers, setSelectedUsers] = useState<string[]>([]);
    const [userSearch, setUserSearch] = useState("");

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const [messagesRes, groupsRes, usersRes] = await Promise.all([
                (api as any).messages.list(),
                (api as any).userGroups.list(),
                api.users.list({ limit: 500 }),
            ]);
            setMessages(messagesRes.items);
            setGroups(groupsRes.items);
            setUsers(usersRes.items);
        } catch (error: any) {
            toast({ title: "Error", description: error.message, variant: "destructive" });
        } finally {
            setLoading(false);
        }
    };

    const handleSend = async () => {
        if (!title.trim() || !body.trim()) {
            toast({ title: "Validation Error", description: "Title and body are required", variant: "destructive" });
            return;
        }

        setSending(true);
        try {
            const data: MessageCreate = {
                title: title.trim(),
                body: body.trim(),
                url: showUrl && url.trim() ? url.trim() : undefined,
                target_type: targetType,
                target_ids: targetType === "groups" ? selectedGroups : targetType === "users" ? selectedUsers : [],
            };

            await (api as any).messages.send(data);
            toast({ title: "Success", description: "Message sent successfully!" });

            // Reset form
            setTitle("");
            setBody("");
            setUrl("");
            setShowUrl(false);
            setTargetType("all");
            setSelectedGroups([]);
            setSelectedUsers([]);
            setDialogOpen(false);

            loadData();
        } catch (error: any) {
            toast({ title: "Error", description: error.message, variant: "destructive" });
        } finally {
            setSending(false);
        }
    };

    const handleDelete = async (id: string) => {
        if (!confirm("Are you sure you want to delete this message?")) return;

        try {
            await (api as any).messages.delete(id, true);
            toast({ title: "Success", description: "Message deleted" });
            loadData();
        } catch (error: any) {
            toast({ title: "Error", description: error.message, variant: "destructive" });
        }
    };

    const filteredUsers = users.filter(u =>
    (u.display_name?.toLowerCase().includes(userSearch.toLowerCase()) ||
        u.username?.toLowerCase().includes(userSearch.toLowerCase()) ||
        u.mac_address?.toLowerCase().includes(userSearch.toLowerCase()))
    );

    const getTargetLabel = (msg: Message) => {
        if (msg.target_type === "all") return <Badge variant="secondary"><Globe className="h-3 w-3 mr-1" />All Users</Badge>;
        if (msg.target_type === "groups") return <Badge variant="outline"><Users className="h-3 w-3 mr-1" />{msg.target_ids.length} Groups</Badge>;
        return <Badge variant="outline"><Users className="h-3 w-3 mr-1" />{msg.target_ids.length} Users</Badge>;
    };

    return (
        <DashboardLayout>
            <div className="p-6 space-y-6">
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-3xl font-bold tracking-tight">Messages</h1>
                        <p className="text-muted-foreground">
                            Send batch messages to users with optional QR codes
                        </p>
                    </div>
                    <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
                        <DialogTrigger asChild>
                            <Button>
                                <Send className="h-4 w-4 mr-2" />
                                New Message
                            </Button>
                        </DialogTrigger>
                        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
                            <DialogHeader>
                                <DialogTitle>Send Message</DialogTitle>
                                <DialogDescription>
                                    Compose and send a message to users or groups
                                </DialogDescription>
                            </DialogHeader>

                            <div className="space-y-4 py-4">
                                {/* Target Selection */}
                                <div className="space-y-2">
                                    <Label>Send To</Label>
                                    <RadioGroup value={targetType} onValueChange={(v) => setTargetType(v as MessageTargetType)}>
                                        <div className="flex items-center space-x-2">
                                            <RadioGroupItem value="all" id="all" />
                                            <Label htmlFor="all" className="font-normal">All Users</Label>
                                        </div>
                                        <div className="flex items-center space-x-2">
                                            <RadioGroupItem value="groups" id="groups" />
                                            <Label htmlFor="groups" className="font-normal">Specific Groups</Label>
                                        </div>
                                        <div className="flex items-center space-x-2">
                                            <RadioGroupItem value="users" id="users" />
                                            <Label htmlFor="users" className="font-normal">Specific Users</Label>
                                        </div>
                                    </RadioGroup>
                                </div>

                                {/* Group Selection */}
                                {targetType === "groups" && (
                                    <div className="space-y-2">
                                        <Label>Select Groups</Label>
                                        <ScrollArea className="h-32 rounded border p-2">
                                            {groups.length === 0 ? (
                                                <p className="text-sm text-muted-foreground">No groups available. Create groups first.</p>
                                            ) : (
                                                groups.map(group => (
                                                    <div key={group.id} className="flex items-center space-x-2 py-1">
                                                        <Checkbox
                                                            id={group.id}
                                                            checked={selectedGroups.includes(group.id)}
                                                            onCheckedChange={(checked) => {
                                                                if (checked) {
                                                                    setSelectedGroups([...selectedGroups, group.id]);
                                                                } else {
                                                                    setSelectedGroups(selectedGroups.filter(id => id !== group.id));
                                                                }
                                                            }}
                                                        />
                                                        <Label htmlFor={group.id} className="font-normal">
                                                            {group.name} ({group.user_count} users)
                                                        </Label>
                                                    </div>
                                                ))
                                            )}
                                        </ScrollArea>
                                    </div>
                                )}

                                {/* User Selection */}
                                {targetType === "users" && (
                                    <div className="space-y-2">
                                        <Label>Select Users</Label>
                                        <div className="relative">
                                            <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                                            <Input
                                                placeholder="Search users..."
                                                className="pl-8"
                                                value={userSearch}
                                                onChange={(e) => setUserSearch(e.target.value)}
                                            />
                                        </div>
                                        <ScrollArea className="h-40 rounded border p-2">
                                            {filteredUsers.slice(0, 50).map(user => (
                                                <div key={user.id} className="flex items-center space-x-2 py-1">
                                                    <Checkbox
                                                        id={user.id}
                                                        checked={selectedUsers.includes(user.id)}
                                                        onCheckedChange={(checked) => {
                                                            if (checked) {
                                                                setSelectedUsers([...selectedUsers, user.id]);
                                                            } else {
                                                                setSelectedUsers(selectedUsers.filter(id => id !== user.id));
                                                            }
                                                        }}
                                                    />
                                                    <Label htmlFor={user.id} className="font-normal text-sm">
                                                        {user.display_name || user.username || user.mac_address}
                                                    </Label>
                                                </div>
                                            ))}
                                        </ScrollArea>
                                        {selectedUsers.length > 0 && (
                                            <p className="text-sm text-muted-foreground">{selectedUsers.length} users selected</p>
                                        )}
                                    </div>
                                )}

                                {/* Message Content */}
                                <div className="space-y-2">
                                    <Label htmlFor="title">Title</Label>
                                    <Input
                                        id="title"
                                        placeholder="Message title"
                                        value={title}
                                        onChange={(e) => setTitle(e.target.value)}
                                    />
                                </div>

                                <div className="space-y-2">
                                    <Label htmlFor="body">Message Body</Label>
                                    <Textarea
                                        id="body"
                                        placeholder="Your message here..."
                                        rows={4}
                                        value={body}
                                        onChange={(e) => setBody(e.target.value)}
                                    />
                                </div>

                                {/* URL with QR */}
                                <div className="space-y-2">
                                    <div className="flex items-center justify-between">
                                        <Label htmlFor="url-toggle">Include URL (shows as QR code)</Label>
                                        <Switch
                                            id="url-toggle"
                                            checked={showUrl}
                                            onCheckedChange={setShowUrl}
                                        />
                                    </div>

                                    {showUrl && (
                                        <div className="space-y-4 pt-2">
                                            <Input
                                                placeholder="https://example.com"
                                                value={url}
                                                onChange={(e) => setUrl(e.target.value)}
                                            />
                                            {url && (
                                                <div className="flex flex-col items-center gap-2 p-4 bg-white rounded-lg">
                                                    <QRCodeSVG value={url} size={150} />
                                                    <p className="text-xs text-gray-500">QR Preview</p>
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </div>
                            </div>

                            <DialogFooter>
                                <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancel</Button>
                                <Button onClick={handleSend} disabled={sending}>
                                    {sending ? <Loader2 className="h-4 w-4 mr-2 animate-spin" /> : <Send className="h-4 w-4 mr-2" />}
                                    Send Message
                                </Button>
                            </DialogFooter>
                        </DialogContent>
                    </Dialog>
                </div>

                {/* Messages History */}
                <Card>
                    <CardHeader>
                        <CardTitle>Message History</CardTitle>
                        <CardDescription>Previously sent messages</CardDescription>
                    </CardHeader>
                    <CardContent>
                        {loading ? (
                            <div className="flex justify-center p-8">
                                <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
                            </div>
                        ) : messages.length === 0 ? (
                            <div className="text-center py-8 text-muted-foreground">
                                <MessageSquare className="h-12 w-12 mx-auto mb-2 opacity-50" />
                                <p>No messages sent yet</p>
                            </div>
                        ) : (
                            <Table>
                                <TableHeader>
                                    <TableRow>
                                        <TableHead>Title</TableHead>
                                        <TableHead>Target</TableHead>
                                        <TableHead>URL</TableHead>
                                        <TableHead>Date</TableHead>
                                        <TableHead>Status</TableHead>
                                        <TableHead className="w-12"></TableHead>
                                    </TableRow>
                                </TableHeader>
                                <TableBody>
                                    {messages.map((msg) => (
                                        <TableRow key={msg.id}>
                                            <TableCell className="font-medium">{msg.title}</TableCell>
                                            <TableCell>{getTargetLabel(msg)}</TableCell>
                                            <TableCell>
                                                {msg.url ? (
                                                    <Badge variant="secondary">
                                                        <QrCode className="h-3 w-3 mr-1" />
                                                        Has QR
                                                    </Badge>
                                                ) : (
                                                    <span className="text-muted-foreground">—</span>
                                                )}
                                            </TableCell>
                                            <TableCell className="text-muted-foreground">
                                                {msg.created_at ? new Date(msg.created_at).toLocaleDateString() : "—"}
                                            </TableCell>
                                            <TableCell>
                                                <Badge variant={msg.is_active ? "default" : "secondary"}>
                                                    {msg.is_active ? "Active" : "Inactive"}
                                                </Badge>
                                            </TableCell>
                                            <TableCell>
                                                <Button variant="ghost" size="icon" onClick={() => handleDelete(msg.id)}>
                                                    <Trash2 className="h-4 w-4 text-destructive" />
                                                </Button>
                                            </TableCell>
                                        </TableRow>
                                    ))}
                                </TableBody>
                            </Table>
                        )}
                    </CardContent>
                </Card>
            </div>
        </DashboardLayout>
    );
}
