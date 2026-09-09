package dev.lunynt.opengate.account;

import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAccountRepository implements AccountRepository {
    private static final String SELECT_COLUMNS = """
            SELECT player_id, username, identity_type, password_hash, totp_secret, created_at
            FROM accounts
            """;

    private final OpenGateDataSource dataSource;
    private final DatabaseType databaseType;

    public JdbcAccountRepository(OpenGateDataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource");
        this.databaseType = dataSource.type();
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
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
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
                """;
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.playerId().toString());
            statement.setString(2, account.username());
            statement.setString(3, account.normalizedUsername());
            statement.setString(4, account.identityType().name());
            statement.setString(5, account.passwordHash());
            statement.setString(6, account.totpSecret());
            statement.setLong(7, account.createdAt().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            if ("23505".equals(exception.getSQLState()) || "23000".equals(exception.getSQLState())
                    || exception.getMessage() != null && exception.getMessage().contains("UNIQUE constraint failed")) {
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

    @Override
    public void updateIdentityType(UUID playerId, dev.lunynt.opengate.auth.IdentityType identityType) {
        updateCredential("identity_type", playerId, identityType.name());
    }

    private void updateCredential(String column, UUID playerId, String value) {
        var sql = "UPDATE accounts SET " + column + " = ? WHERE player_id = ?";
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
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
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement("DELETE FROM accounts WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("could not delete OpenGate account", exception);
        }
    }

    @Override
    public boolean claimTotpStep(UUID playerId, long step) {
        if (databaseType == DatabaseType.H2 || databaseType == DatabaseType.MYSQL
                || databaseType == DatabaseType.MARIADB) return claimConditionalTotpStep(playerId, step);
        var sql = switch (databaseType) {
            case MYSQL, MARIADB, H2 -> throw new AssertionError();
            default -> """
                INSERT INTO totp_replay(player_id, last_step) VALUES(?, ?)
                ON CONFLICT(player_id) DO UPDATE SET last_step = excluded.last_step
                WHERE excluded.last_step > totp_replay.last_step
                """;
        };
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerId.toString());
            statement.setLong(2, step);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("could not update OpenGate TOTP counter", exception);
        }
    }

    private boolean claimConditionalTotpStep(UUID playerId, long step) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var update = connection.prepareStatement(
                            "UPDATE totp_replay SET last_step = ? WHERE player_id = ? AND last_step < ?")) {
                update.setLong(1, step);
                update.setString(2, playerId.toString());
                update.setLong(3, step);
                if (update.executeUpdate() == 1) {
                    connection.commit();
                    return true;
                }
            }
            try (var insert = connection.prepareStatement(
                            "INSERT INTO totp_replay(player_id, last_step) VALUES(?, ?)")) {
                insert.setString(1, playerId.toString());
                insert.setLong(2, step);
                insert.executeUpdate();
                connection.commit();
                return true;
            } catch (SQLException duplicate) {
                connection.rollback();
                return false;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("could not update OpenGate TOTP counter", exception);
        }
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
