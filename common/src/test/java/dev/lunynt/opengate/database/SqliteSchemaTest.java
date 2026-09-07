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
                    INSERT INTO accounts(
                        player_id, username, normalized_username, identity_type, created_at, last_address
                    ) VALUES(
                        '00000000-0000-0000-0000-000000000001', 'Player', 'player', 'OFFLINE', 0, '203.0.113.42'
                    )
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
            try (var columns = statement.executeQuery("PRAGMA table_info(accounts)")) {
                while (columns.next()) {
                    var name = columns.getString("name");
                    org.junit.jupiter.api.Assertions.assertFalse(name.equals("last_authenticated_at")
                            || name.equals("last_address_fingerprint"));
                }
            }
            try (var audits = statement.executeQuery("SELECT COUNT(*) FROM audit_events")) {
                assertEquals(0, audits.getInt(1));
            }
            try (var mappings = statement.executeQuery("SELECT COUNT(*) FROM identity_mappings")) {
                assertEquals(0, mappings.getInt(1));
            }
            try (var sessions = statement.executeQuery("SELECT COUNT(*) FROM login_sessions")) {
                assertEquals(0, sessions.getInt(1));
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
