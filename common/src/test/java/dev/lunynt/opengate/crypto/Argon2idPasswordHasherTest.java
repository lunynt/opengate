package dev.lunynt.opengate.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class Argon2idPasswordHasherTest {
    private final PasswordHasher hasher = new Argon2idPasswordHasher();

    @Test
    void hashesAndVerifiesPassword() {
        var encoded = hasher.hash("correct horse battery staple".toCharArray());

        assertTrue(encoded.startsWith("$argon2id$v=19$m=65536,t=3,p=1$"));
        assertTrue(hasher.verify("correct horse battery staple".toCharArray(), encoded));
        assertFalse(hasher.verify("wrong password".toCharArray(), encoded));
    }

    @Test
    void rejectsMalformedOrDangerouslyExpensiveHashes() {
        assertFalse(hasher.verify("password".toCharArray(), "not-a-hash"));
        assertFalse(hasher.verify(
                "password".toCharArray(),
                "$argon2id$v=19$m=2147483647,t=3,p=1$c2FsdHNhbHRzYWx0c2FsdA$AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
    }
}
