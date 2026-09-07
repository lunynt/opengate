package dev.lunynt.opengate.auth;

import dev.lunynt.opengate.database.OpenGateDataSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class CookieSessionService implements AutoCloseable {
    public static final int TOKEN_BYTES = 32;

    private final OpenGateDataSource dataSource;
    private final Clock clock;
    private final Duration lifetime;
    private final boolean enabled;
    private final java.security.SecureRandom random = new java.security.SecureRandom();
    private final ExecutorService executor = java.util.concurrent.Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("opengate-cookie-", 0).factory());

    public CookieSessionService(OpenGateDataSource dataSource, Clock clock, Duration lifetime, boolean enabled) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        this.enabled = enabled;
        if (lifetime.isZero() || lifetime.isNegative() || lifetime.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("cookie session lifetime must be between 1 millisecond and 30 days");
        }
    }

    public CompletableFuture<Optional<byte[]>> issue(UUID playerId) {
        if (!enabled) return CompletableFuture.completedFuture(Optional.empty());
        return CompletableFuture.supplyAsync(() -> Optional.of(issueBlocking(playerId)), executor);
    }

    public CompletableFuture<Boolean> verify(UUID playerId, byte[] token) {
        if (!enabled || token == null || token.length != TOKEN_BYTES) {
            return CompletableFuture.completedFuture(false);
        }
        var owned = token.clone();
        return CompletableFuture.supplyAsync(() -> verifyBlocking(playerId, owned), executor)
                .whenComplete((result, error) -> java.util.Arrays.fill(owned, (byte) 0));
    }

    public CompletableFuture<Void> revokeAll(UUID playerId) {
        return CompletableFuture.runAsync(() -> {
            try (var connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    incrementSessionGeneration(connection, playerId);
                    deleteAll(connection, playerId);
                    connection.commit();
                } catch (SQLException exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (SQLException exception) {
                throw new IllegalStateException("could not revoke cookie sessions", exception);
            }
        }, executor);
    }

    private byte[] issueBlocking(UUID playerId) {
        var token = new byte[TOKEN_BYTES];
        random.nextBytes(token);
        var now = clock.millis();
        try (var connection = dataSource.getConnection()) {
            var accountState = currentAccountState(connection, playerId);
            try (var statement = connection.prepareStatement("""
                    INSERT INTO login_sessions(
                        token_hash, player_id, credential_fingerprint, session_generation, created_at, expires_at)
                    VALUES(?, ?, ?, ?, ?, ?)
                    """)) {
            statement.setString(1, hash(token));
            statement.setString(2, playerId.toString());
            statement.setString(3, accountState.credentialFingerprint());
            statement.setLong(4, accountState.sessionGeneration());
            statement.setLong(5, now);
            statement.setLong(6, Math.addExact(now, lifetime.toMillis()));
            statement.executeUpdate();
            }
            cleanup(connection, now);
            return token;
        } catch (SQLException exception) {
            java.util.Arrays.fill(token, (byte) 0);
            throw new IllegalStateException("could not create cookie session", exception);
        }
    }

    private boolean verifyBlocking(UUID playerId, byte[] token) {
        var now = clock.millis();
        var tokenHash = hash(token);
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        """
                        SELECT s.player_id, s.expires_at, s.credential_fingerprint,
                               s.session_generation AS issued_generation,
                               a.password_hash, a.totp_secret,
                               a.session_generation AS current_generation
                        FROM login_sessions s
                        JOIN accounts a ON a.player_id = s.player_id
                        WHERE s.token_hash = ?
                        """)) {
            statement.setString(1, tokenHash);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return false;
                if (result.getLong("expires_at") <= now) {
                    delete(connection, tokenHash);
                    return false;
                }
                var accountMatches = MessageDigest.isEqual(
                        result.getString("player_id").getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        playerId.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                var credentialsMatch = MessageDigest.isEqual(
                        result.getString("credential_fingerprint").getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        credentialFingerprint(result.getString("password_hash"), result.getString("totp_secret"))
                                .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                var generationMatches = result.getLong("issued_generation") == result.getLong("current_generation");
                if (!credentialsMatch || !generationMatches) delete(connection, tokenHash);
                return accountMatches && credentialsMatch && generationMatches;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("could not verify cookie session", exception);
        }
    }

    private static void cleanup(java.sql.Connection connection, long now) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM login_sessions WHERE expires_at <= ?")) {
            statement.setLong(1, now);
            statement.executeUpdate();
        }
    }

    private static void delete(java.sql.Connection connection, String tokenHash) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM login_sessions WHERE token_hash = ?")) {
            statement.setString(1, tokenHash);
            statement.executeUpdate();
        }
    }

    private static AccountState currentAccountState(java.sql.Connection connection, UUID playerId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT password_hash, totp_secret, session_generation FROM accounts WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("cannot issue a cookie for a missing account");
                return new AccountState(credentialFingerprint(result.getString(1), result.getString(2)),
                        result.getLong(3));
            }
        }
    }

    private static void incrementSessionGeneration(java.sql.Connection connection, UUID playerId)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                "UPDATE accounts SET session_generation = session_generation + 1 WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    private static void deleteAll(java.sql.Connection connection, UUID playerId) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM login_sessions WHERE player_id = ?")) {
            statement.setString(1, playerId.toString());
            statement.executeUpdate();
        }
    }

    private static String credentialFingerprint(String passwordHash, String totpSecret) {
        var value = "password=" + (passwordHash == null ? "<null>" : passwordHash)
                + "\u0000totp=" + (totpSecret == null ? "<null>" : totpSecret);
        return hash(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String hash(byte[] token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the Java platform", exception);
        }
    }

    private record AccountState(String credentialFingerprint, long sessionGeneration) {}

    @Override
    public void close() {
        executor.close();
    }
}
