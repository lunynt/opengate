package dev.lunynt.opengate.crypto;

import java.util.Objects;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

public final class MigratingPasswordHasher implements PasswordHasher {
    private final PasswordHasher current;

    public MigratingPasswordHasher(PasswordHasher current) {
        this.current = Objects.requireNonNull(current, "current");
    }

    @Override
    public String hash(char[] password) {
        return current.hash(password);
    }

    @Override
    public boolean verify(char[] password, String encodedHash) {
        if (encodedHash == null) return false;
        if (encodedHash.startsWith("$argon2id$")) return current.verify(password, encodedHash);
        if (encodedHash.startsWith("$2a$") || encodedHash.startsWith("$2b$") || encodedHash.startsWith("$2y$")) {
            try {
                return OpenBSDBCrypt.checkPassword(encodedHash, password);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        return encodedHash == null || !encodedHash.startsWith("$argon2id$") || current.needsRehash(encodedHash);
    }
}
