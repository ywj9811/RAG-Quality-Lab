import { fetchHealth } from "./health";

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("fetchHealth", () => {
  it("maps actuator components", async () => {
    const fake = vi.fn().mockResolvedValue(
      jsonResponse({ status: "UP", components: { db: { status: "UP" }, ollama: { status: "UP" } } }),
    );
    await expect(fetchHealth(fake)).resolves.toEqual({ status: "UP", components: { db: "UP", ollama: "UP" } });
  });

  it("reads the body of a 503 DOWN response", async () => {
    const fake = vi.fn().mockResolvedValue(
      jsonResponse({ status: "DOWN", components: { db: { status: "UP" }, ollama: { status: "DOWN" } } }, 503),
    );
    const health = await fetchHealth(fake);
    expect(health.status).toBe("DOWN");
    expect(health.components.ollama).toBe("DOWN");
  });

  it("returns UNKNOWN when the request fails", async () => {
    const fake = vi.fn().mockRejectedValue(new TypeError("network"));
    await expect(fetchHealth(fake)).resolves.toEqual({ status: "UNKNOWN", components: {} });
  });
});
