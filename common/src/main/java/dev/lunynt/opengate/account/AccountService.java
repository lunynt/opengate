package dev.lunynt.opengate.account;

import dev.lunynt.opengate.audit.AuditEventType;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.audit.AddressFingerprint;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.crypto.PasswordHasher;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class AccountService implements AutoCloseable {
    private final AccountRepository accounts;
    private final PasswordHasher passwords;
    private final ExecutorService cryptoExecutor;
    private final Clock clock;
    private final int minimumPasswordLength;
    private final int maximumPasswordLength;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditLog auditLog;
    private final AddressFingerprint addressFingerprint;

    public AccountService(
            AccountRepository accounts,
            PasswordHasher passwords,
            ExecutorService cryptoExecutor,
            Clock clock,
            int minimumPasswordLength,
            int maximumPasswordLength,
            LoginRateLimiter loginRateLimiter,
            AuditLog auditLog,
            AddressFingerprint addressFingerprint) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.cryptoExecutor = Objects.requireNonNull(cryptoExecutor, "cryptoExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.minimumPasswordLength = minimumPasswordLength;
        this.maximumPasswordLength = maximumPasswordLength;
        this.loginRateLimiter = Objects.requireNonNull(loginRateLimiter, "loginRateLimiter");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.addressFingerprint = Objects.requireNonNull(addressFingerprint, "addressFingerprint");
    }

    public CompletableFuture<Account> register(
            UUID playerId, String username, IdentityType identityType, char[] password, String address) {
        validatePassword(password);
        var ownedPassword = Arrays.copyOf(password, password.length);
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        if (accounts.findByPlayerId(playerId).isPresent()) {
                            throw new AccountAlreadyExistsException("player is already registered", null);
                        }
                        var now = clock.instant();
                        var account = new Account(
                                playerId,
                                username,
                                identityType,
                                passwords.hash(ownedPassword),
                                null,
                                now,
                                now,
                                addressFingerprint.create(address));
                        accounts.save(account);
                        auditLog.record(
                                AuditEventType.REGISTRATION,
                                account.playerId(),
                                account.username(),
                                address,
                                account.identityType().name());
                        return account;
                    } finally {
                        Arrays.fill(ownedPassword, '\0');
                    }
                },
                cryptoExecutor);
    }

    public CompletableFuture<AuthenticationResult> authenticate(
            UUID playerId, char[] password, String address) {
        var ownedPassword = Arrays.copyOf(password, password.length);
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        if (loginRateLimiter.isBlocked(address)) {
                            auditLog.record(AuditEventType.LOGIN_RATE_LIMITED, playerId, null, address, null);
                            return AuthenticationResult.RATE_LIMITED;
                        }
                        var account = accounts.findByPlayerId(playerId);
                        if (account.isEmpty() || account.orElseThrow().passwordHash() == null) {
                            return AuthenticationResult.ACCOUNT_NOT_FOUND;
                        }
                        if (!passwords.verify(ownedPassword, account.orElseThrow().passwordHash())) {
                            loginRateLimiter.recordFailure(address);
                            auditLog.record(
                                    AuditEventType.LOGIN_FAILURE,
                                    playerId,
                                    account.orElseThrow().username(),
                                    address,
                                    null);
                            return AuthenticationResult.WRONG_PASSWORD;
                        }
                        loginRateLimiter.clear(address);
                        accounts.save(account.orElseThrow()
                                .authenticatedAt(clock.instant(), addressFingerprint.create(address)));
                        auditLog.record(
                                AuditEventType.LOGIN_SUCCESS,
                                playerId,
                                account.orElseThrow().username(),
                                address,
                                null);
                        return AuthenticationResult.SUCCESS;
                    } finally {
                        Arrays.fill(ownedPassword, '\0');
                    }
                },
                cryptoExecutor);
    }

    public Optional<Account> find(UUID playerId) {
        return accounts.findByPlayerId(playerId);
    }

    public Optional<Account> find(String username) {
        return accounts.findByUsername(username);
    }

    public CompletableFuture<AccountActionResult> changePassword(
            UUID playerId, char[] currentPassword, char[] newPassword, String address) {
        validatePassword(newPassword);
        return authenticatedAction(playerId, currentPassword, address, account -> {
            accounts.save(account.withPasswordHash(passwords.hash(newPassword))
                    .authenticatedAt(clock.instant(), addressFingerprint.create(address)));
        }, newPassword);
    }

    public CompletableFuture<AccountActionResult> delete(
            UUID playerId, char[] currentPassword, String address) {
        return authenticatedAction(playerId, currentPassword, address, account -> accounts.delete(playerId));
    }

    public void revokeTrustedSession(UUID playerId) {
        accounts.findByPlayerId(playerId).ifPresent(account -> {
            accounts.save(account.withoutTrustedSession());
            auditLog.record(
                    AuditEventType.SESSION_REVOKED,
                    playerId,
                    account.username(),
                    null,
                    null);
        });
    }

    private CompletableFuture<AccountActionResult> authenticatedAction(
            UUID playerId,
            char[] currentPassword,
            String address,
            java.util.function.Consumer<Account> action,
            char[]... additionalSecrets) {
        var ownedPassword = Arrays.copyOf(currentPassword, currentPassword.length);
        var ownedAdditional = java.util.Arrays.stream(additionalSecrets)
                .map(value -> Arrays.copyOf(value, value.length))
                .toArray(char[][]::new);
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (loginRateLimiter.isBlocked(address)) return AccountActionResult.RATE_LIMITED;
                var account = accounts.findByPlayerId(playerId);
                if (account.isEmpty() || account.orElseThrow().passwordHash() == null) {
                    return AccountActionResult.ACCOUNT_NOT_FOUND;
                }
                if (!passwords.verify(ownedPassword, account.orElseThrow().passwordHash())) {
                    loginRateLimiter.recordFailure(address);
                    return AccountActionResult.WRONG_PASSWORD;
                }
                loginRateLimiter.clear(address);
                if (ownedAdditional.length == 0) {
                    action.accept(account.orElseThrow());
                } else {
                    var replacement = ownedAdditional[0];
                    accounts.save(account.orElseThrow()
                            .withPasswordHash(passwords.hash(replacement))
                            .authenticatedAt(clock.instant(), addressFingerprint.create(address)));
                }
                auditLog.record(
                        ownedAdditional.length == 0
                                ? AuditEventType.ACCOUNT_DELETED
                                : AuditEventType.PASSWORD_CHANGED,
                        playerId,
                        account.orElseThrow().username(),
                        address,
                        null);
                return AccountActionResult.SUCCESS;
            } finally {
                Arrays.fill(ownedPassword, '\0');
                for (var secret : ownedAdditional) Arrays.fill(secret, '\0');
            }
        }, cryptoExecutor);
    }

    public boolean hasTrustedSession(Account account, String address, Duration lifetime) {
        if (account.lastAuthenticatedAt() == null || account.lastAddressFingerprint() == null) {
            return false;
        }
        return account.lastAddressFingerprint().equals(addressFingerprint.create(address))
                && account.lastAuthenticatedAt().plus(lifetime).isAfter(clock.instant());
    }

    private void validatePassword(char[] password) {
        if (password == null
                || password.length < minimumPasswordLength
                || password.length > maximumPasswordLength) {
            throw new IllegalArgumentException(
                    "password length must be between "
                            + minimumPasswordLength
                            + " and "
                            + maximumPasswordLength);
        }
    }

    @Override
    public void close() {
        cryptoExecutor.close();
        accounts.close();
    }
}
