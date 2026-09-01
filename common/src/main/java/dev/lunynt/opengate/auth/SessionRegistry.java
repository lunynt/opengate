package dev.lunynt.opengate.auth;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionRegistry {
    private final ConcurrentMap<UUID, AuthenticationSession> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public SessionRegistry(Clock clock) {
        this.clock = clock;
    }

    public AuthenticationSession open(UUID connectionId) {
        var session = new AuthenticationSession(connectionId, clock.instant());
        var existing = sessions.putIfAbsent(connectionId, session);
        if (existing != null) {
            throw new IllegalStateException("a session already exists for " + connectionId);
        }
        return session;
    }

    public Optional<AuthenticationSession> find(UUID connectionId) {
        return Optional.ofNullable(sessions.get(connectionId));
    }

    public void close(UUID connectionId) {
        var session = sessions.remove(connectionId);
        if (session != null) {
            session.close();
        }
    }

    public int size() {
        return sessions.size();
    }
}
