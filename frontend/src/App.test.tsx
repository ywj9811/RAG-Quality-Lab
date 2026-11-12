import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import App from "./App";

beforeEach(() => {
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
});

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
    renderAt("/");
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("RAG Quality Lab");
    expect(screen.getByText(/개인정보·회사 기밀을 입력하지 마세요/)).toBeInTheDocument();
    expect(await screen.findByText(/시스템 상태:/)).toBeInTheDocument();
  });

  it("renders the admin placeholder", () => {
    renderAt("/admin");
    expect(screen.getByRole("heading", { name: "관리자" })).toBeInTheDocument();
  });

  it("renders not found for unknown paths", () => {
    renderAt("/nope");
    expect(screen.getByRole("heading", { name: "페이지를 찾을 수 없습니다" })).toBeInTheDocument();
  });
});
