package dev.lunynt.opengate.totp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.account.Account;
import dev.lunynt.opengate.account.AccountRepository;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.crypto.SecretCipher;
import dev.lunynt.opengate.audit.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class TotpEnrollmentServiceTest {
    @Test
    void persistsSecretOnlyAfterValidConfirmation() {
        var account = new Account(
                UUID.randomUUID(), "Player", IdentityType.OFFLINE, "hash", null, Instant.EPOCH, null, null);
        var repository = new MemoryRepository(account);
        var clock = Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC);
        var totp = new TotpService(clock);
        var service = new TotpEnrollmentService(
                repository,
                totp,
                new SecretCipher(new SecretKeySpec(new byte[32], "AES")),
                clock,
                AuditLog.noop());

        var uri = service.begin(account);
        var secret = uri.substring(uri.indexOf("secret=") + 7, uri.indexOf("&issuer="));

        assertFalse(service.confirm(account.playerId(), "000000"));
        assertTrue(service.confirm(account.playerId(), totp.generate(secret, 1)));
        assertTrue(repository.account.totpSecret().startsWith("enc:v1:"));
        assertFalse(repository.account.totpSecret().contains(secret));
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
        public void delete(UUID playerId) {
            throw new UnsupportedOperationException();
        }
    }
}
