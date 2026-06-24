import * as React from "react";
import {
  BookOpen,
  CheckCircle2,
  ChevronDown,
  CircleSlash,
  Compass,
  Loader2,
  Network,
  ShieldCheck,
  Sparkles,
  Wrench,
  XCircle,
  type LucideIcon
} from "lucide-react";

import { cn } from "@/lib/utils";
import type { AgentStatusInfo, AgentTrace } from "@/types";

const ROLE_META: Record<string, { label: string; icon: LucideIcon }> = {
  PLANNER: { label: "规划", icon: Compass },
  KNOWLEDGE: { label: "知识库检索", icon: BookOpen },
  TOOL: { label: "工具调用", icon: Wrench },
  SYNTHESIS: { label: "汇总生成", icon: Sparkles },
  CRITIC: { label: "反思", icon: ShieldCheck }
};

function roleMeta(role: string) {
  return ROLE_META[role] ?? { label: role, icon: Network };
}

function StatusIcon({ status }: { status: string }) {
  switch (status) {
    case "running":
      return <Loader2 className="h-3.5 w-3.5 animate-spin text-orange-500" />;
    case "success":
      return <CheckCircle2 className="h-3.5 w-3.5 text-emerald-500" />;
    case "empty":
      return <CircleSlash className="h-3.5 w-3.5 text-slate-400" />;
    case "error":
      return <XCircle className="h-3.5 w-3.5 text-rose-500" />;
    default:
      return null;
  }
}

function AgentCard({ agent }: { agent: AgentStatusInfo }) {
  const meta = roleMeta(agent.role);
  const Icon = meta.icon;
  return (
    <div className="flex items-start gap-2 rounded-md border border-slate-200 bg-white px-3 py-2">
      <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md bg-orange-50">
        <Icon className="h-3.5 w-3.5 text-orange-500" />
      </div>
      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-slate-700">{meta.label}</span>
          <StatusIcon status={agent.status} />
          {agent.durationMs > 0 ? (
            <span className="text-xs text-slate-400">{agent.durationMs}ms</span>
          ) : null}
        </div>
        {agent.summary ? (
          <p className="mt-0.5 truncate text-xs text-slate-500">{agent.summary}</p>
        ) : null}
      </div>
    </div>
  );
}

interface AgentCollaborationPanelProps {
  trace?: AgentTrace;
}

export function AgentCollaborationPanel({ trace }: AgentCollaborationPanelProps) {
  const [expanded, setExpanded] = React.useState(true);
  if (!trace) return null;

  const agents = trace.agents ?? [];
  const reflections = trace.reflections ?? [];
  const hasAny = Boolean(trace.plan) || agents.length > 0 || reflections.length > 0;
  if (!hasAny) return null;

  const running = agents.some((agent) => agent.status === "running");

  return (
    <div className="overflow-hidden rounded-lg border border-orange-200 bg-orange-50/40">
      <button
        type="button"
        onClick={() => setExpanded((prev) => !prev)}
        className="flex w-full items-center gap-2 px-4 py-3 text-left transition-colors hover:bg-orange-100/40"
      >
        <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-orange-100">
          {running ? (
            <Loader2 className="h-4 w-4 animate-spin text-orange-600" />
          ) : (
            <Network className="h-4 w-4 text-orange-600" />
          )}
        </div>
        <span className="text-sm font-medium text-orange-700">多智能体协作</span>
        <span className="rounded-full bg-orange-100 px-2 py-0.5 text-xs text-orange-600">
          {agents.length} 个智能体
        </span>
        <ChevronDown
          className={cn(
            "ml-auto h-4 w-4 text-orange-500 transition-transform",
            expanded && "rotate-180"
          )}
        />
      </button>
      {expanded ? (
        <div className="space-y-3 border-t border-orange-200 px-4 py-3">
          {trace.plan ? (
            <div>
              <p className="mb-1 text-xs font-medium text-slate-500">执行计划</p>
              {trace.plan.reasoning ? (
                <p className="mb-2 text-xs leading-relaxed text-slate-500">
                  {trace.plan.reasoning}
                </p>
              ) : null}
              <div className="flex flex-wrap gap-1.5">
                {trace.plan.steps.map((step, idx) => {
                  const meta = roleMeta(step.role);
                  const StepIcon = meta.icon;
                  return (
                    <span
                      key={`${step.role}-${idx}`}
                      className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs text-slate-600"
                    >
                      <StepIcon className="h-3 w-3 text-orange-500" />
                      {meta.label}
                    </span>
                  );
                })}
              </div>
            </div>
          ) : null}

          {agents.length > 0 ? (
            <div className="space-y-1.5">
              {agents.map((agent, idx) => (
                <AgentCard key={`${agent.role}-${idx}`} agent={agent} />
              ))}
            </div>
          ) : null}

          {reflections.length > 0 ? (
            <div>
              <p className="mb-1 text-xs font-medium text-slate-500">反思</p>
              <div className="space-y-1">
                {reflections.map((reflection, idx) => (
                  <div key={idx} className="flex items-start gap-2 text-xs">
                    <span
                      className={cn(
                        "shrink-0 rounded-full px-2 py-0.5 font-medium",
                        reflection.verdict === "PASS"
                          ? "bg-emerald-50 text-emerald-600"
                          : "bg-amber-50 text-amber-600"
                      )}
                    >
                      第{reflection.round + 1}轮·{reflection.verdict === "PASS" ? "通过" : "补充检索"}
                    </span>
                    {reflection.reason ? (
                      <span className="text-slate-500">{reflection.reason}</span>
                    ) : null}
                  </div>
                ))}
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
