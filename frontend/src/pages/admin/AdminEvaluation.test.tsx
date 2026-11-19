import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import AdminEvaluation from "./AdminEvaluation";
import EvaluationControl from "./EvaluationControl";
import { adminEvaluations, type EvaluationResult } from "../../api/adminEvaluations";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function result(over: Partial<EvaluationResult> = {}): EvaluationResult {
  return {
    evaluationId: "e1", messageId: "m1", status: "COMPLETED", rubricVersion: "v1", judgeModelId: "gemini-3.6-flash",
    promptVersion: "judge-v1@abcd", totalScore: 83,
    scores: [{ criterion: "faithfulness", label: "근거 충실성", score: 30, maxScore: 35, reason: "근거에 부합" }],
    issues: ["설명 누락"], suggestions: ["조건 추가"], disclaimer: "이 점수는 LLM이 추정한 품질 지표이며 실제 정답률이 아닙니다.",
    createdAt: "2026-11-19T01:00:00Z", completedAt: "2026-11-19T01:00:05Z", errorCode: null, ...over,
  };
}

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=t";
});
afterEach(() => vi.unstubAllGlobals());

describe("evaluation UI", () => {
  it("shows scores, issues, disclaimer and history", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: string) =>
      url.endsWith("/evaluations") ? json([result(), result({ evaluationId: "e0", totalScore: 70 })]) : json(result())));
    render(<MemoryRouter initialEntries={["/admin/evaluations/e1"]}>
      <Routes><Route path="/admin/evaluations/:id" element={<AdminEvaluation />} /></Routes>
    </MemoryRouter>);
    expect(await screen.findByRole("heading", { name: /83 \/ 100 \(추정\)/ })).toBeInTheDocument();
    expect(screen.getByText("근거 충실성")).toBeInTheDocument();
    expect(screen.getByText("30/35")).toBeInTheDocument();
    expect(screen.getByText("설명 누락")).toBeInTheDocument();
    expect(screen.getByText(/실제 정답률이 아닙니다/)).toBeInTheDocument();
    expect(screen.getByText(/70점/)).toBeInTheDocument();
  });

  it("shows failure reason", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: string) =>
      url.endsWith("/evaluations") ? json([]) : json(result({ status: "FAILED", totalScore: null, scores: [], errorCode: "DAILY_QUOTA_EXCEEDED" }))));
    render(<MemoryRouter initialEntries={["/admin/evaluations/e1"]}>
      <Routes><Route path="/admin/evaluations/:id" element={<AdminEvaluation />} /></Routes>
    </MemoryRouter>);
    expect(await screen.findByRole("alert")).toHaveTextContent("오늘 평가 한도를 모두 사용했습니다");
  });

  it("requests an evaluation and shows the score when done", async () => {
    const f = vi.fn(async (_url: string, init?: RequestInit) =>
      init?.method === "POST" ? json({ evaluationId: "e1", status: "PENDING" }, 202) : json(result()));
    vi.stubGlobal("fetch", f);
    render(<MemoryRouter><EvaluationControl messageId="m1" latest={null} /></MemoryRouter>);
    fireEvent.click(screen.getByRole("button", { name: "평가 요청" }));
    expect(await screen.findByText("83점 (추정)")).toBeInTheDocument();
    expect(JSON.parse(f.mock.calls[0][1]!.body as string)).toEqual({ messageId: "m1" });
    expect(screen.getByRole("button", { name: "재평가" })).toBeInTheDocument();
  });

  it("uses the existing evaluation when one is already running", async () => {
    vi.stubGlobal("fetch", vi.fn(async () =>
      json({ code: "EVALUATION_IN_PROGRESS", detail: "이미 평가 중", existingEvaluationId: "e9" }, 409)));
    await expect(adminEvaluations.request("m1")).resolves.toBe("e9");
  });

  it("polls until the evaluation finishes", async () => {
    const statuses = ["PENDING", "RUNNING", "COMPLETED"] as const;
    let i = 0;
    vi.stubGlobal("fetch", vi.fn(async () => json(result({ status: statuses[Math.min(i++, 2)] }))));
    const sleep = vi.fn(async () => {});
    const r = await adminEvaluations.waitForResult("e1", sleep);
    expect(r.status).toBe("COMPLETED");
    expect(sleep).toHaveBeenCalledTimes(2);
  });
});
