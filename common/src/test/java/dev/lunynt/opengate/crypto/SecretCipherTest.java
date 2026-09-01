package dev.lunynt.opengate.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class SecretCipherTest {
    @Test
    void encryptsWithAuthenticatedRandomNonce() {
        var cipher = new SecretCipher(new SecretKeySpec(new byte[32], "AES"));

        var first = cipher.encrypt("secret");
        var second = cipher.encrypt("secret");

        assertNotEquals(first, second);
        assertEquals("secret", cipher.decrypt(first));
    }
}
