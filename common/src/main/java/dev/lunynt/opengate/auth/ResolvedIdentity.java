package dev.lunynt.opengate.auth;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ResolvedIdentity(
        String username,
        UUID playerId,
        IdentityType type,
        boolean registered,
        boolean passwordRequired,
        boolean totpRequired) {

    public ResolvedIdentity {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(type, "type");
        if (username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        if (!registered && (passwordRequired || totpRequired)) {
            throw new IllegalArgumentException("an unregistered identity cannot have credentials or a session");
        }
    }

    public Optional<AuthenticationMethod> automaticAuthentication() {
        if (type == IdentityType.FLOODGATE) {
            return Optional.of(AuthenticationMethod.PREMIUM);
        }
        if (type == IdentityType.PREMIUM && !passwordRequired && !totpRequired) {
            return Optional.of(AuthenticationMethod.PREMIUM);
        }
        return Optional.empty();
    }
}
