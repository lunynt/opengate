package dev.lunynt.opengate.admin;

import dev.lunynt.opengate.auth.IdentityType;
import java.time.Instant;
import java.util.UUID;

public record AccountSummary(
        UUID playerId, String username, IdentityType identityType, Instant createdAt, boolean totpEnabled) {}
