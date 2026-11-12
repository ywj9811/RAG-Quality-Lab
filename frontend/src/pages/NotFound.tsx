import { Link } from "react-router";

export default function NotFound() {
  return (
    <main className="page">
      <h1>페이지를 찾을 수 없습니다</h1>
      <Link to="/">처음으로</Link>
    </main>
  );
}
