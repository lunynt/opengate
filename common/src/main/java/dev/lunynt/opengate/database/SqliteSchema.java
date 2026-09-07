package dev.lunynt.opengate.database;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

public final class SqliteSchema {
    public static final int CURRENT_VERSION = 8;

    private SqliteSchema() {}

    public static void migrate(Path databaseFile) {
        var jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        try (var connection = SqliteConnections.open(jdbcUrl)) {
            configure(connection);
            connection.setAutoCommit(false);
            try {
                migrate(connection);
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

    static void migrate(Connection connection) throws SQLException {
        var version = version(connection);
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException(
                    "database schema " + version + " is newer than supported " + CURRENT_VERSION);
        }
        if (version < 1) migrateToVersion1(connection);
        if (version < 2) migrateToVersion2(connection);
        if (version < 3) migrateToVersion3(connection);
        if (version < 4) migrateToVersion4(connection);
        if (version < 5) migrateToVersion5(connection);
        if (version < 6) migrateToVersion6(connection);
        if (version < 7) migrateToVersion7(connection);
        if (version < 8) migrateToVersion8(connection);
    }

    private static void configure(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
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

    private static void migrateToVersion3(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE accounts RENAME COLUMN last_address TO last_address_fingerprint");
            statement.executeUpdate("UPDATE accounts SET last_address_fingerprint = NULL");
            statement.execute("PRAGMA user_version=3");
        }
    }

    private static void migrateToVersion4(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS totp_replay (
                        player_id TEXT PRIMARY KEY NOT NULL REFERENCES accounts(player_id) ON DELETE CASCADE,
                        last_step INTEGER NOT NULL
                    )
                    """);
            statement.execute("PRAGMA user_version=4");
        }
    }

    private static void migrateToVersion5(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE accounts DROP COLUMN last_authenticated_at");
            statement.executeUpdate("ALTER TABLE accounts DROP COLUMN last_address_fingerprint");
            statement.execute("PRAGMA user_version=5");
        }
    }

    private static void migrateToVersion6(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS identity_mappings (
                        source_id TEXT PRIMARY KEY NOT NULL,
                        translated_id TEXT UNIQUE NOT NULL,
                        created_at INTEGER NOT NULL
                    )
                    """);
            statement.execute("PRAGMA user_version=6");
        }
    }

    private static void migrateToVersion7(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS login_sessions (
                        token_hash TEXT PRIMARY KEY NOT NULL,
                        player_id TEXT NOT NULL REFERENCES accounts(player_id) ON DELETE CASCADE,
                        credential_fingerprint TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        expires_at INTEGER NOT NULL
                    )
                    """);
            if (!columnExists(connection, "login_sessions", "credential_fingerprint")) {
                statement.executeUpdate("ALTER TABLE login_sessions ADD COLUMN "
                        + "credential_fingerprint TEXT NOT NULL DEFAULT ''");
            }
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS login_sessions_player ON login_sessions(player_id)");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS login_sessions_expiry ON login_sessions(expires_at)");
            statement.execute("PRAGMA user_version=7");
        }
    }

    private static void migrateToVersion8(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            if (!columnExists(connection, "accounts", "session_generation")) {
                statement.executeUpdate("ALTER TABLE accounts ADD COLUMN "
                        + "session_generation INTEGER NOT NULL DEFAULT 0");
            }
            if (!columnExists(connection, "login_sessions", "session_generation")) {
                statement.executeUpdate("ALTER TABLE login_sessions ADD COLUMN "
                        + "session_generation INTEGER NOT NULL DEFAULT 0");
            }
            statement.execute("PRAGMA user_version=8");
        }
    }

    private static boolean columnExists(Connection connection, String table, String column) throws SQLException {
        try (var statement = connection.createStatement();
                var columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) return true;
            }
            return false;
        }
    }
}
