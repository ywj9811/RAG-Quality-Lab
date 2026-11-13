export type HealthStatus = "UP" | "DOWN" | "UNKNOWN";

export interface SystemHealth {
  status: HealthStatus;
  components: Record<string, HealthStatus>;
}

interface ActuatorHealth {
  status?: string;
  components?: Record<string, { status?: string }>;
}

function toStatus(value: string | undefined): HealthStatus {
  return value === "UP" || value === "DOWN" ? value : "UNKNOWN";
}

/** /actuator/health 응답을 화면용으로 변환한다. 503(DOWN)도 본문을 읽는다. */
export async function fetchHealth(fetchImpl: typeof fetch = fetch): Promise<SystemHealth> {
  try {
    const res = await fetchImpl("/actuator/health", { headers: { Accept: "application/json" } });
    const body = (await res.json()) as ActuatorHealth;
    const components: Record<string, HealthStatus> = {};
    for (const [name, c] of Object.entries(body.components ?? {})) {
      components[name] = toStatus(c.status);
    }
    return { status: toStatus(body.status), components };
  } catch {
    return { status: "UNKNOWN", components: {} };
  }
}
