package dev.lunynt.opengate.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void groupsIpv6PrivacyAddressesByNetwork() {
        assertEquals(
                NetworkAddress.rateLimitKey("2001:db8:1234:5678::1"),
                NetworkAddress.rateLimitKey("2001:db8:1234:5678:abcd::2"));
        org.junit.jupiter.api.Assertions.assertNotEquals(
                NetworkAddress.rateLimitKey("2001:db8:1234:5678::1"),
                NetworkAddress.rateLimitKey("2001:db8:1234:5679::1"));
    }
}
