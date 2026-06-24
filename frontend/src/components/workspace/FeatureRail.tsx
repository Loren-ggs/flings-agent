import { Activity, Database, Network, Tags, Workflow, type LucideIcon } from "lucide-react";

import { cn } from "@/lib/utils";

export interface FeatureItem {
  key: string;
  label: string;
  icon: LucideIcon;
}

export const FEATURE_ITEMS: FeatureItem[] = [
  { key: "knowledge", label: "知识库管理", icon: Database },
  { key: "intent", label: "意图树配置", icon: Network },
  { key: "ingestion", label: "流水线管理", icon: Workflow },
  { key: "mapping", label: "关键词映射", icon: Tags },
  { key: "traces", label: "链路追踪", icon: Activity }
];

interface FeatureRailProps {
  active: string | null;
  onSelect: (key: string) => void;
}

/**
 * 问答页右侧常驻的功能图标栏。点击图标打开对应功能的抽屉工作台。
 */
export function FeatureRail({ active, onSelect }: FeatureRailProps) {
  return (
    <div className="z-30 hidden w-14 shrink-0 flex-col items-center gap-1.5 border-l border-[#F5E9DC] bg-[#FFFBF5] py-3 lg:flex">
      {FEATURE_ITEMS.map((item) => {
        const Icon = item.icon;
        const isActive = active === item.key;
        return (
          <button
            key={item.key}
            type="button"
            onClick={() => onSelect(item.key)}
            title={item.label}
            aria-label={item.label}
            className={cn(
              "group relative flex h-11 w-11 items-center justify-center rounded-xl transition-colors",
              isActive
                ? "bg-[#FFEDD5] text-[#EA580C]"
                : "text-[#78716C] hover:bg-[#FFF1E6] hover:text-[#EA580C]"
            )}
          >
            <Icon className="h-5 w-5" />
            <span className="pointer-events-none absolute right-full mr-2 whitespace-nowrap rounded-md bg-[#1C1917] px-2 py-1 text-xs font-medium text-white opacity-0 shadow-lg transition-opacity duration-150 group-hover:opacity-100">
              {item.label}
            </span>
          </button>
        );
      })}
    </div>
  );
}
