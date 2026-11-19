import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import AdminHome from "./AdminHome";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const base = {
  from: "2026-11-13", to: "2026-11-19",
  totals: { sessions: 12, questions: 34, failedAnswers: 2, insufficientEvidence: 5, evaluations: 6, failedEvaluations: 1 },
  quality: { rubricVersion: "v1", evaluatedAnswers: 4, averageTotal: 78.4,
    criteriaAverage: { faithfulness: 29.1, relevance: 21, completeness: 14.2, citationAccuracy: null },
    criteriaMax: { faithfulness: 35, relevance: 25, completeness: 20, citationAccuracy: 20 } },
  lowScores: [{ evaluationId: "e1", messageId: "m1", sessionId: "s1", totalScore: 41, question: "JPA 지연 로딩" }],
  usage: { generationCalls: 37, generationBudget: 300, judgeCalls: 3, judgeBudget: 12 },
};

afterEach(() => vi.unstubAllGlobals());

describe("AdminHome dashboard", () => {
  it("renders totals, quality, usage and low scores", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json(base)));
    render(<MemoryRouter><AdminHome /></MemoryRouter>);
    expect(await screen.findByRole("heading", { name: /평균 품질 점수\(추정\) 78.4 \/ 100/ })).toBeInTheDocument();
    expect(screen.getByText("34")).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "근거 충실성 29.1 / 35" })).toBeInTheDocument();
    expect(screen.getByRole("img", { name: "인용 정확성 - / 20" })).toBeInTheDocument();
    expect(screen.getByText(/답변 생성 37 \/ 300/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "41점" })).toHaveAttribute("href", "/admin/evaluations/e1");
    expect(screen.getByLabelText("시작일")).toHaveValue("2026-11-13");
  });

  it("handles no evaluations", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ ...base, lowScores: [],
      quality: { ...base.quality, evaluatedAnswers: 0, averageTotal: null } })));
    render(<MemoryRouter><AdminHome /></MemoryRouter>);
    expect(await screen.findByText("평가 완료된 답변이 없습니다")).toBeInTheDocument();
  });
});
