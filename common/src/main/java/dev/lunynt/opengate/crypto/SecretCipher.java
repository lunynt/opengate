package dev.lunynt.opengate.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecretCipher {
    private static final String PREFIX = "enc:v1:";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random;

    public SecretCipher(SecretKey key) {
        this(key, new SecureRandom());
    }

    SecretCipher(SecretKey key, SecureRandom random) {
        this.key = key;
        this.random = random;
    }

    public String encrypt(String plaintext) {
        try {
            var nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var encoder = Base64.getUrlEncoder().withoutPadding();
            return PREFIX + encoder.encodeToString(nonce) + ":" + encoder.encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("could not encrypt secret", exception);
        }
    }

    public String decrypt(String encoded) {
        if (!encoded.startsWith(PREFIX)) {
            throw new IllegalArgumentException("unsupported encrypted secret format");
        }
        try {
            var parts = encoded.substring(PREFIX.length()).split(":", 2);
            var decoder = Base64.getUrlDecoder();
            var nonce = decoder.decode(parts[0]);
            var ciphertext = decoder.decode(parts[1]);
            if (nonce.length != NONCE_BYTES) {
                throw new IllegalArgumentException("invalid encrypted secret nonce");
            }
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("could not decrypt secret", exception);
        }
    }
}
