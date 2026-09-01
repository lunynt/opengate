package dev.lunynt.opengate.totp;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountRepository;
import dev.lunynt.opengate.crypto.SecretCipher;
import dev.lunynt.opengate.audit.AuditEventType;
import dev.lunynt.opengate.audit.AuditLog;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TotpEnrollmentService {
    private static final Duration ENROLLMENT_LIFETIME = Duration.ofMinutes(10);

    private final AccountRepository accounts;
    private final TotpService totp;
    private final SecretCipher secrets;
    private final Clock clock;
    private final AuditLog auditLog;
    private final ConcurrentHashMap<UUID, PendingEnrollment> pending = new ConcurrentHashMap<>();

    public TotpEnrollmentService(
            AccountRepository accounts,
            TotpService totp,
            SecretCipher secrets,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.totp = totp;
        this.secrets = secrets;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    public String begin(Account account) {
        var secret = totp.createSecret();
        pending.put(account.playerId(), new PendingEnrollment(secret, clock.instant().plus(ENROLLMENT_LIFETIME)));
        return totp.provisioningUri("OpenGate", account.username(), secret);
    }

    public boolean confirm(UUID playerId, String code) {
        var enrollment = pending.get(playerId);
        if (enrollment == null || !enrollment.expiresAt().isAfter(clock.instant())) {
            pending.remove(playerId);
            return false;
        }
        if (!totp.verify(enrollment.secret(), code)) {
            return false;
        }
        var account = accounts.findByPlayerId(playerId).orElseThrow();
        accounts.save(account.withTotpSecret(secrets.encrypt(enrollment.secret())));
        auditLog.record(AuditEventType.TOTP_ENABLED, playerId, account.username(), null, null);
        pending.remove(playerId);
        return true;
    }

    public boolean verify(Account account, String code) {
        return Optional.ofNullable(account.totpSecret())
                .map(secrets::decrypt)
                .map(secret -> totp.verify(secret, code))
                .orElse(false);
    }

    public void disable(Account account) {
        accounts.save(account.withTotpSecret(null));
        pending.remove(account.playerId());
        auditLog.record(
                AuditEventType.TOTP_DISABLED,
                account.playerId(),
                account.username(),
                null,
                null);
    }

    private record PendingEnrollment(String secret, Instant expiresAt) {}
}
