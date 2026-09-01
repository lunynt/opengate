package dev.lunynt.opengate.totp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TotpServiceTest {
    @Test
    void verifiesRfc6238Sha1VectorAsSixDigits() {
        var clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        var service = new TotpService(clock);

        assertTrue(service.verify("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "287082"));
        assertFalse(service.verify("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", "000000"));
    }
}
