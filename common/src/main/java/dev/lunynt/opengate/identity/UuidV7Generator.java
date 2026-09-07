package dev.lunynt.opengate.identity;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

public final class UuidV7Generator {
    private static final long TIMESTAMP_MASK = 0x0000_FFFF_FFFF_FFFFL;

    private final Clock clock;
    private final SecureRandom random;
    private final AtomicLong lastTimestamp = new AtomicLong(-1);

    public UuidV7Generator(Clock clock, SecureRandom random) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
    }

    public UUID generate() {
        var timestamp = lastTimestamp.accumulateAndGet(clock.millis(), Math::max) & TIMESTAMP_MASK;
        var randomA = random.nextInt(1 << 12);
        var randomB = random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
        var mostSignificant = timestamp << 16 | 0x7000L | randomA;
        var leastSignificant = 0x8000_0000_0000_0000L | randomB;
        return new UUID(mostSignificant, leastSignificant);
    }
}
