package dev.lunynt.opengate.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.crypto.Argon2idPasswordHasher;
import dev.lunynt.opengate.crypto.MigratingPasswordHasher;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.database.DatabaseConfig;
import dev.lunynt.opengate.database.DatabaseSchema;
import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

class AccountServiceTest {
    @TempDir
    Path directory;

    @Test
    void registersAndAuthenticatesWithoutCreatingAnIpSession() {
        var clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
        try (var dataSource = dataSource("accounts.db");
                var service = new AccountService(
                new JdbcAccountRepository(dataSource),
                new Argon2idPasswordHasher(),
                Executors.newSingleThreadExecutor(),
                clock,
                8,
                128,
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(5, Duration.ofHours(1), clock),
                AuditLog.noop())) {
            var playerId = UUID.randomUUID();
            var password = "correct horse battery staple".toCharArray();
            var account = service.register(playerId, "Player", IdentityType.OFFLINE, password, "127.0.0.1").join();

            assertEquals(
                    AuthenticationResult.WRONG_PASSWORD,
                    service.authenticate(playerId, "incorrect".toCharArray(), "127.0.0.1").join());
            assertEquals(
                    AuthenticationResult.SUCCESS,
                    service.authenticate(playerId, password, "127.0.0.1").join());

            var updated = service.find(playerId).orElseThrow();
            assertEquals(account.createdAt(), updated.createdAt());
        }
    }

    @Test
    void changesPasswordRevokesSessionAndDeletesAccount() {
        var clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
        try (var dataSource = dataSource("lifecycle.db");
                var service = new AccountService(
                new JdbcAccountRepository(dataSource),
                new Argon2idPasswordHasher(),
                Executors.newSingleThreadExecutor(),
                clock,
                8,
                128,
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(5, Duration.ofHours(1), clock),
                AuditLog.noop())) {
            var playerId = UUID.randomUUID();
            service.register(
                            playerId,
                            "Player",
                            IdentityType.OFFLINE,
                            "old password".toCharArray(),
                            "127.0.0.1")
                    .join();

            assertEquals(
                    AccountActionResult.SUCCESS,
                    service.changePassword(
                                    playerId,
                                    "old password".toCharArray(),
                                    "new password".toCharArray(),
                                    "127.0.0.1")
                            .join());
            assertEquals(
                    AuthenticationResult.SUCCESS,
                    service.authenticate(playerId, "new password".toCharArray(), "127.0.0.1").join());

            assertEquals(
                    AccountActionResult.SUCCESS,
                    service.delete(playerId, "new password".toCharArray(), "127.0.0.1").join());
            assertTrue(service.find(playerId).isEmpty());
        }
    }

    @Test
    void successfulLegacyBcryptLoginMigratesHashToArgon2id() {
        var clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
        try (var dataSource = dataSource("legacy-hash.db")) {
            var repository = new JdbcAccountRepository(dataSource);
            var playerId = UUID.randomUUID();
            var password = "legacy password".toCharArray();
            var salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            repository.save(new Account(
                    playerId,
                    "LegacyPlayer",
                    IdentityType.OFFLINE,
                    OpenBSDBCrypt.generate(password, salt, 10),
                    null,
                    clock.instant()));
            try (var service = new AccountService(
                    repository,
                new MigratingPasswordHasher(new Argon2idPasswordHasher()),
                Executors.newSingleThreadExecutor(),
                clock,
                8,
                128,
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(5, Duration.ofHours(1), clock),
                AuditLog.noop())) {
                assertEquals(AuthenticationResult.SUCCESS,
                        service.authenticate(playerId, password, "127.0.0.1").join());
                assertTrue(service.find(playerId).orElseThrow().passwordHash().startsWith("$argon2id$"));
            }
        }
    }

    private OpenGateDataSource dataSource(String name) {
        var config = new DatabaseConfig(DatabaseType.SQLITE, "jdbc:sqlite:" + directory.resolve(name),
                "", "", 1, Duration.ofSeconds(5));
        var dataSource = new OpenGateDataSource(config);
        DatabaseSchema.migrate(dataSource);
        return dataSource;
    }
}
