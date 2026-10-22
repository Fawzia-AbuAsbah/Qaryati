package com.qaryati.security.ratelimit;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

@Service
public class LoginRateLimitService {
    private static final int MAX_REQUESTS = 5;
    private static final int MAX_CLIENTS = 10_000;
    private static final long WINDOW_NANOS = Duration.ofMinutes(1).toNanos();

    private final Map<String, Window> attempts = new HashMap<>();
    private final LongSupplier nanoTime;
    private final int maxClients;
    private long lastCleanup;

    public LoginRateLimitService() {
        this(System::nanoTime);
    }

    LoginRateLimitService(LongSupplier nanoTime) {
        this(nanoTime, MAX_CLIENTS);
    }

    LoginRateLimitService(LongSupplier nanoTime, int maxClients) {
        if (maxClients <= 0) {
            throw new IllegalArgumentException("Client capacity must be positive");
        }
        this.nanoTime = nanoTime;
        this.maxClients = maxClients;
        this.lastCleanup = nanoTime.getAsLong();
    }

    // Serialize admission, cleanup and capacity checks so neither the request
    // limit nor the memory limit can be bypassed by concurrent requests.
    public synchronized boolean allowRequest(String clientIp) {
        long now = nanoTime.getAsLong();
        if (now - lastCleanup >= WINDOW_NANOS) {
            attempts.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_NANOS);
            lastCleanup = now;
        }
        Window window = attempts.get(clientIp);
        if (window == null) {
            if (attempts.size() >= maxClients) {
                return false; // Fail closed; never evict a live client's counter.
            }
            attempts.put(clientIp, new Window(now, 1));
            return true;
        }
        if (now - window.startedAt() >= WINDOW_NANOS) {
            attempts.put(clientIp, new Window(now, 1));
            return true;
        }
        if (window.count() >= MAX_REQUESTS) {
            return false; // Saturate counters to prevent integer overflow.
        }
        attempts.put(clientIp, new Window(window.startedAt(), window.count() + 1));
        return true;
    }

    private record Window(long startedAt, int count) {
    }
}
