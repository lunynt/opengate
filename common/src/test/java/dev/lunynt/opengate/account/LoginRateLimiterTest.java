package dev.lunynt.opengate.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginRateLimiterTest {
    @Test
    void blocksAddressAtLimitAndCanBeCleared() {
        var limiter = new LoginRateLimiter(
                2, Duration.ofMinutes(10), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        limiter.recordFailure("127.0.0.1");
        assertFalse(limiter.isBlocked("127.0.0.1"));
        limiter.recordFailure("127.0.0.1");
        assertTrue(limiter.isBlocked("127.0.0.1"));

        limiter.clear("127.0.0.1");
        assertFalse(limiter.isBlocked("127.0.0.1"));
    }
}
