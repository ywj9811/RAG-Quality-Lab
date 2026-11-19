import { ApiError, apiRequest } from "./client";

export type EvaluationStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";

export interface EvaluationResult {
  evaluationId: string;
  messageId: string;
  status: EvaluationStatus;
  rubricVersion: string;
  judgeModelId: string;
  promptVersion: string;
  totalScore: number | null;
  scores: { criterion: string; label: string; score: number; maxScore: number; reason: string }[];
  issues: string[];
  suggestions: string[];
  disclaimer: string;
  createdAt: string;
  completedAt: string | null;
  errorCode: string | null;
}

/** 대화 상세에 함께 오는 답변별 최신 평가 요약 */
export interface LatestEvaluation {
  id: string;
  status: EvaluationStatus;
  totalScore: number | null;
  createdAt: string;
  count: number;
}

export const POLL_MS = 2500;
export const POLL_LIMIT_MS = 120_000;

export const adminEvaluations = {
  get: (id: string) => apiRequest<EvaluationResult>("GET", `/api/admin/evaluations/${id}`),
  history: (messageId: string) => apiRequest<EvaluationResult[]>("GET", `/api/admin/messages/${messageId}/evaluations`),
  /** 평가를 요청하고 평가 ID를 돌려준다. 이미 진행 중이면 그 평가 ID를 돌려준다. */
  request: async (messageId: string): Promise<string> => {
    try {
      const r = await apiRequest<{ evaluationId: string }>("POST", "/api/admin/evaluations", { messageId });
      return r.evaluationId;
    } catch (err) {
      if (err instanceof ApiError && err.code === "EVALUATION_IN_PROGRESS" && err.extra?.existingEvaluationId) {
        return String(err.extra.existingEvaluationId);
      }
      throw err;
    }
  },
  /** 끝날 때까지(또는 제한 시간까지) 상태를 확인한다. */
  waitForResult: async (id: string, sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))) => {
    const deadline = Date.now() + POLL_LIMIT_MS;
    for (;;) {
      const r = await adminEvaluations.get(id);
      if (r.status === "COMPLETED" || r.status === "FAILED" || Date.now() > deadline) return r;
      await sleep(POLL_MS);
    }
  },
};

export const EVAL_STATUS_LABEL: Record<EvaluationStatus, string> = {
  PENDING: "대기",
  RUNNING: "진행 중",
  COMPLETED: "완료",
  FAILED: "실패",
};

export const EVAL_ERROR_LABEL: Record<string, string> = {
  INVALID_JUDGE_OUTPUT: "평가 모델 응답 형식이 올바르지 않습니다",
  DAILY_QUOTA_EXCEEDED: "오늘 평가 한도를 모두 사용했습니다",
  RATE_LIMITED: "요청이 많아 잠시 후 다시 시도해야 합니다",
  UPSTREAM_RATE_LIMITED: "외부 AI 서비스 한도에 도달했습니다",
  UPSTREAM_TIMEOUT: "평가 모델 응답 시간이 초과되었습니다",
  UPSTREAM_UNAVAILABLE: "평가 모델에 연결할 수 없습니다",
  INTERRUPTED: "서버 재시작으로 중단되었습니다",
  INTERNAL_ERROR: "평가 중 오류가 발생했습니다",
};
