import HealthBadge from "./HealthBadge";

// 방문자 시작 화면 골격 (06_wireframes.md 2절). 채팅 기능은 Phase 3에서 추가한다.
export default function VisitorHome() {
  return (
    <main className="page">
      <h1>RAG Quality Lab · Java/Spring 기술 Q&amp;A</h1>
      <p>등록된 문서를 근거로 답변하는 챗봇입니다.</p>
      <aside className="notice" aria-label="이용 안내">
        <strong>안내</strong>
        <ul>
          <li>입력 내용은 저장되며 외부 AI 서비스(Google Gemini)로 전송됩니다.</li>
          <li>개인정보·회사 기밀을 입력하지 마세요.</li>
          <li>대화를 종료하면 다시 볼 수 없습니다.</li>
        </ul>
      </aside>
      <button type="button" disabled>
        대화 시작하기 (준비 중)
      </button>
      <HealthBadge />
    </main>
  );
}
