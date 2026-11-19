import { apiRequest } from "./client";

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
}

export interface SessionSummary {
  id: string;
  status: "ACTIVE" | "CLOSED" | "EXPIRED";
  createdAt: string;
  lastActivityAt: string;
  closedAt: string | null;
  questionCount: number;
  errorCount: number;
  firstQuestion: string | null;
}

export interface RetrievalSnapshot {
  rank: number;
  score: number;
  cited: boolean;
  documentId: string | null;
  chunkAvailable: boolean;
  filename: string;
  documentVersion: number;
  sourceLocator: { heading: string | null; page: number | null };
  text: string;
}

export interface MessageDetail {
  id: string;
  seq: number;
  role: "USER" | "ASSISTANT";
  content: string;
  answerType: string | null;
  modelId: string | null;
  promptVersion: string | null;
  retrievalQuery: string | null;
  inputTokens: number | null;
  outputTokens: number | null;
  latencyMs: number | null;
  errorCode: string | null;
  createdAt: string;
  retrieval: RetrievalSnapshot[];
}

export interface SessionDetail {
  id: string;
  status: SessionSummary["status"];
  createdAt: string;
  lastActivityAt: string;
  expiresAt: string;
  closedAt: string | null;
  messages: MessageDetail[];
}

export interface ChatFilter {
  from?: string;
  to?: string;
  status?: string;
  page: number;
}

export const PAGE_SIZE = 20;

export const adminChats = {
  list: (f: ChatFilter) => {
    const q = new URLSearchParams({ page: String(f.page), size: String(PAGE_SIZE) });
    if (f.from) q.set("from", f.from);
    if (f.to) q.set("to", f.to);
    if (f.status) q.set("status", f.status);
    return apiRequest<Page<SessionSummary>>("GET", `/api/admin/chats?${q}`);
  },
  detail: (id: string) => apiRequest<SessionDetail>("GET", `/api/admin/chats/${id}`),
};

export const SESSION_STATUS_LABEL: Record<SessionSummary["status"], string> = {
  ACTIVE: "진행 중",
  CLOSED: "종료",
  EXPIRED: "만료",
};

export const ANSWER_TYPE_LABEL: Record<string, string> = {
  ANSWERED: "답변",
  INSUFFICIENT_EVIDENCE: "근거 부족",
  ERROR: "오류",
};
