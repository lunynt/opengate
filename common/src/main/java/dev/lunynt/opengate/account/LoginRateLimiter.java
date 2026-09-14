package dev.lunynt.opengate.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.UUID;
import dev.lunynt.opengate.database.OpenGateDataSource;

public final class LoginRateLimiter {
    private static final int MAXIMUM_TRACKED_KEYS = 16_384;

    private final int maximumFailures;
    private final Duration window;
    private final Clock clock;
    private final OpenGateDataSource dataSource;
    private final String namespace;
    private final HashMap<String, ArrayDeque<Instant>> failures = new HashMap<>();

    public LoginRateLimiter(int maximumFailures, Duration window, Clock clock) {
        this(maximumFailures, window, clock, null, "memory");
    }

    public LoginRateLimiter(
            int maximumFailures, Duration window, Clock clock,
            OpenGateDataSource dataSource, String namespace) {
        if (maximumFailures < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("invalid login rate limit");
        }
        this.maximumFailures = maximumFailures;
        this.window = window;
        this.clock = clock;
        this.dataSource = dataSource;
        this.namespace = namespace;
        if (namespace == null || !namespace.matches("[a-z0-9-]{1,64}")) {
            throw new IllegalArgumentException("invalid rate limiter namespace");
        }
    }

    public synchronized boolean isBlocked(String address) {
        if (dataSource != null) return persistentCount(key(address)) >= maximumFailures;
        var attempts = failures.get(address);
        if (attempts == null) return false;
        removeExpired(attempts);
        if (attempts.isEmpty()) failures.remove(address);
        return attempts.size() >= maximumFailures;
    }

    public synchronized void recordFailure(String address) {
        if (dataSource != null) {
            recordPersistent(key(address));
            return;
        }
        if (!failures.containsKey(address) && failures.size() >= MAXIMUM_TRACKED_KEYS) {
            purgeExpired();
            if (failures.size() >= MAXIMUM_TRACKED_KEYS) {
                failures.remove(failures.keySet().iterator().next());
            }
        }
        var attempts = failures.computeIfAbsent(address, ignored -> new ArrayDeque<>());
        removeExpired(attempts);
        attempts.addLast(clock.instant());
    }

    public synchronized void clear(String address) {
        if (dataSource != null) {
            clearPersistent(key(address));
            return;
        }
        failures.remove(address);
    }

    private String key(String value) {
        return namespace + ":" + value;
    }

    private int persistentCount(String key) {
        var cutoff = clock.instant().minus(window).toEpochMilli();
        try (var connection = dataSource.getConnection();
                var cleanup = connection.prepareStatement(
                        "DELETE FROM rate_limit_failures WHERE limiter_key = ? AND failed_at <= ?")) {
            cleanup.setString(1, key);
            cleanup.setLong(2, cutoff);
            cleanup.executeUpdate();
            try (var query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM rate_limit_failures WHERE limiter_key = ? AND failed_at > ?")) {
                query.setString(1, key);
                query.setLong(2, cutoff);
                try (var result = query.executeQuery()) {
                    result.next();
                    return result.getInt(1);
                }
            }
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("could not read shared rate limit", exception);
        }
    }

    private void recordPersistent(String key) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "INSERT INTO rate_limit_failures(event_id, limiter_key, failed_at) VALUES(?, ?, ?)")) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, key);
            statement.setLong(3, clock.millis());
            statement.executeUpdate();
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("could not update shared rate limit", exception);
        }
    }

    private void clearPersistent(String key) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "DELETE FROM rate_limit_failures WHERE limiter_key = ?")) {
            statement.setString(1, key);
            statement.executeUpdate();
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("could not clear shared rate limit", exception);
        }
    }

    private void removeExpired(ArrayDeque<Instant> attempts) {
        var cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && !attempts.getFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }

    private void purgeExpired() {
        var iterator = failures.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            removeExpired(entry.getValue());
            if (entry.getValue().isEmpty()) iterator.remove();
        }
    }
}
