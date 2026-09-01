package dev.lunynt.opengate.account;

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

    public AccountService(
            AccountRepository accounts,
            PasswordHasher passwords,
            ExecutorService cryptoExecutor,
            Clock clock,
            int minimumPasswordLength,
            int maximumPasswordLength) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.cryptoExecutor = Objects.requireNonNull(cryptoExecutor, "cryptoExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.minimumPasswordLength = minimumPasswordLength;
        this.maximumPasswordLength = maximumPasswordLength;
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
                                address);
                        accounts.save(account);
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
                        var account = accounts.findByPlayerId(playerId);
                        if (account.isEmpty() || account.orElseThrow().passwordHash() == null) {
                            return AuthenticationResult.ACCOUNT_NOT_FOUND;
                        }
                        if (!passwords.verify(ownedPassword, account.orElseThrow().passwordHash())) {
                            return AuthenticationResult.WRONG_PASSWORD;
                        }
                        accounts.save(account.orElseThrow().authenticatedAt(clock.instant(), address));
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

    public boolean hasTrustedSession(Account account, String address, Duration lifetime) {
        if (account.lastAuthenticatedAt() == null || account.lastAddress() == null) {
            return false;
        }
        return account.lastAddress().equals(address)
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
