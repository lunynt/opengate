package dev.lunynt.opengate.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class SecretKeyFile {
    private SecretKeyFile() {}

    public static SecretKey loadOrCreate(Path file) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                var bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                Files.writeString(
                        file,
                        Base64.getEncoder().encodeToString(bytes),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                try {
                    Files.setPosixFilePermissions(file, Set.of(
                            java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                            java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
                } catch (UnsupportedOperationException ignored) {
                    // The host filesystem does not expose POSIX permissions.
                }
            }
            var decoded = Base64.getDecoder().decode(Files.readString(file).trim());
            if (decoded.length != 32) {
                throw new IllegalStateException("OpenGate secret key must contain 32 bytes");
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("could not load OpenGate secret key", exception);
        }
    }
}
