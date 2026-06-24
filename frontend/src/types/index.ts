export type Role = "user" | "assistant";

export type FeedbackValue = "like" | "dislike" | null;

export type MessageStatus = "streaming" | "done" | "cancelled" | "error";

export interface User {
  userId: string;
  username?: string;
  role: string;
  token: string;
  avatar?: string;
}

export type CurrentUser = Omit<User, "token">;

export interface Session {
  id: string;
  title: string;
  lastTime?: string;
}

export interface Message {
  id: string;
  role: Role;
  content: string;
  thinking?: string;
  thinkingDuration?: number;
  isDeepThinking?: boolean;
  isThinking?: boolean;
  createdAt?: string;
  feedback?: FeedbackValue;
  status?: MessageStatus;
  /** 多智能体协作过程（实时填充，仅当前会话流式时存在） */
  agentTrace?: AgentTrace;
}

export interface StreamMetaPayload {
  conversationId: string;
  taskId: string;
}

export interface MessageDeltaPayload {
  type: string;
  delta: string;
}

export interface CompletionPayload {
  messageId?: string | null;
  title?: string | null;
}

// ==================== 多智能体协作 ====================

export interface AgentPlanStepView {
  role: string;
  agentName: string;
  subQuestion: string;
}

export interface AgentPlanInfo {
  round: number;
  reasoning: string;
  steps: AgentPlanStepView[];
}

export interface AgentStatusInfo {
  role: string;
  agentName: string;
  /** running / success / empty / error */
  status: string;
  durationMs: number;
  summary?: string;
}

export interface AgentReflectionInfo {
  round: number;
  /** PASS / RETRY */
  verdict: string;
  reason: string;
  supplementQuestions: string[];
}

export interface AgentTrace {
  plan?: AgentPlanInfo;
  agents: AgentStatusInfo[];
  reflections: AgentReflectionInfo[];
}
