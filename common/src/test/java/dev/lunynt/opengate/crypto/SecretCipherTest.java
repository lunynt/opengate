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

    @Test
    void readsLegacyCiphertextWithTheMasterKey() {
        var master = new SecretKeySpec(new byte[32], "AES");
        var current = new SecretKeySpec(new byte[] {
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
            1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1
        }, "AES");
        var legacy = new SecretCipher(master).encrypt("secret").replace("enc:v2:", "enc:v1:");
        var cipher = new SecretCipher(current, master);

        assertEquals("secret", cipher.decrypt(legacy));
    }
}
