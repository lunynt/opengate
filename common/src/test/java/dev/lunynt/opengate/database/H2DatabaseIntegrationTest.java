package dev.lunynt.opengate.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.JdbcAccountRepository;
import dev.lunynt.opengate.auth.IdentityType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class H2DatabaseIntegrationTest {
    @Test
    void upgradesExistingCookieTableWithInvalidatingSessionState() throws Exception {
        var config = new DatabaseConfig(DatabaseType.H2,
                "jdbc:h2:mem:cookie-schema-upgrade;DB_CLOSE_DELAY=-1", "", "", 1, Duration.ofSeconds(5));
        try (var dataSource = new OpenGateDataSource(config)) {
            try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE accounts (
                            player_id VARCHAR(36) PRIMARY KEY NOT NULL,
                            username VARCHAR(64) NOT NULL,
                            normalized_username VARCHAR(64) UNIQUE NOT NULL,
                            identity_type VARCHAR(32) NOT NULL,
                            password_hash VARCHAR(512),
                            totp_secret VARCHAR(1024),
                            created_at BIGINT NOT NULL)
                        """);
                statement.executeUpdate("""
                        CREATE TABLE login_sessions (
                            token_hash VARCHAR(64) PRIMARY KEY NOT NULL,
                            player_id VARCHAR(36) NOT NULL,
                            created_at BIGINT NOT NULL,
                            expires_at BIGINT NOT NULL,
                            FOREIGN KEY (player_id) REFERENCES accounts(player_id) ON DELETE CASCADE)
                        """);
            }

            DatabaseSchema.migrate(dataSource);

            try (var connection = dataSource.getConnection();
                    var columns = connection.getMetaData().getColumns(
                            null, null, "LOGIN_SESSIONS", "CREDENTIAL_FINGERPRINT")) {
                assertTrue(columns.next());
                assertFalse(columns.next());
            }
            assertColumnExists(dataSource, "ACCOUNTS", "SESSION_GENERATION");
            assertColumnExists(dataSource, "LOGIN_SESSIONS", "SESSION_GENERATION");
        }
    }

    @Test
    void migratesAndPersistsUsingPortableRepository() {
        var config = new DatabaseConfig(
                DatabaseType.H2, "jdbc:h2:mem:opengate-test;DB_CLOSE_DELAY=-1", "", "", 2, Duration.ofSeconds(5));
        try (var dataSource = new OpenGateDataSource(config)) {
            DatabaseSchema.migrate(dataSource);
            DatabaseSchema.migrate(dataSource);
            var repository = new JdbcAccountRepository(dataSource);
            var account = new Account(
                    UUID.randomUUID(), "DatabasePlayer", IdentityType.OFFLINE, "argon2id-hash", null, Instant.EPOCH);

            repository.save(account);

            assertEquals(account, repository.findByUsername("databaseplayer").orElseThrow());
            assertTrue(repository.claimTotpStep(account.playerId(), 10));
            assertFalse(repository.claimTotpStep(account.playerId(), 10));
            assertTrue(repository.claimTotpStep(account.playerId(), 11));
        }
    }

    private static void assertColumnExists(OpenGateDataSource dataSource, String table, String column)
            throws Exception {
        try (var connection = dataSource.getConnection();
                var columns = connection.getMetaData().getColumns(null, null, table, column)) {
            assertTrue(columns.next());
            assertFalse(columns.next());
        }
    }
}
