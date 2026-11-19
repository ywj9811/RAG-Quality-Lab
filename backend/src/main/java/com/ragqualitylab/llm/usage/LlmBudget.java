package com.ragqualitylab.llm.usage;

import com.ragqualitylab.common.ratelimit.FixedWindowRateLimiter;
import com.ragqualitylab.common.web.ApiException;
import com.ragqualitylab.common.web.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 외부 LLM 호출 전 예산을 예약한다 (ADR-0007).
 * - 분당 상한: 인메모리, 초과 시 429 RATE_LIMITED
 * - 일일 상한: llm_usage_records 집계(태평양 시간 자정 기준). 예약 행을 먼저 넣어 동시 요청에도 넘지 않게 한다.
 *   집계와 삽입은 operation_type별 advisory lock으로 직렬화한다. 초과 시 503 DAILY_QUOTA_EXCEEDED
 * 호출 결과는 {@link #complete}로 같은 행에 기록한다. 실패한 호출도 한도를 쓴 것으로 센다.
 */
@Service
public class LlmBudget {

    private final JdbcTemplate jdbc;
    private final LlmQuotaProperties properties;
    private final Clock clock;
    private final Map<OperationType, FixedWindowRateLimiter> perMinute = new EnumMap<>(OperationType.class);

    public LlmBudget(JdbcTemplate jdbc, LlmQuotaProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
        for (OperationType type : new OperationType[] {OperationType.GENERATE, OperationType.JUDGE}) {
            perMinute.put(type, new FixedWindowRateLimiter(properties.perMinute(type), Duration.ofMinutes(1), clock));
        }
    }

    /** 예약 ID를 반환한다. 한도 초과면 QUOTA_BLOCKED 행을 남기고(커밋) 예외를 던진다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public UUID reserve(OperationType type, String provider, String modelId) {
        long retryAfter = perMinute.get(type).tryAcquire("global");
        if (retryAfter > 0) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "요청이 많습니다. 잠시 후 다시 시도해 주세요.", retryAfter);
        }
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, "llm_budget:" + type);
        Instant now = clock.instant();
        Integer used = jdbc.queryForObject("""
            SELECT count(*) FROM llm_usage_records
            WHERE operation_type = ? AND created_at >= ? AND status <> 'QUOTA_BLOCKED'
            """, Integer.class, type.name(), Timestamp.from(dayStart(now)));
        UUID id = UUID.randomUUID();
        boolean blocked = used != null && used >= properties.dailyLimit(type);
        insert(id, type, provider, modelId, blocked ? UsageStatus.QUOTA_BLOCKED : UsageStatus.RESERVED, now);
        if (blocked) {
            throw new ApiException(ErrorCode.DAILY_QUOTA_EXCEEDED,
                "오늘 사용할 수 있는 요청을 모두 사용했습니다. 내일 다시 이용해 주세요.");
        }
        return id;
    }

    /** 예약 후 반드시 호출한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(UUID reservationId, UsageStatus status, Integer inputTokens, Integer outputTokens,
                         Integer latencyMs, UUID messageId, UUID evaluationId) {
        if (reservationId == null) {
            return;
        }
        jdbc.update("""
            UPDATE llm_usage_records SET status = ?, input_tokens = ?, output_tokens = ?, latency_ms = ?,
                   message_id = ?, evaluation_id = ?
            WHERE id = ?
            """, status.name(), inputTokens, outputTokens, latencyMs, messageId, evaluationId, reservationId);
    }

    /** 대시보드 표시용 오늘 사용량. */
    public int usedToday(OperationType type) {
        Integer used = jdbc.queryForObject("""
            SELECT count(*) FROM llm_usage_records
            WHERE operation_type = ? AND created_at >= ? AND status <> 'QUOTA_BLOCKED'
            """, Integer.class, type.name(), Timestamp.from(dayStart(clock.instant())));
        return used == null ? 0 : used;
    }

    Instant dayStart(Instant now) {
        LocalDate day = now.atZone(properties.resetZone()).toLocalDate();
        return day.atStartOfDay(properties.resetZone()).toInstant();
    }

    private void insert(UUID id, OperationType type, String provider, String modelId, UsageStatus status, Instant now) {
        jdbc.update("""
            INSERT INTO llm_usage_records (id, operation_type, provider, model_id, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """, id, type.name(), provider, modelId, status.name(), Timestamp.from(now));
    }
}
