package com.ragqualitylab.llm.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LlmBudgetDayStartTests {

    @Test
    void dayBoundaryIsMidnightPacific() {
        var budget = new LlmBudget(null, new LlmQuotaProperties(null, null, null, null, null), Clock.systemUTC());
        // 2026-11-19 09:30 KST = 2026-11-18 16:30 PST → 그날 0시 PST = 2026-11-18T08:00Z
        assertThat(budget.dayStart(Instant.parse("2026-11-19T00:30:00Z"))).isEqualTo(Instant.parse("2026-11-18T08:00:00Z"));
        // 여름(PDT, UTC-7)
        assertThat(budget.dayStart(Instant.parse("2026-07-01T12:00:00Z"))).isEqualTo(Instant.parse("2026-07-01T07:00:00Z"));
    }
}
