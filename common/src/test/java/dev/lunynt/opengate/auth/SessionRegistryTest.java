package dev.lunynt.opengate.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {
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
}
