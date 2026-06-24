import * as React from "react";

import { Header } from "@/components/layout/Header";
import { Sidebar } from "@/components/layout/Sidebar";
import { FeatureRail } from "@/components/workspace/FeatureRail";
import { useAuthStore } from "@/stores/authStore";
import { useWorkspaceStore } from "@/stores/workspaceStore";

interface MainLayoutProps {
  children: React.ReactNode;
}

export function MainLayout({ children }: MainLayoutProps) {
  const [sidebarOpen, setSidebarOpen] = React.useState(false);
  const user = useAuthStore((state) => state.user);
  const isAdmin = user?.role === "admin";
  const activeFeature = useWorkspaceStore((state) => state.activeFeature);
  const openFeature = useWorkspaceStore((state) => state.openFeature);

  return (
    <div className="flex min-h-screen bg-[#FFFBF5]">
      <Sidebar isOpen={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="flex min-h-screen min-w-0 flex-1 flex-col bg-white">
        <Header onToggleSidebar={() => setSidebarOpen((prev) => !prev)} />
        <main className="flex-1 min-h-0 overflow-hidden bg-white">{children}</main>
      </div>
      {isAdmin ? <FeatureRail active={activeFeature} onSelect={openFeature} /> : null}
    </div>
  );
}
