package dev.lunynt.opengate.account;

import dev.lunynt.opengate.auth.IdentityType;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Account(
        UUID playerId,
        String username,
        IdentityType identityType,
        String passwordHash,
        String totpSecret,
        Instant createdAt,
        Instant lastAuthenticatedAt,
        String lastAddressFingerprint) {

    public Account {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(identityType, "identityType");
        Objects.requireNonNull(createdAt, "createdAt");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    public String normalizedUsername() {
        return username.toLowerCase(Locale.ROOT);
    }

    public Optional<String> passwordHashOptional() {
        return Optional.ofNullable(passwordHash);
    }

    public Optional<String> totpSecretOptional() {
        return Optional.ofNullable(totpSecret);
    }

    public Account authenticatedAt(Instant instant, String addressFingerprint) {
        return new Account(
                playerId,
                username,
                identityType,
                passwordHash,
                totpSecret,
                createdAt,
                Objects.requireNonNull(instant, "instant"),
                Objects.requireNonNull(addressFingerprint, "addressFingerprint"));
    }

    public Account withTotpSecret(String encryptedSecret) {
        return new Account(
                playerId,
                username,
                identityType,
                passwordHash,
                encryptedSecret,
                createdAt,
                lastAuthenticatedAt,
                lastAddressFingerprint);
    }

    public Account withPasswordHash(String newPasswordHash) {
        return new Account(
                playerId,
                username,
                identityType,
                newPasswordHash,
                totpSecret,
                createdAt,
                lastAuthenticatedAt,
                lastAddressFingerprint);
    }

    public Account withoutTrustedSession() {
        return new Account(
                playerId,
                username,
                identityType,
                passwordHash,
                totpSecret,
                createdAt,
                null,
                null);
    }
}
