import { type FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { type AdminDocument, adminDocuments, FAILURE_LABEL, STATUS_LABEL } from "../../api/adminDocuments";
import { ApiError } from "../../api/client";

/** 처리 중인 문서가 있으면 이 간격으로 목록을 다시 불러온다 */
export const POLL_INTERVAL_MS = 2000;

const dateFormat = new Intl.DateTimeFormat("ko-KR", { dateStyle: "short", timeStyle: "short" });

function errorMessage(err: unknown) {
  return err instanceof ApiError ? err.message : "요청을 처리하지 못했습니다.";
}

// 06_wireframes.md 6절
export default function AdminDocuments() {
  const [docs, setDocs] = useState<AdminDocument[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [file, setFile] = useState<File | null>(null);
  const [documentKey, setDocumentKey] = useState("");
  const [uploading, setUploading] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState<AdminDocument | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const load = useCallback(async () => {
    try {
      setDocs(await adminDocuments.list());
    } catch (err) {
      setError(errorMessage(err));
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const pending = docs?.some((d) => d.status === "UPLOADED" || d.status === "PROCESSING") ?? false;
  useEffect(() => {
    if (!pending) return;
    const timer = setInterval(load, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [pending, load]);

  async function onUpload(e: FormEvent) {
    e.preventDefault();
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      await adminDocuments.upload(file, documentKey || undefined);
      setFile(null);
      setDocumentKey("");
      if (fileInput.current) fileInput.current.value = "";
      await load();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setUploading(false);
    }
  }

  async function run(action: () => Promise<unknown>) {
    setError(null);
    try {
      await action();
      await load();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <main className="page wide">
      <h1>문서 관리</h1>

      <form onSubmit={onUpload} className="upload" aria-label="문서 업로드">
        <label>
          파일 (.pdf .txt .md, 최대 10MB)
          <input
            ref={fileInput}
            type="file"
            accept=".pdf,.txt,.md,.markdown"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          />
        </label>
        <label>
          기존 문서의 새 버전으로 올리기
          <select value={documentKey} onChange={(e) => setDocumentKey(e.target.value)}>
            <option value="">새 문서</option>
            {docs?.map((d) => (
              <option key={d.documentKey} value={d.documentKey}>
                {d.filename} (v{d.version})
              </option>
            ))}
          </select>
        </label>
        <button type="submit" disabled={!file || uploading}>
          {uploading ? "업로드 중…" : "업로드"}
        </button>
      </form>

      {error && (
        <p role="alert" className="error">
          {error}
        </p>
      )}

      {docs === null ? (
        <p>불러오는 중…</p>
      ) : docs.length === 0 ? (
        <p className="muted">등록된 문서가 없습니다.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>파일명</th>
                <th>버전</th>
                <th>상태</th>
                <th>청크</th>
                <th>업로드</th>
                <th>
                  <span className="sr-only">작업</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {docs.map((d) => (
                <tr key={d.id}>
                  <td>{d.filename}</td>
                  <td>v{d.version}</td>
                  <td>
                    <span className={`status status-${d.status.toLowerCase()}`}>{STATUS_LABEL[d.status]}</span>
                    {d.status === "FAILED" && d.failureCode && (
                      <div className="muted small">{FAILURE_LABEL[d.failureCode] ?? d.failureCode}</div>
                    )}
                  </td>
                  <td>{d.status === "READY" ? d.chunkCount : "-"}</td>
                  <td>{dateFormat.format(new Date(d.uploadedAt))}</td>
                  <td className="actions">
                    {d.status === "FAILED" && (
                      <button type="button" onClick={() => run(() => adminDocuments.reprocess(d.id))}>
                        재처리
                      </button>
                    )}
                    <button type="button" onClick={() => setConfirmDelete(d)}>
                      삭제
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {confirmDelete && (
        <div role="dialog" aria-modal="true" aria-labelledby="delete-title" className="dialog">
          <h2 id="delete-title">문서를 삭제할까요?</h2>
          <p>
            <strong>{confirmDelete.filename}</strong>을(를) 삭제하면 즉시 검색에서 제외됩니다. 과거 답변의 근거
            스냅샷은 보존 정책에 따라 유지됩니다.
          </p>
          <div className="actions">
            <button type="button" onClick={() => setConfirmDelete(null)}>
              취소
            </button>
            <button
              type="button"
              className="danger"
              onClick={() => {
                const target = confirmDelete;
                setConfirmDelete(null);
                run(() => adminDocuments.remove(target.id));
              }}
            >
              삭제
            </button>
          </div>
        </div>
      )}
    </main>
  );
}
