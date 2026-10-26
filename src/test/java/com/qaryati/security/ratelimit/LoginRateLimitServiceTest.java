
package com.qaryati.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
class LoginRateLimitServiceTest {

    @Test
    void concurrentRequestsAllowExactlyFive() throws Exception {
        LoginRateLimitService service = new LoginRateLimitService();
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(16)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 100; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return service.allowRequest("192.0.2.30");
                }));
            }
            start.countDown();
            int allowed = 0;
            for (var result : results) {
                if (result.get(5, java.util.concurrent.TimeUnit.SECONDS)) allowed++;
            }
            assertEquals(5, allowed);
        }
    }

    @Test
    void capacityFailsClosedAndExpiredEntriesAreReclaimed() {
        AtomicLong time = new AtomicLong();
        LoginRateLimitService service = new LoginRateLimitService(time::get, 2);
        assertTrue(service.allowRequest("a"));
        assertTrue(service.allowRequest("b"));
        assertFalse(service.allowRequest("c"));
        // Rejection of a new client must not evict an existing counter.
        for (int i = 0; i < 4; i++) assertTrue(service.allowRequest("a"));
        assertFalse(service.allowRequest("a"));
        time.set(Duration.ofMinutes(1).toNanos());
        assertTrue(service.allowRequest("c"));
        assertTrue(service.allowRequest("d"));
        assertFalse(service.allowRequest("e"));
    }

    @Test
    void concurrentNewClientsCannotExceedCapacity() throws Exception {
        LoginRateLimitService service = new LoginRateLimitService(() -> 0L, 2);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(16)) {
            var results = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 100; i++) {
                String ip = "client-" + i;
                results.add(executor.submit(() -> service.allowRequest(ip)));
            }
            int allowed = 0;
            for (var result : results) {
                if (result.get(5, java.util.concurrent.TimeUnit.SECONDS)) allowed++;
            }
            assertEquals(2, allowed);
        }
    }

    @Test
    void shouldBlockSixthRequest() {
        LoginRateLimitService service = new LoginRateLimitService();

        for (int i = 0; i < 5; i++) {
            assertTrue(service.allowRequest("192.0.2.10"));
        }

        assertFalse(service.allowRequest("192.0.2.10"));
    }

    @Test
    void shouldTrackDifferentIpsSeparately() {
        LoginRateLimitService service = new LoginRateLimitService();

        for (int i = 0; i < 5; i++) {
            assertTrue(service.allowRequest("192.0.2.10"));
        }

        assertFalse(service.allowRequest("192.0.2.10"));
        assertTrue(service.allowRequest("192.0.2.11"));
    }

    @Test
    void shouldAllowRequestsAfterWindowExpires() {
        AtomicLong fakeTime = new AtomicLong(0);

        LoginRateLimitService service =
                new LoginRateLimitService(fakeTime::get);

        String ip = "192.0.2.20";

        for (int i = 0; i < 5; i++) {
            assertTrue(service.allowRequest(ip));
        }

        assertFalse(service.allowRequest(ip));

        fakeTime.addAndGet(Duration.ofMinutes(1).toNanos());

        assertTrue(service.allowRequest(ip));

        for (int i = 0; i < 4; i++) {
            assertTrue(service.allowRequest(ip));
        }

        assertFalse(service.allowRequest(ip));
    }

}
