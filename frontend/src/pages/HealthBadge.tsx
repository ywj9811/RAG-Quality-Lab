import { useEffect, useState } from "react";
import { fetchHealth, type SystemHealth } from "../api/health";

const LABELS: Record<string, string> = {
  db: "데이터베이스",
  ollama: "임베딩 서버(Ollama)",
};

const STATUS_TEXT = { UP: "정상", DOWN: "장애", UNKNOWN: "확인 불가" } as const;

export default function HealthBadge() {
  const [health, setHealth] = useState<SystemHealth | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchHealth().then((h) => {
      if (!cancelled) setHealth(h);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!health) return <p className="health">시스템 상태 확인 중…</p>;

  return (
    <section className="health" aria-label="시스템 상태">
      <p>
        시스템 상태: <strong>{STATUS_TEXT[health.status]}</strong>
      </p>
      <ul>
        {Object.entries(LABELS).map(([key, label]) => (
          <li key={key}>
            {label}: {STATUS_TEXT[health.components[key] ?? "UNKNOWN"]}
          </li>
        ))}
      </ul>
    </section>
  );
}
