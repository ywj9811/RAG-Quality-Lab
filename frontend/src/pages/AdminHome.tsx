import { type FormEvent, useCallback, useEffect, useState } from "react";
import { Link } from "react-router";
import { adminDashboard, CRITERION_LABEL, type Dashboard } from "../api/adminDashboard";
import { ApiError } from "../api/client";

function Card({ label, value, tone }: { label: string; value: number; tone?: "bad" }) {
  return (
    <div className={`card ${tone === "bad" && value > 0 ? "card-bad" : ""}`}>
      <div className="card-value">{value.toLocaleString()}</div>
      <div className="muted small">{label}</div>
    </div>
  );
}

function Meter({ label, value, max }: { label: string; value: number | null; max: number }) {
  const ratio = value == null ? 0 : Math.max(0, Math.min(1, value / max));
  return (
    <div className="meter-row">
      <span className="meter-label">{label}</span>
      <span className="meter" role="img" aria-label={`${label} ${value ?? "-"} / ${max}`}>
        <span className="meter-fill" style={{ width: `${ratio * 100}%` }} />
      </span>
      <span className="meter-value">
        {value ?? "-"} / {max}
      </span>
    </div>
  );
}

// 06_wireframes.md 5절 (ADM-07). 평균은 답변별 최신 완료 평가 1건 기준
export default function AdminHome() {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [range, setRange] = useState({ from: "", to: "" });

  const load = useCallback(async (from?: string, to?: string) => {
    setError(null);
    try {
      const d = await adminDashboard.get(from, to);
      setData(d);
      setRange({ from: d.from, to: d.to });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "대시보드를 불러오지 못했습니다.");
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    load(range.from || undefined, range.to || undefined);
  }

  return (
    <main className="page wide">
      <h1>대시보드</h1>
      <form onSubmit={onSubmit} className="upload" aria-label="기간">
        <label>
          시작일
          <input type="date" value={range.from} onChange={(e) => setRange({ ...range, from: e.target.value })} />
        </label>
        <label>
          종료일
          <input type="date" value={range.to} onChange={(e) => setRange({ ...range, to: e.target.value })} />
        </label>
        <button type="submit">조회</button>
      </form>
      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}
      {!data ? (
        !error && <p>불러오는 중…</p>
      ) : (
        <>
          <section className="cards" aria-label="합계">
            <Card label="세션" value={data.totals.sessions} />
            <Card label="질문" value={data.totals.questions} />
            <Card label="근거 부족 답변" value={data.totals.insufficientEvidence} />
            <Card label="답변 실패" value={data.totals.failedAnswers} tone="bad" />
            <Card label="평가" value={data.totals.evaluations} />
            <Card label="평가 실패" value={data.totals.failedEvaluations} tone="bad" />
          </section>

          <section aria-label="품질">
            <h2>
              평균 품질 점수(추정){" "}
              {data.quality.averageTotal == null ? (
                <span className="muted">평가 완료된 답변이 없습니다</span>
              ) : (
                <>
                  {data.quality.averageTotal} / 100
                  <span className="muted small">
                    {" "}
                    · 루브릭 {data.quality.rubricVersion} · 답변 {data.quality.evaluatedAnswers}건
                  </span>
                </>
              )}
            </h2>
            {Object.entries(data.quality.criteriaMax).map(([key, max]) => (
              <Meter key={key} label={CRITERION_LABEL[key] ?? key} value={data.quality.criteriaAverage[key] ?? null} max={max} />
            ))}
          </section>

          <section aria-label="사용량">
            <h2>오늘 외부 호출 사용량</h2>
            <p>
              답변 생성 {data.usage.generationCalls} / {data.usage.generationBudget} · 평가 {data.usage.judgeCalls} /{" "}
              {data.usage.judgeBudget}
              <span className="muted small"> (태평양 시간 자정에 초기화)</span>
            </p>
          </section>

          <section aria-label="낮은 점수">
            <h2>낮은 점수 답변</h2>
            {data.lowScores.length === 0 ? (
              <p className="muted">없음</p>
            ) : (
              <ul>
                {data.lowScores.map((l) => (
                  <li key={l.evaluationId}>
                    <Link to={`/admin/evaluations/${l.evaluationId}`}>{l.totalScore}점</Link> ·{" "}
                    <Link to={`/admin/chats/${l.sessionId}`}>{l.question ?? "(질문 없음)"}</Link>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <p className="notice small">※ LLM이 추정한 품질 점수이며 실제 정답률이 아닙니다.</p>
        </>
      )}
    </main>
  );
}
