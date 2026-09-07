package dev.lunynt.opengate.api;

import dev.lunynt.opengate.auth.IdentityType;
import java.time.Instant;
import java.util.UUID;

public record OpenGateUser(
        UUID accountId,
        String username,
        IdentityType identityType,
        Instant createdAt,
        boolean passwordConfigured,
        boolean totpConfigured) {}
