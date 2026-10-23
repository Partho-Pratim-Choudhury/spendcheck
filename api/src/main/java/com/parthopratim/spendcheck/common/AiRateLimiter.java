package com.parthopratim.spendcheck.common;

import com.parthopratim.spendcheck.config.AppProperties;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory per-user sliding-window limiter for the AI endpoints. One instance per JVM (single Render node) is
 * enough; buckets are keyed by user id and stale ones are swept periodically. Uses wall-clock time, independent of
 * the business {@code Clock}.
 */
@Component
public class AiRateLimiter {

    private static final long WINDOW_MS = 60_000L;

    /** @param retryAfterSeconds seconds until a slot frees; only meaningful when {@code !allowed} */
    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private final int perMinute;
    private final Map<Long, Deque<Long>> hits = new ConcurrentHashMap<>();

    public AiRateLimiter(AppProperties props) {
        this.perMinute = props.aiRateLimit().perMinute();
    }

    /** Records an attempt for {@code userId}, allowing it only if the last minute holds fewer than the limit. */
    public Decision check(long userId) {
        long now = now();
        Deque<Long> window = hits.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (window) {
            evictBefore(window, now - WINDOW_MS);
            if (window.size() >= perMinute) {
                long retryMs = window.peekFirst() + WINDOW_MS - now;
                return new Decision(false, Math.max(1, ceilDiv(retryMs, 1000)));
            }
            window.addLast(now);
            return new Decision(true, 0);
        }
    }

    /** Drops buckets with no hits in the last minute so idle users don't accumulate forever. */
    @Scheduled(fixedDelay = 5 * 60_000L)
    void sweep() {
        long cutoff = now() - WINDOW_MS;
        hits.forEach((userId, window) -> {
            synchronized (window) {
                evictBefore(window, cutoff);
                if (window.isEmpty()) {
                    hits.remove(userId, window);
                }
            }
        });
    }

    private static void evictBefore(Deque<Long> window, long cutoff) {
        while (!window.isEmpty() && window.peekFirst() <= cutoff) {
            window.pollFirst();
        }
    }

    private static long ceilDiv(long a, long b) {
        return (a + b - 1) / b;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
}
