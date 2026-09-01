package dev.lunynt.opengate.totp;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountRepository;
import dev.lunynt.opengate.crypto.SecretCipher;
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
    private final ConcurrentHashMap<UUID, PendingEnrollment> pending = new ConcurrentHashMap<>();

    public TotpEnrollmentService(
            AccountRepository accounts, TotpService totp, SecretCipher secrets, Clock clock) {
        this.accounts = accounts;
        this.totp = totp;
        this.secrets = secrets;
        this.clock = clock;
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
    }

    private record PendingEnrollment(String secret, Instant expiresAt) {}
}
