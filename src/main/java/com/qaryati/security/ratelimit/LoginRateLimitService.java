
package com.qaryati.security.ratelimit;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

@Service
public class LoginRateLimitService {

    private static final int MAX_REQUESTS = 5;

    private static final long WINDOW_NANOS =
            Duration.ofMinutes(1).toNanos();

    private static final long CLEANUP_INTERVAL_NANOS =
            Duration.ofMinutes(1).toNanos();

    private final ConcurrentHashMap<String, Window> attempts =
            new ConcurrentHashMap<>();

    private final AtomicLong lastCleanup = new AtomicLong();

    private final LongSupplier nanoTime;

    public LoginRateLimitService() {
        this(System::nanoTime);
    }

    LoginRateLimitService(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
        this.lastCleanup.set(nanoTime.getAsLong());
    }

    public boolean allowRequest(String clientIp) {
        long now = nanoTime.getAsLong();

        cleanupExpiredWindows(now);

        Window updated = attempts.compute(clientIp, (ip, window) -> {
            if (window == null ||
                    now - window.startedAt() >= WINDOW_NANOS) {
                return new Window(now, 1);
            }

            return new Window(window.startedAt(), window.count() + 1);
        });

        return updated.count() <= MAX_REQUESTS;
    }

    private void cleanupExpiredWindows(long now) {
        long previousCleanup = lastCleanup.get();

        if (now - previousCleanup < CLEANUP_INTERVAL_NANOS) {
            return;
        }

        if (!lastCleanup.compareAndSet(previousCleanup, now)) {
            return;
        }

        attempts.forEach((ip, window) -> {
            if (now - window.startedAt() >= WINDOW_NANOS) {
                attempts.computeIfPresent(ip, (key, current) ->
                        current == window ? null : current
                );
            }
        });
    }

    private record Window(long startedAt, int count) {
    }
}
