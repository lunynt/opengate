package dev.lunynt.opengate.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.lunynt.opengate.auth.IdentityType;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteAccountRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void persistsAndFindsAccountsCaseInsensitively() {
        var repository = new SqliteAccountRepository(directory.resolve("accounts.db"));
        var account = account(UUID.randomUUID(), "PlayerOne");

        repository.save(account);

        assertEquals(account, repository.findByUsername("playerone").orElseThrow());
        assertEquals(account, repository.findByPlayerId(account.playerId()).orElseThrow());
    }

    @Test
    void preventsTwoPlayerIdsFromOwningTheSameUsername() {
        var repository = new SqliteAccountRepository(directory.resolve("accounts.db"));
        repository.save(account(UUID.randomUUID(), "PlayerOne"));

        assertThrows(
                AccountAlreadyExistsException.class,
                () -> repository.save(account(UUID.randomUUID(), "PLAYERONE")));
    }

    private static Account account(UUID playerId, String username) {
        return new Account(
                playerId,
                username,
                IdentityType.OFFLINE,
                "hash",
                null,
                Instant.EPOCH,
                null,
                null);
    }
}
