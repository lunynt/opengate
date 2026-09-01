package dev.lunynt.opengate.database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteSchemaTest {
    @TempDir
    Path directory;

    @Test
    void migratesExistingVersionOneDatabaseWithoutLosingAccounts() throws Exception {
        var file = directory.resolve("upgrade.db");
        var url = "jdbc:sqlite:" + file;
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE accounts (
                        player_id TEXT PRIMARY KEY NOT NULL, username TEXT NOT NULL,
                        normalized_username TEXT UNIQUE NOT NULL, identity_type TEXT NOT NULL,
                        password_hash TEXT, totp_secret TEXT, created_at INTEGER NOT NULL,
                        last_authenticated_at INTEGER, last_address TEXT
                    )
                    """);
            statement.executeUpdate("""
                    INSERT INTO accounts(player_id, username, normalized_username, identity_type, created_at)
                    VALUES('00000000-0000-0000-0000-000000000001', 'Player', 'player', 'OFFLINE', 0)
                    """);
            statement.execute("PRAGMA user_version=1");
        }

        SqliteSchema.migrate(file);

        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            try (var version = statement.executeQuery("PRAGMA user_version")) {
                assertEquals(SqliteSchema.CURRENT_VERSION, version.getInt(1));
            }
            try (var accounts = statement.executeQuery("SELECT COUNT(*) FROM accounts")) {
                assertEquals(1, accounts.getInt(1));
            }
            try (var audits = statement.executeQuery("SELECT COUNT(*) FROM audit_events")) {
                assertEquals(0, audits.getInt(1));
            }
        }
    }

    @Test
    void refusesDatabaseFromNewerOpenGateVersion() throws Exception {
        var file = directory.resolve("future.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + file);
                var statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=999");
        }

        assertThrows(IllegalStateException.class, () -> SqliteSchema.migrate(file));
    }
}
