package com.ragqualitylab.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FixedWindowRateLimiterTests {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-19T00:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void limitsPerKeyAndResetsNextWindow() {
        MutableClock clock = new MutableClock();
        var limiter = new FixedWindowRateLimiter(2, Duration.ofMinutes(1), clock);
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isZero();
        assertThat(limiter.tryAcquire("a")).isEqualTo(60);
        assertThat(limiter.tryAcquire("b")).isZero();
        clock.now = clock.now.plusSeconds(45);
        assertThat(limiter.tryAcquire("a")).isEqualTo(15);
        clock.now = clock.now.plusSeconds(15);
        assertThat(limiter.tryAcquire("a")).isZero();
    }
}
