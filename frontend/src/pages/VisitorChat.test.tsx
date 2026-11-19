import { fireEvent, render, screen, within } from "@testing-library/react";
import VisitorChat from "./VisitorChat";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const created = { sessionId: "s1", status: "ACTIVE", expiresAt: "2026-11-20T00:00:00Z", idleTimeoutSeconds: 1800, notice: "" };
const health = { status: "UP", components: {} };

/** URL·메서드별 응답을 지정하는 가짜 fetch. 헬스체크는 항상 정상. */
function routes(map: Record<string, () => Response>) {
  return vi.fn(async (url: string, init?: RequestInit) => {
    if (url === "/actuator/health") return json(health);
    const key = `${init?.method ?? "GET"} ${url}`;
    const handler = map[key];
    if (!handler) throw new Error(`unexpected ${key}`);
    return handler();
  });
}

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=t";
  sessionStorage.clear();
});

afterEach(() => {
  vi.unstubAllGlobals();
});

async function startChat() {
  render(<VisitorChat />);
  fireEvent.click(await screen.findByRole("button", { name: "대화 시작하기" }));
  return screen.findByLabelText("질문");
}

describe("VisitorChat", () => {
  it("shows the notice and starts a session", async () => {
    const f = routes({ "POST /api/chat/sessions": () => json(created, 201) });
    vi.stubGlobal("fetch", f);
    render(<VisitorChat />);
    expect(await screen.findByText(/개인정보·회사 기밀을 입력하지 마세요/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "대화 시작하기" }));
    expect(await screen.findByLabelText("질문")).toBeInTheDocument();
    expect(sessionStorage.getItem("rql.chat.sessionId")).toBe("s1");
  });

  it("sends a question and renders the answer as plain text with citations", async () => {
    vi.stubGlobal(
      "fetch",
      routes({
        "POST /api/chat/sessions": () => json(created, 201),
        "POST /api/chat/sessions/s1/messages": () =>
          json({
            questionMessageId: "q1",
            answerMessageId: "a1",
            answer: "기본값은 REQUIRED입니다 [1]. <b>굵게</b>",
            answerType: "ANSWERED",
            citations: [
              {
                index: 1,
                documentId: "d1",
                filename: "04_transaction.md",
                sourceLocator: { heading: "전파 속성", page: null, charStart: 0, charEnd: 10 },
                snippet: "기본 전파 속성은 REQUIRED",
              },
            ],
            latencyMs: 1200,
          }),
      }),
    );
    const box = await startChat();
    fireEvent.change(box, { target: { value: "기본 전파 속성은?" } });
    fireEvent.click(screen.getByRole("button", { name: "전송" }));

    expect(await screen.findByText(/기본값은 REQUIRED입니다/)).toHaveTextContent("<b>굵게</b>");
    expect(document.querySelector("b")).toBeNull();
    expect(screen.getByText("[1] 04_transaction.md › 전파 속성")).toBeInTheDocument();
    expect(screen.getByText("기본 전파 속성은?")).toBeInTheDocument();
  });

  it("marks insufficient evidence answers", async () => {
    vi.stubGlobal(
      "fetch",
      routes({
        "POST /api/chat/sessions": () => json(created, 201),
        "POST /api/chat/sessions/s1/messages": () =>
          json({ questionMessageId: "q", answerMessageId: "a", answer: "등록된 문서에서 답을 찾지 못했습니다.",
            answerType: "INSUFFICIENT_EVIDENCE", citations: [], latencyMs: 10 }),
      }),
    );
    const box = await startChat();
    fireEvent.change(box, { target: { value: "gRPC?" } });
    fireEvent.click(screen.getByRole("button", { name: "전송" }));
    expect(await screen.findByText("근거 부족")).toBeInTheDocument();
  });

  it("restores the question into the input when it is rejected", async () => {
    vi.stubGlobal(
      "fetch",
      routes({
        "POST /api/chat/sessions": () => json(created, 201),
        "POST /api/chat/sessions/s1/messages": () =>
          json({ code: "PERSONAL_INFO_DETECTED", detail: "개인정보를 지우고 다시 질문해 주세요." }, 400),
      }),
    );
    const box = await startChat();
    fireEvent.change(box, { target: { value: "메일 a@b.com" } });
    fireEvent.click(screen.getByRole("button", { name: "전송" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("개인정보를 지우고");
    expect(screen.getByLabelText("질문")).toHaveValue("메일 a@b.com");
  });

  it("shows the quota message and keeps the question in history", async () => {
    vi.stubGlobal(
      "fetch",
      routes({
        "POST /api/chat/sessions": () => json(created, 201),
        "POST /api/chat/sessions/s1/messages": () =>
          json({ code: "DAILY_QUOTA_EXCEEDED", detail: "오늘 사용할 수 있는 요청을 모두 사용했습니다." }, 503),
      }),
    );
    const box = await startChat();
    fireEvent.change(box, { target: { value: "질문" } });
    fireEvent.click(screen.getByRole("button", { name: "전송" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("모두 사용했습니다");
  });

  it("moves to the ended state when the session has expired", async () => {
    vi.stubGlobal(
      "fetch",
      routes({
        "POST /api/chat/sessions": () => json(created, 201),
        "POST /api/chat/sessions/s1/messages": () =>
          json({ code: "SESSION_EXPIRED", detail: "대화 시간이 만료되었습니다." }, 410),
      }),
    );
    const box = await startChat();
    fireEvent.change(box, { target: { value: "질문" } });
    fireEvent.click(screen.getByRole("button", { name: "전송" }));
    expect(await screen.findByText("대화 시간이 만료되었습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새 대화 시작" })).toBeInTheDocument();
    expect(sessionStorage.getItem("rql.chat.sessionId")).toBeNull();
  });

  it("confirms before closing", async () => {
    const f = routes({
      "POST /api/chat/sessions": () => json(created, 201),
      "POST /api/chat/sessions/s1/close": () => new Response(null, { status: 204 }),
    });
    vi.stubGlobal("fetch", f);
    await startChat();
    fireEvent.click(screen.getByRole("button", { name: "대화 종료" }));
    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("다시 볼 수 없습니다");
    fireEvent.click(within(dialog).getByRole("button", { name: "종료" }));
    expect(await screen.findByText("대화를 종료했습니다.")).toBeInTheDocument();
    expect(f.mock.calls.some(([u, i]) => u === "/api/chat/sessions/s1/close" && i?.method === "POST")).toBe(true);
  });

  it("restores an existing session after reload", async () => {
    sessionStorage.setItem("rql.chat.sessionId", "s1");
    vi.stubGlobal(
      "fetch",
      routes({
        "GET /api/chat/sessions/s1": () =>
          json({ sessionId: "s1", status: "ACTIVE", expiresAt: "x", messages: [
            { id: "q", role: "USER", content: "이전 질문", answerType: null, citations: [], createdAt: "x" },
            { id: "a", role: "ASSISTANT", content: "이전 답변", answerType: "ANSWERED", citations: [], createdAt: "x" },
          ] }),
      }),
    );
    render(<VisitorChat />);
    expect(await screen.findByText("이전 답변")).toBeInTheDocument();
  });

  it("starts fresh when the saved session is gone", async () => {
    sessionStorage.setItem("rql.chat.sessionId", "old");
    vi.stubGlobal("fetch", routes({ "GET /api/chat/sessions/old": () => json({ code: "SESSION_CLOSED", detail: "x" }, 410) }));
    render(<VisitorChat />);
    expect(await screen.findByRole("button", { name: "대화 시작하기" })).toBeInTheDocument();
    expect(sessionStorage.getItem("rql.chat.sessionId")).toBeNull();
  });
});
