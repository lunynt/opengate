package dev.lunynt.opengate.audit;

import java.util.List;
import java.util.UUID;

public final class ResilientAuditLog implements AuditLog {
    private static final System.Logger LOGGER = System.getLogger(ResilientAuditLog.class.getName());

    private final AuditLog delegate;

    public ResilientAuditLog(AuditLog delegate) {
        this.delegate = delegate;
    }

    @Override
    public void record(AuditEventType type, UUID playerId, String username, String address, String detail) {
        try {
            delegate.record(type, playerId, username, address, detail);
        } catch (RuntimeException exception) {
            LOGGER.log(System.Logger.Level.WARNING, "OpenGate could not record audit event " + type, exception);
        }
    }

    @Override
    public List<AuditRecord> recent(UUID playerId, int limit) {
        return delegate.recent(playerId, limit);
    }
}
