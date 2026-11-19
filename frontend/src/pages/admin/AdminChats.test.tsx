import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import AdminChats from "./AdminChats";
import AdminChatDetail from "./AdminChatDetail";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const summary = (i: number) => ({
  id: `s${i}`, status: "CLOSED", createdAt: "2026-11-18T01:00:00Z", lastActivityAt: "2026-11-18T01:10:00Z",
  closedAt: "2026-11-18T01:12:00Z", questionCount: 3, errorCount: i === 0 ? 1 : 0, evaluationCount: 0, firstQuestion: `질문 ${i}`,
});

afterEach(() => vi.unstubAllGlobals());

describe("AdminChats", () => {
  it("lists sessions and sends filters and paging", async () => {
    const f = vi.fn().mockImplementation(async () =>
      json({ items: [summary(0), summary(1)], page: 0, size: 20, totalElements: 45 }));
    vi.stubGlobal("fetch", f);
    render(<MemoryRouter><AdminChats /></MemoryRouter>);

    expect(await screen.findByText("질문 0")).toBeInTheDocument();
    expect(screen.getByText("1 / 3 (총 45건)")).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("시작일"), { target: { value: "2026-11-01" } });
    fireEvent.change(screen.getByLabelText("상태"), { target: { value: "EXPIRED" } });
    fireEvent.click(screen.getByRole("button", { name: "검색" }));
    await vi.waitFor(() => expect(f.mock.calls.at(-1)![0]).toContain("from=2026-11-01"));
    expect(f.mock.calls.at(-1)![0]).toContain("status=EXPIRED");
    expect(f.mock.calls.at(-1)![0]).toContain("page=0");

    fireEvent.click(screen.getByRole("button", { name: "다음" }));
    await vi.waitFor(() => expect(f.mock.calls.at(-1)![0]).toContain("page=1"));
  });

  it("shows detail with answer metadata and retrieval snapshots", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({
      id: "s1", status: "CLOSED", createdAt: "2026-11-18T01:00:00Z", lastActivityAt: "x", expiresAt: "x",
      closedAt: "2026-11-18T01:12:00Z",
      messages: [
        { id: "q", seq: 1, role: "USER", content: "기본 전파 속성은?", answerType: null, modelId: null, promptVersion: null,
          retrievalQuery: null, inputTokens: null, outputTokens: null, latencyMs: null, errorCode: null, createdAt: "x", retrieval: [], latestEvaluation: null },
        { id: "a", seq: 2, role: "ASSISTANT", content: "REQUIRED입니다 [1]", answerType: "ANSWERED",
          modelId: "gemini-3.5-flash-lite", promptVersion: "gen-v1@abcd1234", retrievalQuery: "기본 전파 속성은?",
          inputTokens: 812, outputTokens: 164, latencyMs: 2300, errorCode: null, createdAt: "x", latestEvaluation: null,
          retrieval: [{ rank: 1, score: 0.82, cited: true, documentId: null, chunkAvailable: false, filename: "04_transaction.md",
            documentVersion: 2, sourceLocator: { heading: "전파 속성", page: null }, text: "REQUIRED가 기본" }] },
      ],
    })));
    render(
      <MemoryRouter initialEntries={["/admin/chats/s1"]}>
        <Routes><Route path="/admin/chats/:id" element={<AdminChatDetail />} /></Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByText("REQUIRED입니다 [1]")).toBeInTheDocument();
    expect(screen.getByText(/모델 gemini-3.5-flash-lite · 프롬프트 gen-v1@abcd1234 · 2.3초 · 토큰 812\/164/)).toBeInTheDocument();
    expect(screen.getByText(/#1 0.82 04_transaction.md v2 › 전파 속성/)).toBeInTheDocument();
    expect(screen.getByText("인용됨")).toBeInTheDocument();
    expect(screen.getByText("(원본 삭제됨)")).toBeInTheDocument();
  });

  it("shows not found errors on detail", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ code: "NOT_FOUND", detail: "대화를 찾을 수 없습니다." }, 404)));
    render(
      <MemoryRouter initialEntries={["/admin/chats/x"]}>
        <Routes><Route path="/admin/chats/:id" element={<AdminChatDetail />} /></Routes>
      </MemoryRouter>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("대화를 찾을 수 없습니다.");
  });
});
