import { type FormEvent, useEffect, useState } from "react";
import { Link } from "react-router";
import { adminChats, type ChatFilter, type Page, PAGE_SIZE, SESSION_STATUS_LABEL, type SessionSummary } from "../../api/adminChats";
import { ApiError } from "../../api/client";

const dateTime = new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "short" });

// 06_wireframes.md 7절 (목록)
export default function AdminChats() {
  const [filter, setFilter] = useState<ChatFilter>({ page: 0 });
  const [draft, setDraft] = useState({ from: "", to: "", status: "" });
  const [data, setData] = useState<Page<SessionSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    adminChats
      .list(filter)
      .then((d) => !cancelled && setData(d))
      .catch((err) => !cancelled && setError(err instanceof ApiError ? err.message : "목록을 불러오지 못했습니다."));
    return () => {
      cancelled = true;
    };
  }, [filter]);

  function onSearch(e: FormEvent) {
    e.preventDefault();
    setFilter({ page: 0, from: draft.from || undefined, to: draft.to || undefined, status: draft.status || undefined });
  }

  const lastPage = data ? Math.max(0, Math.ceil(data.totalElements / PAGE_SIZE) - 1) : 0;

  return (
    <main className="page wide">
      <h1>대화 세션</h1>
      <form onSubmit={onSearch} className="upload" aria-label="대화 검색">
        <label>
          시작일
          <input type="date" value={draft.from} onChange={(e) => setDraft({ ...draft, from: e.target.value })} />
        </label>
        <label>
          종료일
          <input type="date" value={draft.to} onChange={(e) => setDraft({ ...draft, to: e.target.value })} />
        </label>
        <label>
          상태
          <select value={draft.status} onChange={(e) => setDraft({ ...draft, status: e.target.value })}>
            <option value="">전체</option>
            <option value="ACTIVE">진행 중</option>
            <option value="CLOSED">종료</option>
            <option value="EXPIRED">만료</option>
          </select>
        </label>
        <button type="submit">검색</button>
      </form>

      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}

      {data === null ? (
        !error && <p>불러오는 중…</p>
      ) : data.items.length === 0 ? (
        <p className="muted">조건에 맞는 대화가 없습니다.</p>
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>시작</th>
                  <th>상태</th>
                  <th>첫 질문</th>
                  <th>질문 수</th>
                  <th>오류</th>
                  <th>마지막 활동</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((s) => (
                  <tr key={s.id}>
                    <td>
                      <Link to={`/admin/chats/${s.id}`}>{dateTime.format(new Date(s.createdAt))}</Link>
                    </td>
                    <td>{SESSION_STATUS_LABEL[s.status]}</td>
                    <td className="truncate">{s.firstQuestion ?? <span className="muted">(질문 없음)</span>}</td>
                    <td>{s.questionCount}</td>
                    <td>{s.errorCount > 0 ? <span className="status-failed">{s.errorCount}</span> : 0}</td>
                    <td>{dateTime.format(new Date(s.lastActivityAt))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <nav className="pager" aria-label="페이지">
            <button type="button" disabled={filter.page === 0} onClick={() => setFilter({ ...filter, page: filter.page - 1 })}>
              이전
            </button>
            <span>
              {filter.page + 1} / {lastPage + 1} (총 {data.totalElements}건)
            </span>
            <button
              type="button"
              disabled={filter.page >= lastPage}
              onClick={() => setFilter({ ...filter, page: filter.page + 1 })}
            >
              다음
            </button>
          </nav>
        </>
      )}
    </main>
  );
}
