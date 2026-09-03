package dev.lunynt.opengate.crypto;

public interface PasswordHasher {
    String hash(char[] password);

    boolean verify(char[] password, String encodedHash);

    default boolean needsRehash(String encodedHash) {
        return false;
    }
}
