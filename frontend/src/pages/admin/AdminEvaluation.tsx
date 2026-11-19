import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { ApiError } from "../../api/client";
import { adminEvaluations, EVAL_ERROR_LABEL, EVAL_STATUS_LABEL, type EvaluationResult } from "../../api/adminEvaluations";

const dateTime = new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "short" });

// 06_wireframes.md 8절 (ADM-05·06, EVAL-06)
export default function AdminEvaluation() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [result, setResult] = useState<EvaluationResult | null>(null);
  const [history, setHistory] = useState<EvaluationResult[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(async (evalId: string) => {
    setError(null);
    try {
      const r = await adminEvaluations.waitForResult(evalId);
      setResult(r);
      setHistory(await adminEvaluations.history(r.messageId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "평가를 불러오지 못했습니다.");
    }
  }, []);

  useEffect(() => {
    if (id) load(id);
  }, [id, load]);

  async function reEvaluate() {
    if (!result) return;
    setBusy(true);
    try {
      const newId = await adminEvaluations.request(result.messageId);
      navigate(`/admin/evaluations/${newId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "재평가를 요청하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  if (error)
    return (
      <main className="page">
        <p role="alert" className="error">
          {error}
        </p>
      </main>
    );
  if (!result) return <p className="page">평가 결과를 기다리는 중…</p>;

  return (
    <main className="page wide">
      <h1>
        평가 결과 {result.status === "COMPLETED" ? `· ${result.totalScore} / 100 (추정)` : `· ${EVAL_STATUS_LABEL[result.status]}`}
      </h1>
      <p className="muted">
        Judge 모델 {result.judgeModelId} · 프롬프트 {result.promptVersion} · 루브릭 {result.rubricVersion} ·{" "}
        {dateTime.format(new Date(result.createdAt))}
      </p>
      {result.status === "FAILED" && (
        <p role="alert" className="error">
          평가 실패: {result.errorCode ? (EVAL_ERROR_LABEL[result.errorCode] ?? result.errorCode) : "알 수 없는 오류"}
        </p>
      )}
      {result.scores.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>항목</th>
                <th>점수</th>
                <th>사유</th>
              </tr>
            </thead>
            <tbody>
              {result.scores.map((s) => (
                <tr key={s.criterion}>
                  <td>{s.label}</td>
                  <td>
                    {s.score}/{s.maxScore}
                  </td>
                  <td>{s.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {result.issues.length > 0 && (
        <>
          <h2>문제점</h2>
          <ul>{result.issues.map((x, i) => <li key={i}>{x}</li>)}</ul>
        </>
      )}
      {result.suggestions.length > 0 && (
        <>
          <h2>개선 제안</h2>
          <ul>{result.suggestions.map((x, i) => <li key={i}>{x}</li>)}</ul>
        </>
      )}
      <p className="notice small">※ {result.disclaimer}</p>
      <div className="actions">
        <button type="button" onClick={reEvaluate} disabled={busy}>
          같은 답변 재평가
        </button>
        <Link to="/admin/chats">대화 목록</Link>
      </div>
      {history && history.length > 1 && (
        <>
          <h2>평가 이력</h2>
          <ul>
            {history.map((h) => (
              <li key={h.evaluationId}>
                <Link to={`/admin/evaluations/${h.evaluationId}`}>
                  {dateTime.format(new Date(h.createdAt))} ·{" "}
                  {h.status === "COMPLETED" ? `${h.totalScore}점` : EVAL_STATUS_LABEL[h.status]} · {h.rubricVersion}
                </Link>
                {h.evaluationId === result.evaluationId && <span className="muted small"> (현재)</span>}
              </li>
            ))}
          </ul>
        </>
      )}
    </main>
  );
}
