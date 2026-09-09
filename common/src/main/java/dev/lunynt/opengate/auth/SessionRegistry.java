package dev.lunynt.opengate.auth;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class SessionRegistry {
    private final ConcurrentMap<UUID, AuthenticationSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, UUID> releasedByAccount = new ConcurrentHashMap<>();
    private final Clock clock;
    private volatile java.util.function.Consumer<AuthenticationSession> releaseListener = ignored -> {};
    private volatile java.util.function.Consumer<UUID> invalidationListener = ignored -> {};

    public SessionRegistry(Clock clock) {
        this.clock = clock;
    }

    public AuthenticationSession open(UUID connectionId) {
        var session = new AuthenticationSession(connectionId, clock.instant(), this::released);
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
            removeReleasedAccount(session);
            session.close();
        }
    }

    public void invalidate(UUID connectionId) {
        var session = sessions.remove(connectionId);
        if (session != null) {
            removeReleasedAccount(session);
            session.close();
            invalidationListener.accept(connectionId);
        }
    }

    public int size() {
        return sessions.size();
    }

    public void invalidateAll() {
        RuntimeException failure = null;
        for (var connectionId : sessions.keySet()) {
            try {
                invalidate(connectionId);
            } catch (RuntimeException exception) {
                if (failure == null) failure = exception;
                else failure.addSuppressed(exception);
            }
        }
        if (failure != null) throw failure;
    }

    public int closeByAccountId(UUID accountId) {
        var closed = 0;
        for (var entry : sessions.entrySet()) {
            var matches = entry.getValue().identity()
                    .map(identity -> identity.playerId().equals(accountId))
                    .orElse(false);
            if (matches && sessions.remove(entry.getKey(), entry.getValue())) {
                removeReleasedAccount(entry.getValue());
                entry.getValue().close();
                invalidationListener.accept(entry.getKey());
                closed++;
            }
        }
        return closed;
    }

    public void onReleased(java.util.function.Consumer<AuthenticationSession> listener) {
        releaseListener = java.util.Objects.requireNonNull(listener, "listener");
    }

    public void onInvalidated(java.util.function.Consumer<UUID> listener) {
        invalidationListener = java.util.Objects.requireNonNull(listener, "listener");
    }

    private void released(AuthenticationSession session) {
        var accountId = session.identity().orElseThrow().playerId();
        var previous = releasedByAccount.put(accountId, session.connectionId());
        if (previous != null && !previous.equals(session.connectionId())) invalidate(previous);
        releaseListener.accept(session);
    }

    private void removeReleasedAccount(AuthenticationSession session) {
        session.identity().ifPresent(identity ->
                releasedByAccount.remove(identity.playerId(), session.connectionId()));
    }
}
