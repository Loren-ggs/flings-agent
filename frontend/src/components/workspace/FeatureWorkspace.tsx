import { MemoryRouter, Navigate, Route, Routes } from "react-router-dom";
import { X } from "lucide-react";

import { cn } from "@/lib/utils";
import { useWorkspaceStore } from "@/stores/workspaceStore";
import { KnowledgeListPage } from "@/pages/admin/knowledge/KnowledgeListPage";
import { KnowledgeDocumentsPage } from "@/pages/admin/knowledge/KnowledgeDocumentsPage";
import { KnowledgeChunksPage } from "@/pages/admin/knowledge/KnowledgeChunksPage";
import { IntentTreePage } from "@/pages/admin/intent-tree/IntentTreePage";
import { IntentListPage } from "@/pages/admin/intent-tree/IntentListPage";
import { IntentEditPage } from "@/pages/admin/intent-tree/IntentEditPage";
import { IngestionPage } from "@/pages/admin/ingestion/IngestionPage";
import { QueryTermMappingPage } from "@/pages/admin/query-term-mapping/QueryTermMappingPage";
import { RagTracePage } from "@/pages/admin/traces/RagTracePage";
import { RagTraceDetailPage } from "@/pages/admin/traces/RagTraceDetailPage";

const FEATURE_ENTRY: Record<string, string> = {
  knowledge: "/admin/knowledge",
  intent: "/admin/intent-tree",
  ingestion: "/admin/ingestion",
  mapping: "/admin/mappings",
  traces: "/admin/traces"
};

function WorkspaceRoutes() {
  return (
    <Routes>
      <Route path="/admin/knowledge" element={<KnowledgeListPage />} />
      <Route path="/admin/knowledge/:kbId" element={<KnowledgeDocumentsPage />} />
      <Route path="/admin/knowledge/:kbId/docs/:docId" element={<KnowledgeChunksPage />} />
      <Route path="/admin/intent-tree" element={<IntentTreePage />} />
      <Route path="/admin/intent-list" element={<IntentListPage />} />
      <Route path="/admin/intent-list/:id/edit" element={<IntentEditPage />} />
      <Route path="/admin/ingestion" element={<IngestionPage />} />
      <Route path="/admin/mappings" element={<QueryTermMappingPage />} />
      <Route path="/admin/traces" element={<RagTracePage />} />
      <Route path="/admin/traces/:traceId" element={<RagTraceDetailPage />} />
      <Route path="*" element={<Navigate to="/admin/knowledge" replace />} />
    </Routes>
  );
}

export function FeatureWorkspace() {
  const feature = useWorkspaceStore((state) => state.activeFeature);
  const onClose = useWorkspaceStore((state) => state.closeFeature);
  const entry = feature ? FEATURE_ENTRY[feature] : null;
  const open = Boolean(feature);

  return (
    <>
      <div
        className={cn(
          "fixed inset-0 z-40 bg-stone-900/40 backdrop-blur-sm transition-opacity duration-300",
          open ? "opacity-100" : "pointer-events-none opacity-0"
        )}
        onClick={onClose}
      />
      <aside
        className={cn(
          "fixed inset-y-0 right-0 z-50 flex w-full max-w-[72vw] flex-col bg-[#FFFBF5] shadow-[0_0_60px_rgba(28,25,23,0.25)] transition-transform duration-300",
          open ? "translate-x-0" : "translate-x-full"
        )}
      >
        <div className="flex h-12 shrink-0 items-center justify-end border-b border-[#F5E9DC] px-4">
          <button
            type="button"
            onClick={onClose}
            aria-label="关闭"
            className="flex h-8 w-8 items-center justify-center rounded-lg text-[#78716C] transition-colors hover:bg-[#FFF1E6] hover:text-[#EA580C]"
          >
            <X className="h-5 w-5" />
          </button>
        </div>
        <div className="admin-layout min-h-0 flex-1 overflow-auto bg-[#FFFBF5]">
          <div className="mx-auto w-full max-w-[1200px] px-8 py-6">
            {open && entry ? (
              <MemoryRouter key={feature ?? "none"} initialEntries={[entry]}>
                <WorkspaceRoutes />
              </MemoryRouter>
            ) : null}
          </div>
        </div>
      </aside>
    </>
  );
}
