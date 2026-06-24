import { Menu, Plus } from "lucide-react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { useChatStore } from "@/stores/chatStore";

interface HeaderProps {
  onToggleSidebar: () => void;
}

export function Header({ onToggleSidebar }: HeaderProps) {
  const navigate = useNavigate();
  const { currentSessionId, sessions, createSession } = useChatStore();
  const currentSession = sessions.find((session) => session.id === currentSessionId);

  const handleNewChat = () => {
    createSession().catch(() => null);
    navigate("/chat");
  };

  return (
    <header className="sticky top-0 z-20 border-b border-[#F5E9DC] bg-[#FFFBF5]/85 backdrop-blur-sm">
      <div className="flex h-16 items-center justify-between gap-3 px-6">
        <div className="flex min-w-0 items-center gap-2">
          <Button
            variant="ghost"
            size="icon"
            onClick={onToggleSidebar}
            aria-label="切换侧边栏"
            className="text-[#78716C] hover:bg-[#FFF1E6] lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </Button>
          <p className="truncate text-base font-semibold text-[#1C1917]">
            {currentSession?.title || "新对话"}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            onClick={handleNewChat}
            className="inline-flex items-center gap-1.5 rounded-xl border border-[#FED7AA] bg-white px-3.5 py-2 text-sm font-medium text-[#EA580C] transition-colors hover:bg-[#FFF7ED]"
          >
            <Plus className="h-4 w-4" />
            新建对话
          </button>
        </div>
      </div>
    </header>
  );
}
