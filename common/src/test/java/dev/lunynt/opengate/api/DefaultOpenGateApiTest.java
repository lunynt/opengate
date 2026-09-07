package dev.lunynt.opengate.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountRepository;
import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.account.LoginRateLimiter;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.ResolvedIdentity;
import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.crypto.PasswordHasher;
import dev.lunynt.opengate.cluster.ClusterCoordinator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class DefaultOpenGateApiTest {
    @Test
    void revocationReportsClusterFailureAfterInvalidatingLocalSession() {
        var account = new Account(UUID.randomUUID(), "ApiUser", IdentityType.OFFLINE, "hash", null, Instant.EPOCH);
        var clock = Clock.systemUTC();
        var limiter = new LoginRateLimiter(10, Duration.ofMinutes(1), clock);
        var registry = new SessionRegistry(clock);
        var connection = UUID.randomUUID();
        registry.open(connection).resolve(new ResolvedIdentity("ApiUser", account.playerId(),
                IdentityType.OFFLINE, true, true, false));
        var publication = new CompletableFuture<Void>();
        var cluster = new ClusterCoordinator() {
            @Override public CompletableFuture<Void> authenticated(UUID id) { return CompletableFuture.completedFuture(null); }
            @Override public CompletableFuture<Void> revoked(UUID id) { return publication; }
            @Override public void close() { }
        };
        try (var accounts = new AccountService(new InMemoryRepository(account), new UnusedHasher(),
                        Executors.newSingleThreadExecutor(), clock, 8, 128, limiter, limiter, limiter, AuditLog.noop());
                var api = new DefaultOpenGateApi(accounts, registry, null, cluster)) {
            var result = api.revokeSession(connection);
            assertFalse(result.isDone());
            publication.completeExceptionally(new IllegalStateException("Redis unavailable"));
            assertThrows(CompletionException.class, result::join);
            assertTrue(registry.find(connection).isEmpty());
            assertFalse(api.revokeSession(connection).join());
        }
    }

    @Test
    void returnsSanitizedUsersAndRevokesSessions() throws Exception {
        var account = new Account(UUID.randomUUID(), "ApiUser", IdentityType.OFFLINE, "secret-hash", "secret-totp", Instant.EPOCH);
        var repository = new InMemoryRepository(account);
        var clock = Clock.systemUTC();
        var limiter = new LoginRateLimiter(10, Duration.ofMinutes(1), clock);
        try (var accounts = new AccountService(repository, new UnusedHasher(), Executors.newSingleThreadExecutor(),
                        clock, 8, 128, limiter, limiter, limiter, AuditLog.noop());
                var api = new DefaultOpenGateApi(accounts, new SessionRegistry(clock))) {
            var user = api.findUser("ApiUser").toCompletableFuture().get().orElseThrow();
            assertTrue(user.passwordConfigured());
            assertTrue(user.totpConfigured());
            assertFalse(user.toString().contains("secret-hash"));
            assertFalse(user.toString().contains("secret-totp"));
        }
    }

    private static final class InMemoryRepository implements AccountRepository {
        private final Account account;
        private InMemoryRepository(Account account) { this.account = account; }
        @Override public Optional<Account> findByPlayerId(UUID id) { return account.playerId().equals(id) ? Optional.of(account) : Optional.empty(); }
        @Override public Optional<Account> findByUsername(String name) { return account.username().equals(name) ? Optional.of(account) : Optional.empty(); }
        @Override public void save(Account value) { throw new UnsupportedOperationException(); }
        @Override public void updatePassword(UUID id, String hash) { throw new UnsupportedOperationException(); }
        @Override public void updateTotpSecret(UUID id, String secret) { throw new UnsupportedOperationException(); }
        @Override public void delete(UUID id) { throw new UnsupportedOperationException(); }
        @Override public boolean claimTotpStep(UUID id, long step) { throw new UnsupportedOperationException(); }
    }

    private static final class UnusedHasher implements PasswordHasher {
        @Override public String hash(char[] password) { throw new UnsupportedOperationException(); }
        @Override public boolean verify(char[] password, String hash) { throw new UnsupportedOperationException(); }
        @Override public boolean needsRehash(String hash) { return false; }
    }
}
