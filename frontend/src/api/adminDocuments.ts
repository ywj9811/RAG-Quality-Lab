import { apiRequest, apiUpload } from "./client";

export type DocumentStatus = "UPLOADED" | "PROCESSING" | "READY" | "FAILED";

export interface AdminDocument {
  id: string;
  documentKey: string;
  version: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  failureCode: string | null;
  chunkCount: number;
  uploadedAt: string;
  processedAt: string | null;
  deletedAt: string | null;
}

export const adminDocuments = {
  list: () => apiRequest<AdminDocument[]>("GET", "/api/admin/documents"),
  upload: (file: File, documentKey?: string) => {
    const form = new FormData();
    form.append("file", file);
    const query = documentKey ? `?documentKey=${encodeURIComponent(documentKey)}` : "";
    return apiUpload<AdminDocument>(`/api/admin/documents${query}`, form);
  },
  reprocess: (id: string) => apiRequest<AdminDocument>("POST", `/api/admin/documents/${id}/reprocess`),
  remove: (id: string) => apiRequest<void>("DELETE", `/api/admin/documents/${id}`),
};

export const STATUS_LABEL: Record<DocumentStatus, string> = {
  UPLOADED: "대기",
  PROCESSING: "처리 중",
  READY: "검색 가능",
  FAILED: "실패",
};

export const FAILURE_LABEL: Record<string, string> = {
  NO_TEXT: "텍스트를 추출할 수 없습니다 (스캔 PDF는 지원하지 않습니다)",
  TEXT_EXTRACTION_FAILED: "파일에서 텍스트를 읽지 못했습니다",
  EMBEDDING_UNAVAILABLE: "임베딩 서버(Ollama)에 연결하지 못했습니다",
  FILE_MISSING: "원본 파일을 찾을 수 없습니다",
  INTERRUPTED: "서버 재시작으로 처리가 중단되었습니다",
  INTERNAL_ERROR: "처리 중 오류가 발생했습니다",
};
