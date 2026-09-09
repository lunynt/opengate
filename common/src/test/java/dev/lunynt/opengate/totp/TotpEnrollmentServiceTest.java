package dev.lunynt.opengate.totp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountRepository;
import dev.lunynt.opengate.account.LoginRateLimiter;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.crypto.SecretCipher;
import dev.lunynt.opengate.audit.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TotpEnrollmentServiceTest {
    @Test
    void persistsSecretOnlyAfterValidConfirmation() {
        var account = new Account(UUID.randomUUID(), "Player", IdentityType.OFFLINE, "hash", null, Instant.EPOCH);
        var repository = new MemoryRepository(account);
        var clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        var totp = new TotpService(clock);
        var cipher = new SecretCipher(new SecretKeySpec(new byte[32], "AES"));
        var service = new TotpEnrollmentService(
                repository,
                totp,
                cipher,
                clock,
                AuditLog.noop());

        var uri = service.begin(account);
        var secret = uri.substring(uri.indexOf("secret=") + 7, uri.indexOf("&issuer="));

        assertTrue(uri.contains("algorithm=SHA256"));
        assertFalse(service.confirm(account.playerId(), "000000"));
        var code = totp.generate("sha256:" + secret, 1);
        assertTrue(service.confirm(account.playerId(), code));
        assertTrue(repository.account.totpSecret().startsWith("enc:v2:"));
        assertFalse(repository.account.totpSecret().contains(secret));
        assertTrue(cipher.decrypt(repository.account.totpSecret()).startsWith("sha256:"));
        assertFalse(service.verify(repository.account, code, "127.0.0.1"));
    }

    @Test
    void limitsTotpAttemptsAcrossConnectionsAndRejectsCorruptSecrets() {
        var clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        var totp = new TotpService(clock);
        var cipher = new SecretCipher(new SecretKeySpec(new byte[32], "AES"));
        var secret = totp.createSecret();
        var account = new Account(
                UUID.randomUUID(), "Player", IdentityType.OFFLINE, "hash", cipher.encrypt(secret), Instant.EPOCH);
        var repository = new MemoryRepository(account);
        var service = new TotpEnrollmentService(
                repository,
                totp,
                cipher,
                clock,
                AuditLog.noop(),
                new LoginRateLimiter(1, Duration.ofMinutes(10), clock),
                new LoginRateLimiter(1, Duration.ofMinutes(10), clock));

        assertFalse(service.verify(account, "000000", "127.0.0.1"));
        assertFalse(service.verify(account, totp.generate(secret, 1), "127.0.0.1"));

        var corrupt = new Account(
                UUID.randomUUID(), "Broken", IdentityType.OFFLINE, "hash", "enc:v2:broken", Instant.EPOCH);
        assertFalse(service.verify(corrupt, "123456", "127.0.0.2"));
    }

    private static final class MemoryRepository implements AccountRepository {
        private Account account;

        private MemoryRepository(Account account) {
            this.account = account;
        }

        @Override
        public Optional<Account> findByPlayerId(UUID playerId) {
            return account.playerId().equals(playerId) ? Optional.of(account) : Optional.empty();
        }

        @Override
        public Optional<Account> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public void save(Account account) {
            this.account = account;
        }

        @Override
        public boolean claimTotpStep(UUID playerId, long step) {
            if (step <= lastStep) return false;
            lastStep = step;
            return true;
        }

        private long lastStep = Long.MIN_VALUE;

        @Override
        public void delete(UUID playerId) {
            throw new UnsupportedOperationException();
        }
    }
}
