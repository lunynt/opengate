package dev.lunynt.opengate.account;

import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.database.SqliteSchema;
import dev.lunynt.opengate.database.SqliteConnections;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class SqliteAccountRepository implements AccountRepository {
    private static final String SELECT_COLUMNS = """
            SELECT player_id, username, identity_type, password_hash, totp_secret, created_at
            FROM accounts
            """;

    private final String jdbcUrl;

    public SqliteAccountRepository(Path databaseFile) {
        jdbcUrl = "jdbc:sqlite:" + databaseFile.toAbsolutePath();
        SqliteSchema.migrate(databaseFile);
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
                    totp_secret, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    username = excluded.username,
                    normalized_username = excluded.normalized_username,
                    identity_type = excluded.identity_type,
                    password_hash = excluded.password_hash,
                    totp_secret = excluded.totp_secret
                """;
        try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.playerId().toString());
            statement.setString(2, account.username());
            statement.setString(3, account.normalizedUsername());
            statement.setString(4, account.identityType().name());
            statement.setString(5, account.passwordHash());
            statement.setString(6, account.totpSecret());
            statement.setLong(7, account.createdAt().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("UNIQUE constraint failed")) {
                throw new AccountAlreadyExistsException("username is already registered", exception);
            }
            throw new IllegalStateException("could not save OpenGate account", exception);
        }
    }

    @Override
    public void updatePassword(UUID playerId, String passwordHash) {
        updateCredential("password_hash", playerId, passwordHash);
    }

    @Override
    public void updateTotpSecret(UUID playerId, String encryptedSecret) {
        updateCredential("totp_secret", playerId, encryptedSecret);
    }

    private void updateCredential(String column, UUID playerId, String value) {
        var sql = "UPDATE accounts SET " + column + " = ? WHERE player_id = ?";
        try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            statement.setString(2, playerId.toString());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("OpenGate account disappeared during update");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("could not update OpenGate credential", exception);
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

    @Override
    public boolean claimTotpStep(UUID playerId, long step) {
        var sql = """
                INSERT INTO totp_replay(player_id, last_step) VALUES(?, ?)
                ON CONFLICT(player_id) DO UPDATE SET last_step = excluded.last_step
                WHERE excluded.last_step > totp_replay.last_step
                """;
        try (var connection = connection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, step);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("could not update OpenGate TOTP counter", exception);
        }
    }

    private Connection connection() throws SQLException {
        return SqliteConnections.open(jdbcUrl);
    }

    private static Account map(ResultSet results) throws SQLException {
        return new Account(
                UUID.fromString(results.getString("player_id")),
                results.getString("username"),
                IdentityType.valueOf(results.getString("identity_type")),
                results.getString("password_hash"),
                results.getString("totp_secret"),
                Instant.ofEpochMilli(results.getLong("created_at")));
    }

    private static String normalize(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
