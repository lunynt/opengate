package dev.lunynt.opengate.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.crypto.Argon2idPasswordHasher;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountServiceTest {
    @TempDir
    Path directory;

    @Test
    void registersAuthenticatesAndCreatesTrustedSession() {
        var clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
        try (var service = new AccountService(
                new SqliteAccountRepository(directory.resolve("accounts.db")),
                new Argon2idPasswordHasher(),
                Executors.newSingleThreadExecutor(),
                clock,
                8,
                128,
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock))) {
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
            assertTrue(service.hasTrustedSession(updated, "127.0.0.1", Duration.ofHours(1)));
            assertFalse(service.hasTrustedSession(updated, "127.0.0.2", Duration.ofHours(1)));
            assertEquals(account.createdAt(), updated.createdAt());
        }
    }

    @Test
    void changesPasswordRevokesSessionAndDeletesAccount() {
        var clock = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);
        try (var service = new AccountService(
                new SqliteAccountRepository(directory.resolve("lifecycle.db")),
                new Argon2idPasswordHasher(),
                Executors.newSingleThreadExecutor(),
                clock,
                8,
                128,
                new LoginRateLimiter(10, Duration.ofMinutes(10), clock))) {
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

            service.revokeTrustedSession(playerId);
            assertFalse(service.hasTrustedSession(
                    service.find(playerId).orElseThrow(), "127.0.0.1", Duration.ofHours(1)));

            assertEquals(
                    AccountActionResult.SUCCESS,
                    service.delete(playerId, "new password".toCharArray(), "127.0.0.1").join());
            assertTrue(service.find(playerId).isEmpty());
        }
    }
}
