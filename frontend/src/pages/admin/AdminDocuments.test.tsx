import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import AdminDocuments from "./AdminDocuments";
import type { AdminDocument } from "../../api/adminDocuments";

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function doc(overrides: Partial<AdminDocument>): AdminDocument {
  return {
    id: "d1",
    documentKey: "k1",
    version: 1,
    filename: "04_transaction.md",
    contentType: "text/markdown",
    sizeBytes: 100,
    status: "READY",
    failureCode: null,
    chunkCount: 14,
    uploadedAt: "2026-11-14T01:00:00Z",
    processedAt: "2026-11-14T01:00:05Z",
    deletedAt: null,
    ...overrides,
  };
}

beforeEach(() => {
  document.cookie = "XSRF-TOKEN=test-token";
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe("AdminDocuments", () => {
  it("lists documents with status and failure reason", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        json([doc({}), doc({ id: "d2", filename: "scan.pdf", status: "FAILED", failureCode: "NO_TEXT", chunkCount: 0 })]),
      ),
    );
    render(<AdminDocuments />);

    const readyRow = (await screen.findByText("04_transaction.md")).closest("tr")!;
    expect(within(readyRow).getByText("검색 가능")).toBeInTheDocument();
    expect(within(readyRow).getByText("14")).toBeInTheDocument();
    expect(within(readyRow).queryByRole("button", { name: "재처리" })).not.toBeInTheDocument();

    const failedRow = screen.getByText("scan.pdf").closest("tr")!;
    expect(within(failedRow).getByText(/스캔 PDF는 지원하지 않습니다/)).toBeInTheDocument();
    expect(within(failedRow).getByRole("button", { name: "재처리" })).toBeInTheDocument();
  });

  it("uploads a file as multipart with the XSRF header", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json([]))
      .mockResolvedValueOnce(json(doc({ status: "UPLOADED" }), 202))
      .mockResolvedValue(json([doc({ status: "READY" })]));
    vi.stubGlobal("fetch", fetchMock);
    render(<AdminDocuments />);
    await screen.findByText("등록된 문서가 없습니다.");

    const file = new File(["# 제목"], "a.md", { type: "text/markdown" });
    fireEvent.change(screen.getByLabelText(/파일/), { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: "업로드" }));

    await screen.findByText("04_transaction.md");
    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/admin/documents");
    expect(init.method).toBe("POST");
    expect(init.headers["X-XSRF-TOKEN"]).toBe("test-token");
    expect(init.headers["Content-Type"]).toBeUndefined();
    expect((init.body as FormData).get("file")).toBe(file);
  });

  it("shows the server error when upload is rejected", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(json([]))
        .mockResolvedValueOnce(json({ code: "DUPLICATE_DOCUMENT", detail: "같은 내용의 문서가 이미 등록되어 있습니다." }, 409)),
    );
    render(<AdminDocuments />);
    await screen.findByText("등록된 문서가 없습니다.");
    fireEvent.change(screen.getByLabelText(/파일/), { target: { files: [new File(["x"], "a.md")] } });
    fireEvent.click(screen.getByRole("button", { name: "업로드" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("같은 내용의 문서가 이미 등록되어 있습니다.");
  });

  it("asks for confirmation before deleting", async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json([doc({})]))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValue(json([]));
    vi.stubGlobal("fetch", fetchMock);
    render(<AdminDocuments />);
    fireEvent.click(await screen.findByRole("button", { name: "삭제" }));

    const dialog = screen.getByRole("dialog");
    expect(dialog).toHaveTextContent("즉시 검색에서 제외됩니다");
    fireEvent.click(within(dialog).getByRole("button", { name: "취소" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole("button", { name: "삭제" }));
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "삭제" }));
    await screen.findByText("등록된 문서가 없습니다.");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/admin/documents/d1");
    expect(fetchMock.mock.calls[1][1].method).toBe("DELETE");
  });

  it("polls while documents are processing", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json([doc({ status: "PROCESSING" })]))
      .mockResolvedValue(json([doc({ status: "READY" })]));
    vi.stubGlobal("fetch", fetchMock);
    render(<AdminDocuments />);
    expect(await screen.findByText("처리 중")).toBeInTheDocument();

    await vi.advanceTimersByTimeAsync(2100);
    await waitFor(() => expect(screen.getByText("검색 가능")).toBeInTheDocument());
  });
});
