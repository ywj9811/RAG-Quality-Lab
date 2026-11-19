import { apiRequest } from "./client";

export type AnswerType = "ANSWERED" | "INSUFFICIENT_EVIDENCE" | "ERROR";

export interface Citation {
  index: number;
  documentId: string | null;
  filename: string;
  sourceLocator: { heading: string | null; page: number | null; charStart: number; charEnd: number };
  snippet: string;
}

export interface ChatMessage {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  answerType: AnswerType | null;
  citations: Citation[];
  createdAt: string;
}

export interface CreatedSession {
  sessionId: string;
  status: string;
  expiresAt: string;
  idleTimeoutSeconds: number;
  notice: string;
}

export interface SessionView {
  sessionId: string;
  status: string;
  expiresAt: string;
  messages: ChatMessage[];
}

export interface AskResult {
  questionMessageId: string;
  answerMessageId: string;
  answer: string;
  answerType: AnswerType;
  citations: Citation[];
  latencyMs: number;
}

export const chatApi = {
  create: () => apiRequest<CreatedSession>("POST", "/api/chat/sessions"),
  get: (id: string) => apiRequest<SessionView>("GET", `/api/chat/sessions/${id}`),
  ask: (id: string, content: string) => apiRequest<AskResult>("POST", `/api/chat/sessions/${id}/messages`, { content }),
  close: (id: string) => apiRequest<void>("POST", `/api/chat/sessions/${id}/close`),
};

/** 세션 ID만 탭 저장소에 둔다(토큰은 HttpOnly 쿠키라 스크립트에서 볼 수 없다). */
const KEY = "rql.chat.sessionId";

export const sessionStore = {
  get(): string | null {
    try {
      return sessionStorage.getItem(KEY);
    } catch {
      return null;
    }
  },
  set(id: string) {
    try {
      sessionStorage.setItem(KEY, id);
    } catch {
      /* 저장소를 쓸 수 없으면 새로고침 시 새 대화로 시작한다 */
    }
  },
  clear() {
    try {
      sessionStorage.removeItem(KEY);
    } catch {
      /* 무시 */
    }
  },
};
