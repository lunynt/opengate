package dev.lunynt.opengate.admin;

import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.audit.AuditEventType;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.audit.AuditRecord;
import dev.lunynt.opengate.auth.SessionRegistry;
import java.util.List;
import java.util.Optional;
import dev.lunynt.opengate.cluster.ClusterCoordinator;

public final class AdminService {
    private final AccountService accounts;
    private final SessionRegistry sessions;
    private final AuditLog auditLog;
    private final ClusterCoordinator cluster;
    private final java.util.function.Function<java.util.UUID, java.util.concurrent.CompletionStage<Void>> cookieRevoker;

    public AdminService(AccountService accounts, SessionRegistry sessions, AuditLog auditLog) {
        this(accounts, sessions, auditLog, ClusterCoordinator.disabled(),
                ignored -> java.util.concurrent.CompletableFuture.completedFuture(null));
    }

    public AdminService(
            AccountService accounts,
            SessionRegistry sessions,
            AuditLog auditLog,
            ClusterCoordinator cluster,
            java.util.function.Function<java.util.UUID, java.util.concurrent.CompletionStage<Void>> cookieRevoker) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.auditLog = auditLog;
        this.cluster = cluster;
        this.cookieRevoker = cookieRevoker;
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
            var local = java.util.concurrent.CompletableFuture.completedFuture(null)
                    .thenRun(() -> sessions.closeByAccountId(value.playerId()));
            var cookies = java.util.concurrent.CompletableFuture.completedFuture(null)
                    .thenCompose(ignored -> cookieRevoker.apply(value.playerId()));
            var remote = java.util.concurrent.CompletableFuture.completedFuture(null)
                    .thenCompose(ignored -> cluster.revoked(value.playerId()));
            java.util.concurrent.CompletableFuture.allOf(local, cookies, remote).join();
            auditLog.record(
                    AuditEventType.ADMIN_SESSION_REVOKED,
                    value.playerId(),
                    value.username(),
                    null,
                    actorDetail(actor));
        });
        return account.map(AdminService::summary);
    }

    public Optional<AccountSummary> recover(String username, char[] newPassword, String actor) {
        var account = accounts.find(username);
        if (account.isEmpty()) return Optional.empty();
        var value = account.orElseThrow();
        if (value.identityType() != dev.lunynt.opengate.auth.IdentityType.OFFLINE) {
            throw new IllegalArgumentException("only offline accounts can use password recovery");
        }
        if (!accounts.resetPassword(value.playerId(), newPassword).join()) return Optional.empty();
        auditLog.record(
                AuditEventType.ADMIN_PASSWORD_RECOVERED,
                value.playerId(),
                value.username(),
                null,
                actorDetail(actor));
        var local = java.util.concurrent.CompletableFuture.completedFuture(null)
                .thenRun(() -> sessions.closeByAccountId(value.playerId()));
        var cookies = java.util.concurrent.CompletableFuture.completedFuture(null)
                .thenCompose(ignored -> cookieRevoker.apply(value.playerId()));
        var remote = java.util.concurrent.CompletableFuture.completedFuture(null)
                .thenCompose(ignored -> cluster.revoked(value.playerId()));
        java.util.concurrent.CompletableFuture.allOf(local, cookies, remote).join();
        return Optional.of(summary(accounts.find(value.playerId()).orElseThrow()));
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
