package dev.lunynt.opengate.admin;

import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.audit.AuditEventType;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.audit.AuditRecord;
import dev.lunynt.opengate.auth.SessionRegistry;
import java.util.List;
import java.util.Optional;

public final class AdminService {
    private final AccountService accounts;
    private final SessionRegistry sessions;
    private final AuditLog auditLog;

    public AdminService(AccountService accounts, SessionRegistry sessions, AuditLog auditLog) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.auditLog = auditLog;
    }

    public Optional<AccountSummary> lookup(String username, String actor) {
        var account = accounts.find(username);
        auditLog.record(
                AuditEventType.ADMIN_ACCOUNT_LOOKUP,
                account.map(value -> value.playerId()).orElse(null),
                username,
                null,
                actorDetail(actor));
        return account.map(AdminService::summary);
    }

    public List<AuditRecord> audit(String username, int limit, String actor) {
        var account = accounts.find(username).orElseThrow(() ->
                new IllegalArgumentException("account not found: " + username));
        auditLog.record(
                AuditEventType.ADMIN_AUDIT_VIEWED,
                account.playerId(),
                account.username(),
                null,
                actorDetail(actor));
        return auditLog.recent(account.playerId(), limit);
    }

    public Optional<AccountSummary> revoke(String username, String actor) {
        var account = accounts.find(username);
        account.ifPresent(value -> {
            sessions.close(value.playerId());
            auditLog.record(
                    AuditEventType.ADMIN_SESSION_REVOKED,
                    value.playerId(),
                    value.username(),
                    null,
                    actorDetail(actor));
        });
        return account.map(AdminService::summary);
    }

    private static AccountSummary summary(dev.lunynt.opengate.account.Account account) {
        return new AccountSummary(
                account.playerId(),
                account.username(),
                account.identityType(),
                account.createdAt(),
                account.totpSecret() != null);
    }

    private static String actorDetail(String actor) {
        return "actor=" + actor.substring(0, Math.min(actor.length(), 200));
    }
}
