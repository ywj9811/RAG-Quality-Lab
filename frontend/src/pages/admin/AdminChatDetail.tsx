import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { adminChats, ANSWER_TYPE_LABEL, type MessageDetail, SESSION_STATUS_LABEL, type SessionDetail } from "../../api/adminChats";
import { ApiError } from "../../api/client";

const dateTime = new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "medium" });

function AnswerMeta({ m }: { m: MessageDetail }) {
  const parts = [
    m.modelId && `모델 ${m.modelId}`,
    m.promptVersion && `프롬프트 ${m.promptVersion}`,
    m.latencyMs != null && `${(m.latencyMs / 1000).toFixed(1)}초`,
    m.inputTokens != null && `토큰 ${m.inputTokens}/${m.outputTokens ?? 0}`,
    m.errorCode && `오류 ${m.errorCode}`,
  ].filter(Boolean);
  return <p className="muted small">{parts.join(" · ")}</p>;
}

function Snapshots({ m }: { m: MessageDetail }) {
  if (m.retrieval.length === 0) return null;
  return (
    <details>
      <summary>검색 근거 (Top-{m.retrieval.length}, 당시 스냅샷)</summary>
      {m.retrievalQuery && <p className="muted small">검색어: {m.retrievalQuery}</p>}
      <ol className="snapshots">
        {m.retrieval.map((r) => (
          <li key={r.rank}>
            <div>
              #{r.rank} {r.score.toFixed(2)} {r.filename} v{r.documentVersion}
              {r.sourceLocator.heading && ` › ${r.sourceLocator.heading}`}
              {r.sourceLocator.page && ` · ${r.sourceLocator.page}쪽`}
              {r.cited && <span className="badge">인용됨</span>}
              {!r.chunkAvailable && <span className="muted small"> (원본 삭제됨)</span>}
            </div>
            <pre className="snippet">{r.text}</pre>
          </li>
        ))}
      </ol>
    </details>
  );
}

// 06_wireframes.md 7절 (상세). 평가 요청은 Phase 5에서 추가한다.
export default function AdminChatDetail() {
  const { id } = useParams();
  const [data, setData] = useState<SessionDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    adminChats
      .detail(id)
      .then(setData)
      .catch((err) => setError(err instanceof ApiError ? err.message : "대화를 불러오지 못했습니다."));
  }, [id]);

  if (error)
    return (
      <main className="page">
        <p role="alert" className="error">
          {error}
        </p>
        <Link to="/admin/chats">목록으로</Link>
      </main>
    );
  if (!data) return <p className="page">불러오는 중…</p>;

  let questionNo = 0;
  return (
    <main className="page wide">
      <p>
        <Link to="/admin/chats">← 목록</Link>
      </p>
      <h1>대화 상세</h1>
      <p className="muted">
        상태: {SESSION_STATUS_LABEL[data.status]} · 생성 {dateTime.format(new Date(data.createdAt))}
        {data.closedAt && ` · 종료 ${dateTime.format(new Date(data.closedAt))}`}
      </p>
      <section className="messages">
        {data.messages.map((m) => {
          if (m.role === "USER") {
            questionNo++;
            return (
              <div key={m.id} className="msg msg-user">
                <strong>Q{questionNo}.</strong> {m.content}
              </div>
            );
          }
          return (
            <div key={m.id} className="msg msg-bot">
              {m.answerType && m.answerType !== "ANSWERED" && (
                <span className={`badge ${m.answerType === "ERROR" ? "badge-error" : ""}`}>
                  {ANSWER_TYPE_LABEL[m.answerType] ?? m.answerType}
                </span>
              )}
              <div className="msg-text">{m.content}</div>
              <AnswerMeta m={m} />
              <Snapshots m={m} />
            </div>
          );
        })}
      </section>
    </main>
  );
}
