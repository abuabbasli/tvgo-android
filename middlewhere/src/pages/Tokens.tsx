import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent } from "@/components/ui/card";
import { Shield, Construction } from "lucide-react";

export default function Tokens() {
  return (
    <DashboardLayout>
      <div className="p-6 space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">Token Management</h1>
            <p className="text-muted-foreground">
              Monitor and manage authentication tokens
            </p>
          </div>
        </div>

        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-16 text-center">
            <div className="h-16 w-16 rounded-full bg-muted flex items-center justify-center mb-4">
              <Construction className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="text-xl font-semibold mb-2">Coming Soon</h3>
            <p className="text-sm text-muted-foreground max-w-md">
              Token management dashboard will be available in a future update.
              JWT tokens are automatically managed by the authentication system.
            </p>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
}
