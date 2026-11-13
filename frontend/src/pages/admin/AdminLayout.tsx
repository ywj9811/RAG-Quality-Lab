import { useEffect, useState } from "react";
import { Navigate, NavLink, Outlet, useNavigate } from "react-router";
import { adminAuth, type AdminMe } from "../../api/adminAuth";
import { ApiError } from "../../api/client";

type AuthState = { status: "loading" } | { status: "anonymous" } | { status: "error" } | { status: "ok"; me: AdminMe };

/** 관리자 공통 레이아웃. 로그인하지 않았으면 로그인 화면으로 보낸다 (06_wireframes.md 5절). */
export default function AdminLayout() {
  const navigate = useNavigate();
  const [auth, setAuth] = useState<AuthState>({ status: "loading" });

  useEffect(() => {
    let cancelled = false;
    adminAuth
      .me()
      .then((me) => !cancelled && setAuth({ status: "ok", me }))
      .catch((err) => {
        if (cancelled) return;
        setAuth(err instanceof ApiError && err.status === 401 ? { status: "anonymous" } : { status: "error" });
      });
    return () => {
      cancelled = true;
    };
  }, []);

  async function onLogout() {
    try {
      await adminAuth.logout();
    } finally {
      navigate("/admin/login", { replace: true });
    }
  }

  if (auth.status === "loading") return <p className="page">확인 중…</p>;
  if (auth.status === "anonymous") return <Navigate to="/admin/login" replace />;
  if (auth.status === "error") return <p className="page error">서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.</p>;

  return (
    <div className="admin">
      <nav className="admin-nav" aria-label="관리자 메뉴">
        <strong>RAG QL 관리</strong>
        <NavLink to="/admin" end>
          대시보드
        </NavLink>
        <NavLink to="/admin/documents">문서</NavLink>
        <span className="muted">{auth.me.username}</span>
        <button type="button" onClick={onLogout}>
          로그아웃
        </button>
      </nav>
      <Outlet />
    </div>
  );
}
