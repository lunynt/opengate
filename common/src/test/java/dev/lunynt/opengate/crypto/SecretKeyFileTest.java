package dev.lunynt.opengate.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretKeyFileTest {
    @TempDir
    Path directory;

    @Test
    void injectedKeyAvoidsWritingASecretFile() {
        var bytes = new byte[32];
        java.util.Arrays.fill(bytes, (byte) 0x5a);

        var key = SecretKeyFile.loadOrCreate(
                directory.resolve("secret.key"), Base64.getEncoder().encodeToString(bytes));

        assertArrayEquals(bytes, key.getEncoded());
        assertFalse(Files.exists(directory.resolve("secret.key")));
    }

    @Test
    void rejectsMalformedInjectedKey() {
        assertThrows(IllegalStateException.class,
                () -> SecretKeyFile.loadOrCreate(directory.resolve("secret.key"), "not-base64"));
    }
}
