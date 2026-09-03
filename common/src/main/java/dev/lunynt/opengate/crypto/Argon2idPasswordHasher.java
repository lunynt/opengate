package dev.lunynt.opengate.crypto;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class Argon2idPasswordHasher implements PasswordHasher {
    private static final int VERSION = Argon2Parameters.ARGON2_VERSION_13;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private final SecureRandom random;
    private final int memoryKiB;
    private final int iterations;
    private final int parallelism;

    public Argon2idPasswordHasher() {
        this(new SecureRandom(), 65_536, 3, 1);
    }

    Argon2idPasswordHasher(SecureRandom random, int memoryKiB, int iterations, int parallelism) {
        this.random = random;
        this.memoryKiB = memoryKiB;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    @Override
    public String hash(char[] password) {
        validatePassword(password);
        var salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        var hash = derive(password, salt, memoryKiB, iterations, parallelism);
        var encoder = Base64.getEncoder().withoutPadding();
        return "$argon2id$v=19$m=" + memoryKiB + ",t=" + iterations + ",p=" + parallelism
                + "$" + encoder.encodeToString(salt) + "$" + encoder.encodeToString(hash);
    }

    @Override
    public boolean verify(char[] password, String encodedHash) {
        validatePassword(password);
        try {
            var parts = encodedHash.split("\\$");
            if (parts.length != 6 || !"argon2id".equals(parts[1]) || !"v=19".equals(parts[2])) {
                return false;
            }
            var parameters = parts[3].split(",");
            var memory = parseParameter(parameters[0], "m");
            var rounds = parseParameter(parameters[1], "t");
            var lanes = parseParameter(parameters[2], "p");
            if (memory < 8_192
                    || memory > 131_072
                    || rounds < 1
                    || rounds > 6
                    || lanes < 1
                    || lanes > 4
                    || (long) memory * rounds > 393_216L) {
                return false;
            }
            var decoder = Base64.getDecoder();
            var salt = decoder.decode(parts[4]);
            var expected = decoder.decode(parts[5]);
            if (salt.length < 16 || expected.length != HASH_BYTES) {
                return false;
            }
            var actual = derive(password, salt, memory, rounds, lanes);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public boolean needsRehash(String encodedHash) {
        try {
            var parts = encodedHash.split("\\$");
            if (parts.length != 6 || !"argon2id".equals(parts[1]) || !"v=19".equals(parts[2])) return true;
            var parameters = parts[3].split(",");
            return parseParameter(parameters[0], "m") != memoryKiB
                    || parseParameter(parameters[1], "t") != iterations
                    || parseParameter(parameters[2], "p") != parallelism;
        } catch (RuntimeException exception) {
            return true;
        }
    }

    private static int parseParameter(String value, String name) {
        var prefix = name + "=";
        if (!value.startsWith(prefix)) {
            throw new IllegalArgumentException("missing Argon2 parameter " + name);
        }
        return Integer.parseInt(value.substring(prefix.length()));
    }

    private static byte[] derive(char[] password, byte[] salt, int memory, int rounds, int lanes) {
        var parameters = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(VERSION)
                .withMemoryAsKB(memory)
                .withIterations(rounds)
                .withParallelism(lanes)
                .withSalt(salt)
                .build();
        var generator = new Argon2BytesGenerator();
        generator.init(parameters);
        var output = new byte[HASH_BYTES];
        generator.generateBytes(password, output);
        return output;
    }

    private static void validatePassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("password must not be empty");
        }
    }
}
