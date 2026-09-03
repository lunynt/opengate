package dev.lunynt.opengate.account;

import dev.lunynt.opengate.audit.AuditEventType;
import dev.lunynt.opengate.audit.AuditLog;
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
import java.util.concurrent.RejectedExecutionException;
import java.util.HashMap;
import java.util.function.Supplier;

public final class AccountService implements AutoCloseable {
    private final AccountRepository accounts;
    private final PasswordHasher passwords;
    private final ExecutorService cryptoExecutor;
    private final Clock clock;
    private final int minimumPasswordLength;
    private final int maximumPasswordLength;
    private final LoginRateLimiter loginRateLimiter;
    private final LoginRateLimiter accountRateLimiter;
    private final LoginRateLimiter registrationRateLimiter;
    private final AuditLog auditLog;
    private final HashMap<String, Integer> inFlightByAddress = new HashMap<>();

    public AccountService(
            AccountRepository accounts,
            PasswordHasher passwords,
            ExecutorService cryptoExecutor,
            Clock clock,
            int minimumPasswordLength,
            int maximumPasswordLength,
            LoginRateLimiter loginRateLimiter,
            LoginRateLimiter accountRateLimiter,
            LoginRateLimiter registrationRateLimiter,
            AuditLog auditLog) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.cryptoExecutor = Objects.requireNonNull(cryptoExecutor, "cryptoExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.minimumPasswordLength = minimumPasswordLength;
        this.maximumPasswordLength = maximumPasswordLength;
        this.loginRateLimiter = Objects.requireNonNull(loginRateLimiter, "loginRateLimiter");
        this.accountRateLimiter = Objects.requireNonNull(accountRateLimiter, "accountRateLimiter");
        this.registrationRateLimiter = Objects.requireNonNull(registrationRateLimiter, "registrationRateLimiter");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
    }

    public CompletableFuture<Account> register(
            UUID playerId, String username, IdentityType identityType, char[] password, String address) {
        validatePassword(password);
        var addressKey = NetworkAddress.rateLimitKey(address);
        if (registrationRateLimiter.isBlocked(addressKey)) {
            return CompletableFuture.failedFuture(new IllegalStateException("registration rate limit exceeded"));
        }
        registrationRateLimiter.recordFailure(addressKey);
        var ownedPassword = Arrays.copyOf(password, password.length);
        return submit(addressKey,
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
                                now);
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
                });
    }

    public CompletableFuture<AuthenticationResult> authenticate(
            UUID playerId, char[] password, String address) {
        var ownedPassword = Arrays.copyOf(password, password.length);
        var addressKey = NetworkAddress.rateLimitKey(address);
        return submitAuthentication(addressKey,
                () -> {
                    try {
                        var accountKey = playerId.toString();
                        if (loginRateLimiter.isBlocked(addressKey) || accountRateLimiter.isBlocked(accountKey)) {
                            auditLog.record(AuditEventType.LOGIN_RATE_LIMITED, playerId, null, address, null);
                            return AuthenticationResult.RATE_LIMITED;
                        }
                        var account = accounts.findByPlayerId(playerId);
                        if (account.isEmpty() || account.orElseThrow().passwordHash() == null) {
                            return AuthenticationResult.ACCOUNT_NOT_FOUND;
                        }
                        if (!passwords.verify(ownedPassword, account.orElseThrow().passwordHash())) {
                            loginRateLimiter.recordFailure(addressKey);
                            accountRateLimiter.recordFailure(accountKey);
                            auditLog.record(
                                    AuditEventType.LOGIN_FAILURE,
                                    playerId,
                                    account.orElseThrow().username(),
                                    address,
                                    null);
                            return AuthenticationResult.WRONG_PASSWORD;
                        }
                        accountRateLimiter.clear(accountKey);
                        if (passwords.needsRehash(account.orElseThrow().passwordHash())) {
                            accounts.updatePassword(playerId, passwords.hash(ownedPassword));
                        }
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
                });
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
        return authenticatedAction(
                playerId,
                currentPassword,
                address,
                (account, secrets) -> accounts.updatePassword(account.playerId(), passwords.hash(secrets[0])),
                AuditEventType.PASSWORD_CHANGED,
                newPassword);
    }

    public CompletableFuture<AccountActionResult> delete(
            UUID playerId, char[] currentPassword, String address) {
        return authenticatedAction(
                playerId,
                currentPassword,
                address,
                (account, secrets) -> accounts.delete(playerId),
                AuditEventType.ACCOUNT_DELETED);
    }

    private CompletableFuture<AccountActionResult> authenticatedAction(
            UUID playerId,
            char[] currentPassword,
            String address,
            java.util.function.BiConsumer<Account, char[][]> action,
            AuditEventType eventType,
            char[]... additionalSecrets) {
        var ownedPassword = Arrays.copyOf(currentPassword, currentPassword.length);
        var ownedAdditional = java.util.Arrays.stream(additionalSecrets)
                .map(value -> Arrays.copyOf(value, value.length))
                .toArray(char[][]::new);
        var addressKey = NetworkAddress.rateLimitKey(address);
        return submitAction(addressKey, () -> {
            try {
                var accountKey = playerId.toString();
                if (loginRateLimiter.isBlocked(addressKey) || accountRateLimiter.isBlocked(accountKey)) {
                    return AccountActionResult.RATE_LIMITED;
                }
                var account = accounts.findByPlayerId(playerId);
                if (account.isEmpty() || account.orElseThrow().passwordHash() == null) {
                    return AccountActionResult.ACCOUNT_NOT_FOUND;
                }
                if (!passwords.verify(ownedPassword, account.orElseThrow().passwordHash())) {
                    loginRateLimiter.recordFailure(addressKey);
                    accountRateLimiter.recordFailure(accountKey);
                    return AccountActionResult.WRONG_PASSWORD;
                }
                accountRateLimiter.clear(accountKey);
                action.accept(account.orElseThrow(), ownedAdditional);
                auditLog.record(
                        eventType,
                        playerId,
                        account.orElseThrow().username(),
                        address,
                        null);
                return AccountActionResult.SUCCESS;
            } finally {
                Arrays.fill(ownedPassword, '\0');
                for (var secret : ownedAdditional) Arrays.fill(secret, '\0');
            }
        });
    }

    private <T> CompletableFuture<T> submit(String address, Supplier<T> work) {
        if (!acquire(address)) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("address work limit exceeded"));
        }
        try {
            return CompletableFuture.supplyAsync(work, cryptoExecutor)
                    .whenComplete((result, error) -> release(address));
        } catch (RejectedExecutionException exception) {
            release(address);
            return CompletableFuture.failedFuture(exception);
        }
    }

    private CompletableFuture<AuthenticationResult> submitAuthentication(
            String address, Supplier<AuthenticationResult> work) {
        if (!acquire(address)) return CompletableFuture.completedFuture(AuthenticationResult.SERVICE_BUSY);
        try {
            return CompletableFuture.supplyAsync(work, cryptoExecutor)
                    .whenComplete((result, error) -> release(address));
        } catch (RejectedExecutionException exception) {
            release(address);
            return CompletableFuture.completedFuture(AuthenticationResult.SERVICE_BUSY);
        }
    }

    private CompletableFuture<AccountActionResult> submitAction(String address, Supplier<AccountActionResult> work) {
        if (!acquire(address)) return CompletableFuture.completedFuture(AccountActionResult.SERVICE_BUSY);
        try {
            return CompletableFuture.supplyAsync(work, cryptoExecutor)
                    .whenComplete((result, error) -> release(address));
        } catch (RejectedExecutionException exception) {
            release(address);
            return CompletableFuture.completedFuture(AccountActionResult.SERVICE_BUSY);
        }
    }

    private synchronized boolean acquire(String address) {
        var count = inFlightByAddress.getOrDefault(address, 0);
        if (count >= 2) return false;
        inFlightByAddress.put(address, count + 1);
        return true;
    }

    private synchronized void release(String address) {
        var count = inFlightByAddress.get(address);
        if (count == null || count <= 1) {
            inFlightByAddress.remove(address);
        } else {
            inFlightByAddress.put(address, count - 1);
        }
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
