import { apiRequest } from "./client";

export interface Dashboard {
  from: string;
  to: string;
  totals: {
    sessions: number;
    questions: number;
    failedAnswers: number;
    insufficientEvidence: number;
    evaluations: number;
    failedEvaluations: number;
  };
  quality: {
    rubricVersion: string;
    evaluatedAnswers: number;
    averageTotal: number | null;
    criteriaAverage: Record<string, number | null>;
    criteriaMax: Record<string, number>;
  };
  lowScores: { evaluationId: string; messageId: string; sessionId: string; totalScore: number; question: string | null }[];
  usage: { generationCalls: number; generationBudget: number; judgeCalls: number; judgeBudget: number };
}

export const adminDashboard = {
  get: (from?: string, to?: string) => {
    const q = new URLSearchParams();
    if (from) q.set("from", from);
    if (to) q.set("to", to);
    const qs = q.toString();
    return apiRequest<Dashboard>("GET", `/api/admin/dashboard${qs ? `?${qs}` : ""}`);
  },
};

export const CRITERION_LABEL: Record<string, string> = {
  faithfulness: "근거 충실성",
  relevance: "질문 관련성",
  completeness: "완전성",
  citationAccuracy: "인용 정확성",
};
