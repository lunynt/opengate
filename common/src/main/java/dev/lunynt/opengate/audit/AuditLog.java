package dev.lunynt.opengate.audit;

import java.util.List;
import java.util.UUID;

public interface AuditLog {
    void record(
            AuditEventType type,
            UUID playerId,
            String username,
            String address,
            String detail);

    List<AuditRecord> recent(UUID playerId, int limit);

    static AuditLog noop() {
        return new AuditLog() {
            @Override
            public void record(
                    AuditEventType type,
                    UUID playerId,
                    String username,
                    String address,
                    String detail) {}

            @Override
            public List<AuditRecord> recent(UUID playerId, int limit) {
                return List.of();
            }
        };
    }
}
