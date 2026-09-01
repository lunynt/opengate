package dev.lunynt.opengate.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;

public final class LoginRateLimiter {
    private final int maximumFailures;
    private final Duration window;
    private final Clock clock;
    private final HashMap<String, ArrayDeque<Instant>> failures = new HashMap<>();

    public LoginRateLimiter(int maximumFailures, Duration window, Clock clock) {
        if (maximumFailures < 1 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("invalid login rate limit");
        }
        this.maximumFailures = maximumFailures;
        this.window = window;
        this.clock = clock;
    }

    public synchronized boolean isBlocked(String address) {
        var attempts = failures.get(address);
        if (attempts == null) return false;
        removeExpired(attempts);
        if (attempts.isEmpty()) failures.remove(address);
        return attempts.size() >= maximumFailures;
    }

    public synchronized void recordFailure(String address) {
        var attempts = failures.computeIfAbsent(address, ignored -> new ArrayDeque<>());
        removeExpired(attempts);
        attempts.addLast(clock.instant());
    }

    public synchronized void clear(String address) {
        failures.remove(address);
    }

    private void removeExpired(ArrayDeque<Instant> attempts) {
        var cutoff = clock.instant().minus(window);
        while (!attempts.isEmpty() && !attempts.getFirst().isAfter(cutoff)) {
            attempts.removeFirst();
        }
    }
}
