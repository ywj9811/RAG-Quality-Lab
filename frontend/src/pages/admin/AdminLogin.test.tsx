import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import AdminLogin from "./AdminLogin";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={["/admin/login"]}>
      <Routes>
        <Route path="/admin/login" element={<AdminLogin />} />
        <Route path="/admin" element={<p>관리자 홈</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

function fillAndSubmit() {
  fireEvent.change(screen.getByLabelText("아이디"), { target: { value: "admin" } });
  fireEvent.change(screen.getByLabelText("비밀번호"), { target: { value: "secret-password" } });
  fireEvent.click(screen.getByRole("button", { name: "로그인" }));
}

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=test-token";
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("AdminLogin", () => {
  it("sends credentials with the XSRF header and navigates on success", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ username: "admin" }));
    vi.stubGlobal("fetch", fetchMock);
    renderLogin();
    fillAndSubmit();

    expect(await screen.findByText("관리자 홈")).toBeInTheDocument();
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/admin/auth/login");
    expect(init.headers["X-XSRF-TOKEN"]).toBe("test-token");
    expect(JSON.parse(init.body)).toEqual({ username: "admin", password: "secret-password" });
  });

  it("shows the server message on invalid credentials", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        json({ code: "INVALID_CREDENTIALS", detail: "아이디 또는 비밀번호가 올바르지 않습니다." }, 401),
      ),
    );
    renderLogin();
    fillAndSubmit();
    expect(await screen.findByRole("alert")).toHaveTextContent("아이디 또는 비밀번호가 올바르지 않습니다.");
  });

  it("shows the lock message when the account is locked", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(json({ code: "ACCOUNT_LOCKED", detail: "로그인 시도가 너무 많아 잠시 잠겼습니다." }, 423)),
    );
    renderLogin();
    fillAndSubmit();
    expect(await screen.findByRole("alert")).toHaveTextContent("잠시 잠겼습니다");
  });

  it("shows a connection error when the server is unreachable", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));
    renderLogin();
    fillAndSubmit();
    expect(await screen.findByRole("alert")).toHaveTextContent("서버에 연결할 수 없습니다.");
  });
});
