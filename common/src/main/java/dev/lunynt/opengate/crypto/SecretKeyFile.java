package dev.lunynt.opengate.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public final class SecretKeyFile {
    private SecretKeyFile() {}

    public static SecretKey loadOrCreate(Path file) {
        return loadOrCreate(file, System.getenv("OPENGATE_SECRET_KEY"));
    }

    public static SecretKey loadOrCreate(Path file, String encodedOverride) {
        if (encodedOverride != null) return decode(encodedOverride, "OPENGATE_SECRET_KEY");
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
                SecureFiles.makeOwnerOnly(file);
            }
            return decode(Files.readString(file), file.toString());
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("could not load OpenGate secret key", exception);
        }
    }

    private static SecretKey decode(String encoded, String source) {
        try {
            var decoded = Base64.getDecoder().decode(encoded.trim());
            if (decoded.length != 32) {
                throw new IllegalStateException(source + " must contain exactly 32 bytes");
            }
            return new SecretKeySpec(decoded, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(source + " must be valid Base64", exception);
        }
    }
}
