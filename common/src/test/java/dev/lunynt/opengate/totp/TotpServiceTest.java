package dev.lunynt.opengate.totp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void generatesNewCredentialsWithRfc6238Sha256() {
        var clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        var service = new TotpService(clock);
        var encoded = org.bouncycastle.util.encoders.Base32.toBase32String(
                "12345678901234567890123456789012".getBytes(java.nio.charset.StandardCharsets.US_ASCII))
                .replace("=", "");
        var credential = "sha256:" + encoded;

        assertEquals("119246", service.generate(credential, 1));
        assertTrue(service.verify(credential, "119246"));
        assertTrue(service.provisioningUri("OpenGate", "Player", credential).contains("algorithm=SHA256"));
        assertFalse(service.provisioningUri("OpenGate", "Player", credential).contains("sha256%3A"));
    }
}
