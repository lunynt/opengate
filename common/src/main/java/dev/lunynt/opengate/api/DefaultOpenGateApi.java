package dev.lunynt.opengate.api;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.auth.AuthenticationSession;
import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.auth.CookieSessionService;
import dev.lunynt.opengate.cluster.ClusterCoordinator;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class DefaultOpenGateApi implements OpenGateApi, AutoCloseable {
    private final AccountService accounts;
    private final SessionRegistry sessions;
    private final ExecutorService executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("opengate-api-", 0).factory());

    private final CookieSessionService cookieSessions;
    private final ClusterCoordinator cluster;

    public DefaultOpenGateApi(AccountService accounts, SessionRegistry sessions) {
        this(accounts, sessions, null, ClusterCoordinator.disabled());
    }

    public DefaultOpenGateApi(
            AccountService accounts,
            SessionRegistry sessions,
            CookieSessionService cookieSessions,
            ClusterCoordinator cluster) {
        this.accounts = java.util.Objects.requireNonNull(accounts, "accounts");
        this.sessions = java.util.Objects.requireNonNull(sessions, "sessions");
        this.cookieSessions = cookieSessions;
        this.cluster = java.util.Objects.requireNonNull(cluster, "cluster");
    }

    @Override
    public CompletableFuture<Optional<OpenGateUser>> findUser(UUID accountId) {
        return CompletableFuture.supplyAsync(() -> accounts.find(accountId).map(DefaultOpenGateApi::user), executor);
    }

    @Override
    public CompletableFuture<Optional<OpenGateUser>> findUser(String username) {
        return CompletableFuture.supplyAsync(() -> accounts.find(username).map(DefaultOpenGateApi::user), executor);
    }

    @Override
    public Optional<OpenGateSession> findSession(UUID connectionId) {
        return sessions.find(connectionId).flatMap(DefaultOpenGateApi::session);
    }

    @Override
    public boolean isAuthenticated(UUID connectionId) {
        return sessions.find(connectionId)
                .map(value -> value.state() == AuthenticationState.RELEASED)
                .orElse(false);
    }

    @Override
    public CompletableFuture<Boolean> revokeSession(UUID connectionId) {
        var session = sessions.find(connectionId);
        if (session.isEmpty()) return CompletableFuture.completedFuture(false);
        var identity = session.flatMap(AuthenticationSession::identity);
        var local = CompletableFuture.runAsync(() -> sessions.invalidate(connectionId), executor);
        if (identity.isEmpty()) return local.thenApply(ignored -> true);
        var accountId = identity.orElseThrow().playerId();
        var cookies = cookieSessions == null ? CompletableFuture.completedFuture(null)
                : CompletableFuture.supplyAsync(() -> cookieSessions.revokeAll(accountId), executor)
                        .thenCompose(result -> result);
        var remote = CompletableFuture.supplyAsync(() -> cluster.revoked(accountId), executor)
                .thenCompose(result -> result);
        return CompletableFuture.allOf(local, cookies, remote).thenApply(ignored -> true);
    }

    private static OpenGateUser user(Account account) {
        return new OpenGateUser(
                account.playerId(),
                account.username(),
                account.identityType(),
                account.createdAt(),
                account.passwordHash() != null,
                account.totpSecret() != null);
    }

    private static Optional<OpenGateSession> session(AuthenticationSession session) {
        return session.identity().map(identity -> new OpenGateSession(
                session.connectionId(), identity.playerId(), session.state(), session.method(), session.createdAt()));
    }

    @Override
    public void close() {
        executor.close();
    }
}
