import { useState, useEffect, useRef } from "react";
import { DashboardLayout } from "@/components/DashboardLayout";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Search, Plus, Trash2, Loader2, Monitor, Copy, Eye, EyeOff, Upload, FileUp, Edit } from "lucide-react";
import { toast } from "sonner";
import api, { SubscriberResponse, SubscriberCreateResponse, Package, UserStatus } from "@/lib/api";

export default function Users() {
  const [users, setUsers] = useState<SubscriberResponse[]>([]);
  const [packages, setPackages] = useState<Package[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [visiblePasswords, setVisiblePasswords] = useState<Record<string, boolean>>({});

  // Create/Edit Dialog
  const [dialogOpen, setDialogOpen] = useState(false);
  const [createMode, setCreateMode] = useState<"mac" | "generated">("generated");
  const [isSaving, setIsSaving] = useState(false);
  const [editingUser, setEditingUser] = useState<SubscriberResponse | null>(null);

  // Import Dialog
  const [importDialogOpen, setImportDialogOpen] = useState(false);
  const [importFile, setImportFile] = useState<File | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Form State
  const [formData, setFormData] = useState({
    macAddress: "",
    displayName: "",
    surname: "",
    building: "",
    address: "",
    clientNo: "",
    maxDevices: "1",
    packages: [] as string[],
    status: UserStatus.ACTIVE
  });

  // Result Dialog
  const [resultOpen, setResultOpen] = useState(false);
  const [createdUser, setCreatedUser] = useState<SubscriberCreateResponse | null>(null);

  const fetchData = async () => {
    try {
      const [usersRes, packagesRes] = await Promise.all([
        api.users.list({ limit: 100 }),
        api.packages.list()
      ]);
      setUsers(usersRes.items);
      setPackages(packagesRes.packages);
    } catch (error) {
      console.error("Failed to fetch data:", error);
      toast.error("Failed to load users");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const resetForm = () => {
    setFormData({
      macAddress: "",
      displayName: "",
      surname: "",
      building: "",
      address: "",
      clientNo: "",
      maxDevices: "1",
      packages: [],
      status: UserStatus.ACTIVE
    });
    setEditingUser(null);
  };

  const openCreateDialog = () => {
    resetForm();
    setCreateMode("generated");
    setDialogOpen(true);
  };

  const openEditDialog = (user: SubscriberResponse) => {
    setEditingUser(user);
    setFormData({
      macAddress: user.mac_address || "",
      displayName: user.display_name || "",
      surname: user.surname || "",
      building: user.building || "",
      address: user.address || "",
      clientNo: user.client_no || "",
      maxDevices: user.max_devices.toString(),
      packages: user.package_ids || [],
      status: (user.status as UserStatus) || UserStatus.ACTIVE
    });
    setCreateMode(user.username ? "generated" : "mac");
    setDialogOpen(true);
  };

  const handleSave = async () => {
    setIsSaving(true);
    try {
      const commonData = {
        display_name: formData.displayName,
        surname: formData.surname,
        building: formData.building,
        address: formData.address,
        client_no: formData.clientNo,
        package_ids: formData.packages,
        max_devices: parseInt(formData.maxDevices),
        status: formData.status
      };

      if (editingUser) {
        // Update
        await api.users.update(editingUser.id, commonData);
        toast.success("User updated successfully");
        setDialogOpen(false);
      } else {
        // Create
        let result;
        if (createMode === "mac") {
          if (!formData.macAddress) {
            toast.error("MAC Address is required");
            setIsSaving(false);
            return;
          }
          result = await api.users.createByMac({
            ...commonData,
            mac_address: formData.macAddress,
          });
          toast.success("User created successfully");
          setDialogOpen(false);
        } else {
          result = await api.users.createGenerated(commonData);
          setCreatedUser(result as SubscriberCreateResponse);
          setResultOpen(true);
          setDialogOpen(false);
        }
      }
      fetchData();
    } catch (error: any) {
      console.error("Failed to save user:", error);
      const msg = error.message?.includes("Client ID") ? "Client ID already exists" : "Failed to save user";
      toast.error(msg);
    } finally {
      setIsSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    if (!confirm("Are you sure you want to delete this user?")) return;
    try {
      await api.users.delete(id);
      toast.success("User deleted");
      setUsers(users.filter(u => u.id !== id));
    } catch (error) {
      console.error("Failed to delete:", error);
      toast.error("Failed to delete user");
    }
  };

  const handleImport = async () => {
    if (!importFile) return;
    setIsImporting(true);
    try {
      const res = await api.users.importMacUsers(importFile);
      toast.success(`Imported: ${res.imported}, Skipped: ${res.skipped}`);
      if (res.errors.length > 0) {
        console.error("Import errors:", res.errors);
        toast.warning(`${res.errors.length} errors occurred (check console)`);
      }
      setImportDialogOpen(false);
      setImportFile(null);
      fetchData();
    } catch (error) {
      console.error("Import failed:", error);
      toast.error("Failed to import users");
    } finally {
      setIsImporting(false);
    }
  };

  const togglePasswordVisibility = (id: string) => {
    setVisiblePasswords(prev => ({ ...prev, [id]: !prev[id] }));
  };

  const copyText = (text: string) => {
    navigator.clipboard.writeText(text);
    toast.success("Copied to clipboard");
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case UserStatus.ACTIVE: return "bg-green-500 hover:bg-green-600";
      case UserStatus.INACTIVE: return "bg-red-500 hover:bg-red-600";
      case UserStatus.EXPIRED: return "bg-orange-500 hover:bg-orange-600";
      case UserStatus.BONUS: return "bg-purple-500 hover:bg-purple-600";
      case UserStatus.TEST: return "bg-blue-500 hover:bg-blue-600";
      default: return "bg-gray-500";
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
            <h1 className="text-3xl font-bold tracking-tight">Users</h1>
            <p className="text-muted-foreground">
              Manage subscribers, passwords, devices, and details
            </p>
          </div>
          <div className="flex gap-2">
            <Button variant="outline" onClick={() => setImportDialogOpen(true)} className="gap-2">
              <Upload className="h-4 w-4" />
              Import CSV
            </Button>
            <Button onClick={openCreateDialog} className="gap-2">
              <Plus className="h-4 w-4" />
              Add User
            </Button>
          </div>
        </div>

        {/* Users List */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Subscribers</CardTitle>
              <div className="relative w-64">
                <Search className="absolute left-2 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Search users..."
                  className="pl-8"
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                />
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>User Details</TableHead>
                  <TableHead>Credentials</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Packages</TableHead>
                  <TableHead>Devices</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {users.filter(u =>
                  !search ||
                  u.username?.toLowerCase().includes(search.toLowerCase()) ||
                  u.mac_address?.toLowerCase().includes(search.toLowerCase()) ||
                  u.display_name?.toLowerCase().includes(search.toLowerCase()) ||
                  u.surname?.toLowerCase().includes(search.toLowerCase()) ||
                  u.client_no?.toLowerCase().includes(search.toLowerCase())
                ).map((user) => (
                  <TableRow key={user.id}>
                    <TableCell>
                      <div className="font-medium">{user.display_name} {user.surname}</div>
                      <div className="text-xs text-muted-foreground space-y-0.5">
                        {user.client_no && <div>Client #: {user.client_no}</div>}
                        {user.address && <div>{user.address} {user.building && `(${user.building})`}</div>}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="space-y-1">
                        {user.username ? (
                          <>
                            <div className="text-xs font-mono bg-muted px-1.5 py-0.5 rounded w-fit flex gap-2 items-center">
                              User: {user.username}
                              <Copy className="h-3 w-3 cursor-pointer opacity-50 hover:opacity-100" onClick={() => copyText(user.username!)} />
                            </div>
                            <div className="text-xs font-mono bg-muted px-1.5 py-0.5 rounded w-fit flex gap-2 items-center">
                              Pass: {visiblePasswords[user.id] ? user.password || "******" : "••••••"}
                              <button onClick={() => togglePasswordVisibility(user.id)}>
                                {visiblePasswords[user.id] ? <EyeOff className="h-3 w-3" /> : <Eye className="h-3 w-3" />}
                              </button>
                              {visiblePasswords[user.id] && user.password && (
                                <Copy className="h-3 w-3 cursor-pointer opacity-50 hover:opacity-100" onClick={() => copyText(user.password!)} />
                              )}
                            </div>
                          </>
                        ) : (
                          <div className="text-xs font-mono bg-muted px-1.5 py-0.5 rounded w-fit">
                            MAC: {user.mac_address}
                          </div>
                        )}
                      </div>
                    </TableCell>
                    <TableCell>
                      <Badge className={getStatusColor(user.status || "active")}>
                        {(user.status || "active").toUpperCase().replace("_", " ")}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-wrap gap-1">
                        {user.package_ids?.map(pid => {
                          const pkg = packages.find(p => p.id === pid);
                          return pkg ? (
                            <Badge key={pid} variant="outline" className="text-xs">
                              {pkg.name}
                            </Badge>
                          ) : null;
                        })}
                      </div>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <Monitor className="h-4 w-4 text-muted-foreground" />
                        <span className="text-sm">
                          {user.devices?.length || 0} / {user.max_devices}
                        </span>
                      </div>
                      {user.devices?.length > 0 && (
                        <div className="text-xs text-muted-foreground mt-1 max-w-[150px] truncate" title={user.devices.map(d => d.mac_address).join(", ")}>
                          {user.devices[0].mac_address} {user.devices.length > 1 && `+${user.devices.length - 1}`}
                        </div>
                      )}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-8 w-8 p-0"
                          onClick={() => openEditDialog(user)}
                        >
                          <Edit className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          className="text-destructive h-8 w-8 p-0"
                          onClick={() => handleDelete(user.id)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        {/* Create/Edit Dialog */}
        <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
          <DialogContent className="sm:max-w-[600px] max-h-[90vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle>{editingUser ? "Edit User" : "Add New User"}</DialogTitle>
              <DialogDescription>
                {editingUser ? "Update subscriber details and status." : "Create a new subscriber account with detailed information."}
              </DialogDescription>
            </DialogHeader>

            <Tabs value={createMode} onValueChange={(v) => { if (!editingUser) setCreateMode(v as any) }} className="w-full">
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="generated" disabled={!!editingUser}>Generate Credentials</TabsTrigger>
                <TabsTrigger value="mac" disabled={!!editingUser}>By MAC Address</TabsTrigger>
              </TabsList>

              <div className="space-y-4 py-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Name</Label>
                    <Input
                      placeholder="First Name"
                      value={formData.displayName}
                      onChange={(e) => setFormData({ ...formData, displayName: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Surname</Label>
                    <Input
                      placeholder="Last Name"
                      value={formData.surname}
                      onChange={(e) => setFormData({ ...formData, surname: e.target.value })}
                    />
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Client No</Label>
                    <Input
                      placeholder="Client #"
                      value={formData.clientNo}
                      onChange={(e) => setFormData({ ...formData, clientNo: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Building</Label>
                    <Input
                      placeholder="Building Name/No"
                      value={formData.building}
                      onChange={(e) => setFormData({ ...formData, building: e.target.value })}
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label>Address</Label>
                  <Input
                    placeholder="Full Address"
                    value={formData.address}
                    onChange={(e) => setFormData({ ...formData, address: e.target.value })}
                  />
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label>Max Devices</Label>
                    <Select value={formData.maxDevices} onValueChange={(v) => setFormData({ ...formData, maxDevices: v })}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="1">1 Device</SelectItem>
                        <SelectItem value="2">2 Devices</SelectItem>
                        <SelectItem value="3">3 Devices</SelectItem>
                        <SelectItem value="5">5 Devices</SelectItem>
                        <SelectItem value="10">10 Devices</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label>Status</Label>
                    <Select value={formData.status} onValueChange={(v) => setFormData({ ...formData, status: v as UserStatus })}>
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value={UserStatus.ACTIVE}>Active</SelectItem>
                        <SelectItem value={UserStatus.INACTIVE}>Inactive</SelectItem>
                        <SelectItem value={UserStatus.EXPIRED}>Payment Expired</SelectItem>
                        <SelectItem value={UserStatus.BONUS}>Bonus Days</SelectItem>
                        <SelectItem value={UserStatus.TEST}>Test Days</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                </div>

                <div className="space-y-2">
                  <Label>Assign Packages</Label>
                  <div className="border rounded-md p-3 space-y-2 max-h-40 overflow-y-auto">
                    {packages.length === 0 && <p className="text-sm text-muted-foreground">No packages available</p>}
                    {packages.map(pkg => (
                      <div key={pkg.id} className="flex items-center gap-2">
                        <input
                          type="checkbox"
                          id={`pkg-${pkg.id}`}
                          checked={formData.packages.includes(pkg.id)}
                          onChange={(e) => {
                            const current = formData.packages;
                            if (e.target.checked) setFormData({ ...formData, packages: [...current, pkg.id] });
                            else setFormData({ ...formData, packages: current.filter(id => id !== pkg.id) });
                          }}
                          className="rounded border-gray-300"
                        />
                        <Label htmlFor={`pkg-${pkg.id}`} className="font-normal cursor-pointer">
                          {pkg.name}
                        </Label>
                      </div>
                    ))}
                  </div>
                </div>

                <TabsContent value="mac" className="space-y-4 mt-0">
                  <div className="space-y-2">
                    <Label>MAC Address</Label>
                    <Input
                      disabled={!!editingUser}
                      placeholder="00:1A:2B:3C:4D:5E"
                      value={formData.macAddress}
                      onChange={(e) => setFormData({ ...formData, macAddress: e.target.value })}
                    />
                  </div>
                </TabsContent>

                <TabsContent value="generated" className="mt-0">
                  {editingUser ?
                    <p className="text-sm text-muted-foreground">Username and Password cannot be changed here.</p>
                    :
                    <div className="bg-muted p-3 rounded-md text-sm text-muted-foreground">
                      Login credentials will be generated automatically.
                    </div>
                  }
                </TabsContent>
              </div>
            </Tabs>

            <DialogFooter>
              <Button variant="outline" onClick={() => setDialogOpen(false)}>Cancel</Button>
              <Button onClick={handleSave} disabled={isSaving}>
                {isSaving && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                {editingUser ? "Update User" : "Create User"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Import Dialog */}
        <Dialog open={importDialogOpen} onOpenChange={setImportDialogOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Import Users (CSV)</DialogTitle>
              <DialogDescription>
                Upload a CSV file with columns: <code>mac_address</code>, <code>display_name</code>, <code>surname</code>, <code>building</code>, <code>address</code>, <code>client_no</code>.
              </DialogDescription>
            </DialogHeader>
            <div className="grid gap-4 py-4">
              <div className="flex items-center gap-4">
                <Button
                  variant="outline"
                  onClick={() => fileInputRef.current?.click()}
                  className="w-full"
                >
                  <FileUp className="mr-2 h-4 w-4" />
                  {importFile ? importFile.name : "Select CSV File"}
                </Button>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".csv"
                  className="hidden"
                  onChange={(e) => setImportFile(e.target.files?.[0] || null)}
                />
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setImportDialogOpen(false)}>Cancel</Button>
              <Button onClick={handleImport} disabled={!importFile || isImporting}>
                {isImporting && <Loader2 className="h-4 w-4 mr-2 animate-spin" />}
                Import Users
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Credentials Result Dialog */}
        <Dialog open={resultOpen} onOpenChange={setResultOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>User Created Successfully</DialogTitle>
              <DialogDescription>
                Note down the credentials. The password is also visible in the admin list.
              </DialogDescription>
            </DialogHeader>

            {createdUser && (
              <div className="bg-muted p-6 rounded-lg space-y-4 text-center">
                <div>
                  <div className="text-sm text-muted-foreground mb-1">Username</div>
                  <div className="text-2xl font-mono font-bold tracking-wider">{createdUser.username}</div>
                </div>
                <div className="border-t border-border/50 pt-4">
                  <div className="text-sm text-muted-foreground mb-1">Password</div>
                  <div className="text-2xl font-mono font-bold tracking-wider">{createdUser.password}</div>
                </div>
              </div>
            )}

            <DialogFooter className="sm:justify-center">
              <Button onClick={() => copyText(`${createdUser?.username}\n${createdUser?.password}`)} className="w-full sm:w-auto">
                <Copy className="h-4 w-4 mr-2" />
                Copy Credentials
              </Button>
              <Button variant="outline" onClick={() => setResultOpen(false)}>
                Done
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </DashboardLayout>
  );
}
