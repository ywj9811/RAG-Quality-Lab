import { type FormEvent, useEffect, useRef, useState } from "react";
import { type ChatMessage, type Citation, chatApi, sessionStore } from "../api/chat";
import { ApiError } from "../api/client";
import HealthBadge from "./HealthBadge";

export const MAX_QUESTION_CHARS = 1000;
const ENDED_CODES = new Set(["SESSION_CLOSED", "SESSION_EXPIRED", "NOT_FOUND"]);

type Phase = "loading" | "start" | "chat" | "ended";

function locatorLabel(c: Citation) {
  const parts = [c.filename];
  if (c.sourceLocator.heading) parts.push(c.sourceLocator.heading);
  if (c.sourceLocator.page) parts.push(`${c.sourceLocator.page}쪽`);
  return parts.join(" › ");
}

function Citations({ citations }: { citations: Citation[] }) {
  if (citations.length === 0) return null;
  return (
    <div className="citations" aria-label="근거">
      {citations.map((c) => (
        <details key={c.index}>
          <summary>
            [{c.index}] {locatorLabel(c)}
          </summary>
          <p className="snippet">{c.snippet}</p>
        </details>
      ))}
    </div>
  );
}

function Message({ m }: { m: ChatMessage }) {
  if (m.role === "USER") {
    return <div className="msg msg-user">{m.content}</div>;
  }
  return (
    <div className={`msg msg-bot ${m.answerType === "ANSWERED" ? "" : "msg-notice"}`}>
      {m.answerType === "INSUFFICIENT_EVIDENCE" && <span className="badge">근거 부족</span>}
      {m.answerType === "ERROR" && <span className="badge badge-error">오류</span>}
      <div className="msg-text">{m.content}</div>
      <Citations citations={m.citations} />
    </div>
  );
}

// 06_wireframes.md 2·3절. 답변은 HTML·마크다운으로 해석하지 않고 텍스트로만 표시한다(RK-03).
export default function VisitorChat() {
  const [phase, setPhase] = useState<Phase>("loading");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [endedReason, setEndedReason] = useState<string | null>(null);
  const [confirmClose, setConfirmClose] = useState(false);
  const bottom = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const saved = sessionStore.get();
    if (!saved) {
      setPhase("start");
      return;
    }
    chatApi
      .get(saved)
      .then((view) => {
        setSessionId(saved);
        setMessages(view.messages);
        setPhase("chat");
      })
      .catch(() => {
        sessionStore.clear();
        setPhase("start");
      });
  }, []);

  useEffect(() => {
    bottom.current?.scrollIntoView?.({ block: "end" });
  }, [messages]);

  function endWith(reason: string) {
    sessionStore.clear();
    setEndedReason(reason);
    setPhase("ended");
  }

  async function start() {
    setError(null);
    try {
      const created = await chatApi.create();
      sessionStore.set(created.sessionId);
      setSessionId(created.sessionId);
      setMessages([]);
      setEndedReason(null);
      setPhase("chat");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "대화를 시작하지 못했습니다.");
    }
  }

  async function send(e: FormEvent) {
    e.preventDefault();
    const content = input.trim();
    if (!sessionId || !content || sending) return;
    setSending(true);
    setError(null);
    const pending: ChatMessage = {
      id: `pending-${Date.now()}`,
      role: "USER",
      content,
      answerType: null,
      citations: [],
      createdAt: new Date().toISOString(),
    };
    setMessages((prev) => [...prev, pending]);
    setInput("");
    try {
      const r = await chatApi.ask(sessionId, content);
      setMessages((prev) => [
        ...prev.slice(0, -1),
        { ...pending, id: r.questionMessageId },
        {
          id: r.answerMessageId,
          role: "ASSISTANT",
          content: r.answer,
          answerType: r.answerType,
          citations: r.citations,
          createdAt: new Date().toISOString(),
        },
      ]);
    } catch (err) {
      const apiErr = err instanceof ApiError ? err : null;
      if (apiErr && ENDED_CODES.has(apiErr.code)) {
        endWith(apiErr.message);
        return;
      }
      // 저장되지 않은 질문(검증 실패·요청 제한)은 입력창으로 되돌린다
      if (apiErr && (apiErr.status === 400 || apiErr.status === 429 || apiErr.status === 409)) {
        setMessages((prev) => prev.slice(0, -1));
        setInput(content);
      }
      setError(apiErr ? apiErr.message : "답변을 받지 못했습니다.");
    } finally {
      setSending(false);
    }
  }

  async function close() {
    setConfirmClose(false);
    if (!sessionId) return;
    try {
      await chatApi.close(sessionId);
    } catch {
      /* 이미 종료·만료됐어도 화면은 종료 상태로 둔다 */
    }
    endWith("대화를 종료했습니다.");
  }

  if (phase === "loading") return <p className="page">불러오는 중…</p>;

  if (phase === "start" || phase === "ended") {
    return (
      <main className="page">
        <h1>RAG Quality Lab · Java/Spring 기술 Q&amp;A</h1>
        <p>등록된 문서를 근거로 답변하는 챗봇입니다.</p>
        {endedReason && <p className="muted">{endedReason}</p>}
        <aside className="notice" aria-label="이용 안내">
          <strong>안내</strong>
          <ul>
            <li>입력 내용은 저장되며 외부 AI 서비스(Google Gemini)로 전송됩니다.</li>
            <li>개인정보·회사 기밀을 입력하지 마세요.</li>
            <li>대화를 종료하면 다시 볼 수 없습니다.</li>
          </ul>
        </aside>
        {error && (
          <p role="alert" className="error">
            {error}
          </p>
        )}
        <button type="button" onClick={start}>
          {phase === "ended" ? "새 대화 시작" : "대화 시작하기"}
        </button>
        <HealthBadge />
      </main>
    );
  }

  return (
    <main className="page chat">
      <header className="chat-header">
        <strong>RAG Quality Lab</strong>
        <button type="button" onClick={() => setConfirmClose(true)}>
          대화 종료
        </button>
      </header>

      <section className="messages" aria-live="polite" aria-label="대화">
        {messages.length === 0 && <p className="muted">Java·Spring에 대해 질문해 보세요.</p>}
        {messages.map((m) => (
          <Message key={m.id} m={m} />
        ))}
        {sending && <p className="muted">답변을 만드는 중…</p>}
        <div ref={bottom} />
      </section>

      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}

      <form onSubmit={send} className="composer">
        <label htmlFor="question" className="sr-only">
          질문
        </label>
        <textarea
          id="question"
          value={input}
          maxLength={MAX_QUESTION_CHARS}
          placeholder={`질문을 입력하세요 (최대 ${MAX_QUESTION_CHARS.toLocaleString()}자)`}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
              e.preventDefault();
              e.currentTarget.form?.requestSubmit();
            }
          }}
          rows={3}
        />
        <div className="composer-row">
          <span className="muted small">
            {input.length} / {MAX_QUESTION_CHARS}
          </span>
          <button type="submit" disabled={sending || input.trim().length === 0}>
            전송
          </button>
        </div>
      </form>

      {confirmClose && (
        <div role="dialog" aria-modal="true" aria-labelledby="close-title" className="dialog">
          <h2 id="close-title">대화를 종료할까요?</h2>
          <p>종료하면 이 대화를 다시 볼 수 없습니다.</p>
          <div className="actions">
            <button type="button" onClick={() => setConfirmClose(false)}>
              취소
            </button>
            <button type="button" className="danger" onClick={close}>
              종료
            </button>
          </div>
        </div>
      )}
    </main>
  );
}
