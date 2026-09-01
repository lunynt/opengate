package dev.lunynt.opengate.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditRecord(
        long id,
        Instant occurredAt,
        AuditEventType type,
        UUID playerId,
        String username,
        String addressFingerprint,
        String detail) {}
