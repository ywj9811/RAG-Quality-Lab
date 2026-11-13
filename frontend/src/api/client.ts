/** API 오류 (RFC 9457 Problem Details + code). */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

const XSRF_COOKIE = "XSRF-TOKEN";

function readCookie(name: string): string | null {
  const match = document.cookie.split("; ").find((c) => c.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
}

/** CSRF 쿠키가 없으면 서버에서 발급받는다. */
async function ensureXsrfToken(): Promise<string> {
  const existing = readCookie(XSRF_COOKIE);
  if (existing) return existing;
  await fetch("/api/admin/auth/csrf", { credentials: "same-origin" });
  const issued = readCookie(XSRF_COOKIE);
  if (!issued) throw new ApiError(0, "CSRF_UNAVAILABLE", "보안 토큰을 받지 못했습니다. 새로고침해 주세요.");
  return issued;
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = (await res.json()) as { code?: string; detail?: string };
    return new ApiError(res.status, body.code ?? "UNKNOWN", body.detail ?? "요청을 처리하지 못했습니다.");
  } catch {
    return new ApiError(res.status, "UNKNOWN", "요청을 처리하지 못했습니다.");
  }
}

async function send<T>(method: string, path: string, body: BodyInit | undefined, headers: Record<string, string>) {
  headers.Accept = "application/json";
  if (method !== "GET") headers["X-XSRF-TOKEN"] = await ensureXsrfToken();
  let res: Response;
  try {
    res = await fetch(path, { method, headers, credentials: "same-origin", body });
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", "서버에 연결할 수 없습니다.");
  }
  if (!res.ok) throw await toApiError(res);
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export function apiRequest<T>(method: string, path: string, body?: unknown): Promise<T> {
  return body === undefined
    ? send<T>(method, path, undefined, {})
    : send<T>(method, path, JSON.stringify(body), { "Content-Type": "application/json" });
}

/** multipart 업로드. Content-Type은 브라우저가 boundary와 함께 정한다. */
export function apiUpload<T>(path: string, form: FormData): Promise<T> {
  return send<T>("POST", path, form, {});
}
