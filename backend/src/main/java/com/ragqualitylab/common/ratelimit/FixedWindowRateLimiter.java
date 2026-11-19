package com.ragqualitylab.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키별 고정 창(fixed window) 요청 제한 (ADR-0007: Redis 없이 인메모리, 단일 인스턴스 전제).
 * 재시작하면 초기화된다. 오래된 키는 일정 개수를 넘을 때 정리해 메모리 상한을 둔다.
 */
public class FixedWindowRateLimiter {

    static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, Duration window, Clock clock) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        this.limit = limit;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    /** 허용되면 0, 거절되면 다음 창까지 남은 초(1 이상)를 반환한다. */
    public long tryAcquire(String key) {
        long now = clock.millis();
        long start = now - Math.floorMod(now, windowMillis);
        Window w = windows.compute(key, (k, old) -> old == null || old.start != start ? new Window(start, 1) : old.inc());
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.entrySet().removeIf(e -> e.getValue().start < start);
        }
        if (w.count <= limit) {
            return 0;
        }
        return Math.max(1, (start + windowMillis - now + 999) / 1000);
    }

    private record Window(long start, int count) {
        Window inc() {
            return new Window(start, count + 1);
        }
    }
}
