package dev.lunynt.opengate.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.JdbcAccountRepository;
import dev.lunynt.opengate.database.DatabaseConfig;
import dev.lunynt.opengate.database.DatabaseSchema;
import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CookieSessionServiceTest {
    @Test
    void issuesAccountBoundRevocableOpaqueTokens() throws Exception {
        var database = new DatabaseConfig(
                DatabaseType.H2, "jdbc:h2:mem:cookie-session;DB_CLOSE_DELAY=-1", "", "", 2, Duration.ofSeconds(5));
        try (var dataSource = new OpenGateDataSource(database)) {
            DatabaseSchema.migrate(dataSource);
            var account = new Account(UUID.randomUUID(), "CookieUser", IdentityType.OFFLINE, "hash", null, Instant.EPOCH);
            new JdbcAccountRepository(dataSource).save(account);
            try (var sessions = new CookieSessionService(
                    dataSource, Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC),
                    Duration.ofHours(1), true)) {
                var token = sessions.issue(account.playerId()).get().orElseThrow();
                assertTrue(sessions.verify(account.playerId(), token).get());
                assertFalse(sessions.verify(UUID.randomUUID(), token).get());
                assertTrue(sessions.verify(account.playerId(), token).get());
                new JdbcAccountRepository(dataSource).updatePassword(account.playerId(), "changed-hash");
                assertFalse(sessions.verify(account.playerId(), token).get());
                assertFalse(sessions.verify(account.playerId(), token).get());
                token = sessions.issue(account.playerId()).get().orElseThrow();
                incrementGenerationWithoutDeletingSessions(dataSource, account.playerId());
                assertFalse(sessions.verify(account.playerId(), token).get());
                token = sessions.issue(account.playerId()).get().orElseThrow();
                sessions.revokeAll(account.playerId()).get();
                assertFalse(sessions.verify(account.playerId(), token).get());
            }
        }
    }

    private static void incrementGenerationWithoutDeletingSessions(OpenGateDataSource dataSource, UUID playerId)
            throws Exception {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "UPDATE accounts SET session_generation = session_generation + 1 WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }
}
