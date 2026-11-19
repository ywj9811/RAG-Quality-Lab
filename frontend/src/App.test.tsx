import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import App from "./App";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

afterEach(() => {
  vi.unstubAllGlobals();
});

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  );
}

describe("App routes", () => {
  it("shows the visitor notice on /", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
    sessionStorage.clear();
    renderAt("/");
    await screen.findByRole("button", { name: "대화 시작하기" });
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("RAG Quality Lab");
    expect(screen.getByText(/개인정보·회사 기밀을 입력하지 마세요/)).toBeInTheDocument();
    expect(await screen.findByText(/시스템 상태:/)).toBeInTheDocument();
  });

  it("redirects anonymous users from /admin to the login page", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(json({ code: "UNAUTHENTICATED", detail: "x" }, 401)));
    renderAt("/admin");
    expect(await screen.findByRole("heading", { name: "관리자 로그인" })).toBeInTheDocument();
  });

  it("shows the dashboard and username when logged in", async () => {
    vi.stubGlobal("fetch", vi.fn(async (url: string) =>
      url === "/api/admin/auth/me" ? json({ username: "admin" }) : json({ code: "X", detail: "x" }, 500)));
    renderAt("/admin");
    expect(await screen.findByRole("heading", { name: "대시보드" })).toBeInTheDocument();
    expect(screen.getByText("admin")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "로그아웃" })).toBeInTheDocument();
  });

  it("renders not found for unknown paths", () => {
    renderAt("/nope");
    expect(screen.getByRole("heading", { name: "페이지를 찾을 수 없습니다" })).toBeInTheDocument();
  });
});
