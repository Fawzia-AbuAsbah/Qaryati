
package com.qaryati.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
class LoginRateLimitServiceTest {

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
