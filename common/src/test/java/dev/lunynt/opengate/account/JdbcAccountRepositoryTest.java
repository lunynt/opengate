package dev.lunynt.opengate.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.database.DatabaseConfig;
import dev.lunynt.opengate.database.DatabaseSchema;
import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdbcAccountRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void persistsAndFindsAccountsCaseInsensitively() {
        try (var dataSource = dataSource("accounts.db")) {
            var repository = new JdbcAccountRepository(dataSource);
            var account = account(UUID.randomUUID(), "PlayerOne");

            repository.save(account);

            assertEquals(account, repository.findByUsername("playerone").orElseThrow());
            assertEquals(account, repository.findByPlayerId(account.playerId()).orElseThrow());
        }
    }

    @Test
    void preventsTwoPlayerIdsFromOwningTheSameUsername() {
        try (var dataSource = dataSource("conflict.db")) {
            var repository = new JdbcAccountRepository(dataSource);
            repository.save(account(UUID.randomUUID(), "PlayerOne"));

            assertThrows(
                    AccountAlreadyExistsException.class,
                    () -> repository.save(account(UUID.randomUUID(), "PLAYERONE")));
        }
    }

    @Test
    void neverOverwritesAnExistingPlayerAccount() {
        try (var dataSource = dataSource("player-conflict.db")) {
            var repository = new JdbcAccountRepository(dataSource);
            var playerId = UUID.randomUUID();
            var original = account(playerId, "PlayerOne");
            var replacement = new Account(
                    playerId,
                    "PlayerOne",
                    IdentityType.PREMIUM,
                    "replacement-hash",
                    "replacement-totp",
                    Instant.now());
            repository.save(original);

            assertThrows(AccountAlreadyExistsException.class, () -> repository.save(replacement));
            assertEquals(original, repository.findByPlayerId(playerId).orElseThrow());
        }
    }

    @Test
    void claimsEachTotpStepOnce() {
        try (var dataSource = dataSource("totp.db")) {
            var repository = new JdbcAccountRepository(dataSource);
            var account = account(UUID.randomUUID(), "PlayerOne");
            repository.save(account);

            assertTrue(repository.claimTotpStep(account.playerId(), 100));
            assertFalse(repository.claimTotpStep(account.playerId(), 100));
            assertFalse(repository.claimTotpStep(account.playerId(), 99));
            assertTrue(repository.claimTotpStep(account.playerId(), 101));

            repository.delete(account.playerId());
            repository.save(account);
            assertTrue(repository.claimTotpStep(account.playerId(), 1));
        }
    }

    private OpenGateDataSource dataSource(String name) {
        var config = new DatabaseConfig(DatabaseType.SQLITE, "jdbc:sqlite:" + directory.resolve(name),
                "", "", 1, Duration.ofSeconds(5));
        var dataSource = new OpenGateDataSource(config);
        DatabaseSchema.migrate(dataSource);
        return dataSource;
    }

    private static Account account(UUID playerId, String username) {
        return new Account(
                playerId,
                username,
                IdentityType.OFFLINE,
                "hash",
                null,
                Instant.EPOCH);
    }
}
