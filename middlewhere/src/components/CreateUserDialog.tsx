import { useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Plus } from "lucide-react";
import { supabase } from "@/integrations/supabase/client";
import { toast } from "sonner";

type UserGroup = "vip" | "premium" | "basic" | "trial" | "free";

interface CreateUserDialogProps {
  onUserCreated: () => void;
}

export function CreateUserDialog({ onUserCreated }: CreateUserDialogProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [formData, setFormData] = useState({
    email: "",
    password: "",
    username: "",
    user_group: "free" as UserGroup,
  });

  const handleCreateUser = async () => {
    if (!formData.email || !formData.password || !formData.username) {
      toast.error("Please fill in all required fields");
      return;
    }

    setIsCreating(true);
    try {
      // Create the auth user
      const { data: authData, error: authError } = await supabase.auth.signUp({
        email: formData.email,
        password: formData.password,
        options: {
          data: {
            username: formData.username,
          },
        },
      });

      if (authError) throw authError;

      if (authData.user) {
        // Update the profile with the selected group
        const { error: profileError } = await supabase
          .from("profiles")
          .update({
            username: formData.username,
            user_group: formData.user_group,
            status: "active",
          })
          .eq("id", authData.user.id);

        if (profileError) throw profileError;

        toast.success("User created successfully");
        setIsOpen(false);
        setFormData({
          email: "",
          password: "",
          username: "",
          user_group: "free",
        });
        onUserCreated();
      }
    } catch (error: any) {
      toast.error(error.message || "Failed to create user");
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <Dialog open={isOpen} onOpenChange={setIsOpen}>
      <DialogTrigger asChild>
        <Button>
          <Plus className="h-4 w-4 mr-2" />
          Create User
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Create New Streaming User</DialogTitle>
          <DialogDescription>
            Add a new user who can access the streaming application
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="username">Username *</Label>
            <Input
              id="username"
              value={formData.username}
              onChange={(e) => setFormData({ ...formData, username: e.target.value })}
              placeholder="Enter username"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="email">Email *</Label>
            <Input
              id="email"
              type="email"
              value={formData.email}
              onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              placeholder="user@example.com"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Password *</Label>
            <Input
              id="password"
              type="password"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              placeholder="Enter password"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="user_group">User Group</Label>
            <Select
              value={formData.user_group}
              onValueChange={(value) => setFormData({ ...formData, user_group: value as UserGroup })}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="vip">VIP</SelectItem>
                <SelectItem value="premium">Premium</SelectItem>
                <SelectItem value="basic">Basic</SelectItem>
                <SelectItem value="trial">Trial</SelectItem>
                <SelectItem value="free">Free</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => setIsOpen(false)}>
            Cancel
          </Button>
          <Button onClick={handleCreateUser} disabled={isCreating}>
            {isCreating ? "Creating..." : "Create User"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
