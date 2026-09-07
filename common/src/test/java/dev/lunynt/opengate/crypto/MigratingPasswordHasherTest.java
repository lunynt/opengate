package dev.lunynt.opengate.crypto;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.junit.jupiter.api.Test;

class MigratingPasswordHasherTest {
    @Test
    void acceptsBcryptOnlyForMigrationAndWritesArgon2id() {
        var password = "migration-password".toCharArray();
        var salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        var bcrypt = OpenBSDBCrypt.generate(password, salt, 10);
        var hasher = new MigratingPasswordHasher(new Argon2idPasswordHasher());

        assertTrue(hasher.verify(password, bcrypt));
        assertTrue(hasher.needsRehash(bcrypt));
        assertTrue(hasher.hash(password).startsWith("$argon2id$"));
    }

    @Test
    void rejectsUnsaltedLegacyDigestsAndPlaintext() {
        var hasher = new MigratingPasswordHasher(new Argon2idPasswordHasher());
        assertFalse(hasher.verify("password".toCharArray(), "5f4dcc3b5aa765d61d8327deb882cf99"));
        assertFalse(hasher.verify("password".toCharArray(), "password"));
    }
}
