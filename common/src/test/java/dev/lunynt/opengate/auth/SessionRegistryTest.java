package dev.lunynt.opengate.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {
    @Test
    void bulkInvalidationClosesAllSessionsEvenIfOneDisconnectCallbackFails() {
        var registry = new SessionRegistry(Clock.systemUTC());
        var first = registry.open(UUID.randomUUID());
        var second = registry.open(UUID.randomUUID());
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        registry.onInvalidated(id -> {
            calls.incrementAndGet();
            throw new IllegalStateException("platform disconnect failed");
        });

        var failure = assertThrows(IllegalStateException.class, registry::invalidateAll);

        assertEquals(0, registry.size());
        assertEquals(AuthenticationState.CLOSED, first.state());
        assertEquals(AuthenticationState.CLOSED, second.state());
        assertEquals(2, calls.get());
        assertEquals(1, failure.getSuppressed().length);
    }

    @Test
    void ownsOneSessionPerConnection() {
        var connectionId = UUID.randomUUID();
        var registry = new SessionRegistry(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        var session = registry.open(connectionId);

        assertEquals(Instant.EPOCH, session.createdAt());
        assertThrows(IllegalStateException.class, () -> registry.open(connectionId));

        registry.close(connectionId);
        assertEquals(AuthenticationState.CLOSED, session.state());
        assertEquals(0, registry.size());
    }

    @Test
    void clusterInvalidationClosesEveryConnectionForAccount() {
        var registry = new SessionRegistry(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var accountId = UUID.randomUUID();
        var connectionId = UUID.randomUUID();
        var invalidated = new java.util.concurrent.atomic.AtomicBoolean();
        registry.onInvalidated(id -> invalidated.set(id.equals(connectionId)));
        var session = registry.open(connectionId);
        session.resolve(new ResolvedIdentity("Player", accountId, IdentityType.OFFLINE, true, true, false));

        assertEquals(1, registry.closeByAccountId(accountId));
        assertEquals(AuthenticationState.CLOSED, session.state());
        assertTrue(invalidated.get());
    }
}
