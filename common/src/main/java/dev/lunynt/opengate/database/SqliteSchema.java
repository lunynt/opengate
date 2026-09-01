package dev.lunynt.opengate.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class SqliteSchema {
    public static final int CURRENT_VERSION = 2;

    private SqliteSchema() {}

    public static void migrate(Path databaseFile) {
        var jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        try (var connection = DriverManager.getConnection(jdbcUrl)) {
            configure(connection);
            connection.setAutoCommit(false);
            try {
                var version = version(connection);
                if (version > CURRENT_VERSION) {
                    throw new IllegalStateException(
                            "database schema " + version + " is newer than supported " + CURRENT_VERSION);
                }
                if (version < 1) migrateToVersion1(connection);
                if (version < 2) migrateToVersion2(connection);
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("could not migrate OpenGate database", exception);
        }
    }

    private static void configure(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
    }

    private static int version(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var result = statement.executeQuery("PRAGMA user_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static void migrateToVersion1(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS accounts (
                        player_id TEXT PRIMARY KEY NOT NULL,
                        username TEXT NOT NULL,
                        normalized_username TEXT UNIQUE NOT NULL,
                        identity_type TEXT NOT NULL,
                        password_hash TEXT,
                        totp_secret TEXT,
                        created_at INTEGER NOT NULL,
                        last_authenticated_at INTEGER,
                        last_address TEXT
                    )
                    """);
            statement.execute("PRAGMA user_version=1");
        }
    }

    private static void migrateToVersion2(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS audit_events (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        occurred_at INTEGER NOT NULL,
                        event_type TEXT NOT NULL,
                        player_id TEXT,
                        username TEXT,
                        address_fingerprint TEXT,
                        detail TEXT
                    )
                    """);
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS audit_events_player_time ON audit_events(player_id, occurred_at)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS audit_events_type_time ON audit_events(event_type, occurred_at)");
            statement.execute("PRAGMA user_version=2");
        }
    }
}
