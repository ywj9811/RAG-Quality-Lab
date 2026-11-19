import { useState } from "react";
import { Link } from "react-router";
import { ApiError } from "../../api/client";
import {
  adminEvaluations,
  EVAL_ERROR_LABEL,
  EVAL_STATUS_LABEL,
  type EvaluationResult,
  type LatestEvaluation,
} from "../../api/adminEvaluations";

/** 대화 상세의 답변별 평가 상태와 [평가 요청] 버튼 (06_wireframes 7절, ADM-04). */
export default function EvaluationControl({ messageId, latest }: { messageId: string; latest: LatestEvaluation | null }) {
  const [current, setCurrent] = useState<{ id: string; status: string; totalScore: number | null; errorCode?: string | null } | null>(
    latest ? { id: latest.id, status: latest.status, totalScore: latest.totalScore } : null,
  );
  const [count, setCount] = useState(latest?.count ?? 0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function request() {
    setBusy(true);
    setError(null);
    try {
      const id = await adminEvaluations.request(messageId);
      setCurrent({ id, status: "PENDING", totalScore: null });
      setCount((c) => c + 1);
      const r = await adminEvaluations.waitForResult(id);
      setCurrent({ id: r.evaluationId, status: r.status, totalScore: r.totalScore, errorCode: r.errorCode });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "평가를 요청하지 못했습니다.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="eval-control">
      {current && (
        <span>
          평가:{" "}
          {current.status === "COMPLETED" ? (
            <Link to={`/admin/evaluations/${current.id}`}>{current.totalScore}점 (추정)</Link>
          ) : (
            <Link to={`/admin/evaluations/${current.id}`}>
              {EVAL_STATUS_LABEL[current.status as EvaluationResult["status"]] ?? current.status}
            </Link>
          )}
          {current.status === "FAILED" && current.errorCode && (
            <span className="muted small"> ({EVAL_ERROR_LABEL[current.errorCode] ?? current.errorCode})</span>
          )}
          {count > 1 && <span className="muted small"> · 이력 {count}건</span>}
        </span>
      )}
      <button type="button" onClick={request} disabled={busy}>
        {busy ? "평가 중…" : current ? "재평가" : "평가 요청"}
      </button>
      {error && (
        <span role="alert" className="error small">
          {error}
        </span>
      )}
    </div>
  );
}
