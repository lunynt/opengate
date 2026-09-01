package dev.lunynt.opengate.account;

import dev.lunynt.opengate.auth.IdentityType;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class SqliteAccountRepository implements AccountRepository {
    private static final String SELECT_COLUMNS = """
            SELECT player_id, username, identity_type, password_hash, totp_secret,
                   created_at, last_authenticated_at, last_address
            FROM accounts
            """;

    private final String jdbcUrl;

    public SqliteAccountRepository(Path databaseFile) {
        jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        initialize();
    }

    private void initialize() {
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA foreign_keys=ON");
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
        } catch (SQLException exception) {
            throw new IllegalStateException("could not initialize OpenGate database", exception);
        }
    }

    @Override
    public Optional<Account> findByPlayerId(UUID playerId) {
        return query(SELECT_COLUMNS + " WHERE player_id = ?", playerId.toString());
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return query(SELECT_COLUMNS + " WHERE normalized_username = ?", normalize(username));
    }

    private Optional<Account> query(String sql, String value) {
        try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(map(results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("could not query OpenGate account", exception);
        }
    }

    @Override
    public void save(Account account) {
        var sql = """
                INSERT INTO accounts (
                    player_id, username, normalized_username, identity_type, password_hash,
                    totp_secret, created_at, last_authenticated_at, last_address
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    username = excluded.username,
                    normalized_username = excluded.normalized_username,
                    identity_type = excluded.identity_type,
                    password_hash = excluded.password_hash,
                    totp_secret = excluded.totp_secret,
                    last_authenticated_at = excluded.last_authenticated_at,
                    last_address = excluded.last_address
                """;
        try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.playerId().toString());
            statement.setString(2, account.username());
            statement.setString(3, account.normalizedUsername());
            statement.setString(4, account.identityType().name());
            statement.setString(5, account.passwordHash());
            statement.setString(6, account.totpSecret());
            statement.setLong(7, account.createdAt().toEpochMilli());
            setInstant(statement, 8, account.lastAuthenticatedAt());
            statement.setString(9, account.lastAddress());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("UNIQUE constraint failed")) {
                throw new AccountAlreadyExistsException("username is already registered", exception);
            }
            throw new IllegalStateException("could not save OpenGate account", exception);
        }
    }

    @Override
    public void delete(UUID playerId) {
        try (var connection = connection();
                var statement = connection.prepareStatement("DELETE FROM accounts WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("could not delete OpenGate account", exception);
        }
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private static Account map(ResultSet results) throws SQLException {
        return new Account(
                UUID.fromString(results.getString("player_id")),
                results.getString("username"),
                IdentityType.valueOf(results.getString("identity_type")),
                results.getString("password_hash"),
                results.getString("totp_secret"),
                Instant.ofEpochMilli(results.getLong("created_at")),
                nullableInstant(results, "last_authenticated_at"),
                results.getString("last_address"));
    }

    private static Instant nullableInstant(ResultSet results, String column) throws SQLException {
        var value = results.getLong(column);
        return results.wasNull() ? null : Instant.ofEpochMilli(value);
    }

    private static void setInstant(java.sql.PreparedStatement statement, int index, Instant instant)
            throws SQLException {
        if (instant == null) {
            statement.setNull(index, java.sql.Types.BIGINT);
        } else {
            statement.setLong(index, instant.toEpochMilli());
        }
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
