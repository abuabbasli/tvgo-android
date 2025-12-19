import { DashboardLayout } from "@/components/DashboardLayout";
import { Card, CardContent } from "@/components/ui/card";
import { Key, Construction } from "lucide-react";

export default function ApiManagement() {
  return (
    <DashboardLayout>
      <div className="p-6 space-y-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-3xl font-bold tracking-tight">API Management</h1>
            <p className="text-muted-foreground">
              Manage API keys for your TV applications
            </p>
          </div>
        </div>

        <Card className="border-dashed">
          <CardContent className="flex flex-col items-center justify-center py-16 text-center">
            <div className="h-16 w-16 rounded-full bg-muted flex items-center justify-center mb-4">
              <Construction className="h-8 w-8 text-muted-foreground" />
            </div>
            <h3 className="text-xl font-semibold mb-2">Coming Soon</h3>
            <p className="text-sm text-muted-foreground max-w-md mb-4">
              API key management will be available in a future update.
              Currently, the API uses JWT authentication.
            </p>
            <p className="text-xs text-muted-foreground">
              API Docs: <a href="http://localhost:8000/docs" target="_blank" rel="noopener noreferrer" className="text-primary underline">localhost:8000/docs</a>
            </p>
          </CardContent>
        </Card>
      </div>
    </DashboardLayout>
  );
}
