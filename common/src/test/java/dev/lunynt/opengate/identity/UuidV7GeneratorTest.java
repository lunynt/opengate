package dev.lunynt.opengate.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UuidV7GeneratorTest {
    @Test
    void producesRfc9562UuidWithCurrentUnixTimestamp() {
        var instant = Instant.parse("2026-09-04T12:34:56.789Z");
        var generator = new UuidV7Generator(Clock.fixed(instant, ZoneOffset.UTC), new SecureRandom());

        var uuid = generator.generate();

        assertEquals(7, uuid.version());
        assertEquals(2, uuid.variant());
        assertEquals(instant.toEpochMilli(), uuid.getMostSignificantBits() >>> 16);
    }
}
