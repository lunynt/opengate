package dev.lunynt.opengate.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountRepository;
import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.account.LoginRateLimiter;
import dev.lunynt.opengate.audit.AuditEventType;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.audit.AuditRecord;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.crypto.PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class AdminServiceTest {
    @Test
    void lookupAndRevokeAreAudited() {
        var account = new Account(UUID.randomUUID(), "Player", IdentityType.OFFLINE, "hash", null, Instant.EPOCH);
        var repository = new MemoryRepository(account);
        var audit = new MemoryAudit();
        var clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        try (var accounts = new AccountService(
                repository,
                new UnusedHasher(),
                Executors.newSingleThreadExecutor(),
                clock,
                8,
                128,
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(5, Duration.ofHours(1), clock),
                audit)) {
            var admin = new AdminService(accounts, new SessionRegistry(clock), audit);

            assertTrue(admin.lookup("player", "console").isPresent());
            assertTrue(admin.revoke("Player", "console").isPresent());

            assertEquals(AuditEventType.ADMIN_ACCOUNT_LOOKUP, audit.types.get(0));
            assertEquals(AuditEventType.ADMIN_SESSION_REVOKED, audit.types.get(1));
            var published = new java.util.concurrent.atomic.AtomicBoolean();
            var cluster = new dev.lunynt.opengate.cluster.ClusterCoordinator() {
                @Override public java.util.concurrent.CompletionStage<Void> authenticated(UUID id) {
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                @Override public java.util.concurrent.CompletionStage<Void> revoked(UUID id) {
                    published.set(true);
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                }
                @Override public void close() { }
            };
            var failingAdmin = new AdminService(accounts, new SessionRegistry(clock), audit, cluster,
                    id -> java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("database unavailable")));
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> failingAdmin.revoke("Player", "console"));
            assertTrue(published.get());
            assertEquals(2, audit.types.size(), "failed revocation must not be audited as successful");
        }
    }

    private static final class MemoryRepository implements AccountRepository {
        private Account account;

        private MemoryRepository(Account account) {
            this.account = account;
        }

        @Override
        public Optional<Account> findByPlayerId(UUID playerId) {
            return account.playerId().equals(playerId) ? Optional.of(account) : Optional.empty();
        }

        @Override
        public Optional<Account> findByUsername(String username) {
            return account.username().toLowerCase(Locale.ROOT).equals(username.toLowerCase(Locale.ROOT))
                    ? Optional.of(account)
                    : Optional.empty();
        }

        @Override
        public void save(Account account) {
            this.account = account;
        }

        @Override
        public boolean claimTotpStep(UUID playerId, long step) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(UUID playerId) {}
    }

    private static final class MemoryAudit implements AuditLog {
        private final ArrayList<AuditEventType> types = new ArrayList<>();

        @Override
        public void record(
                AuditEventType type, UUID playerId, String username, String address, String detail) {
            types.add(type);
        }

        @Override
        public List<AuditRecord> recent(UUID playerId, int limit) {
            return List.of();
        }
    }

    private static final class UnusedHasher implements PasswordHasher {
        @Override
        public String hash(char[] password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean verify(char[] password, String encodedHash) {
            throw new UnsupportedOperationException();
        }
    }
}
