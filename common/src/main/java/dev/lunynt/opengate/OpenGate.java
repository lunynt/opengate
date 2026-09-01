package dev.lunynt.opengate;

import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.account.SqliteAccountRepository;
import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.crypto.Argon2idPasswordHasher;
import dev.lunynt.opengate.config.OpenGateConfig;
import dev.lunynt.opengate.config.OpenGateMessages;
import dev.lunynt.opengate.identity.MojangProfileLookup;
import dev.lunynt.opengate.identity.ProfileLookup;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;

public final class OpenGate implements AutoCloseable {
    private final SessionRegistry sessions;
    private final AccountService accounts;
    private final OpenGateConfig config;
    private final OpenGateMessages messages;
    private final ProfileLookup profiles;

    private OpenGate(
            SessionRegistry sessions,
            AccountService accounts,
            OpenGateConfig config,
            OpenGateMessages messages,
            ProfileLookup profiles) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
    }

    public static OpenGate create(Path dataDirectory) {
        var clock = Clock.systemUTC();
        var config = OpenGateConfig.load(dataDirectory);
        var messages = OpenGateMessages.load(dataDirectory);
        var repository = new SqliteAccountRepository(dataDirectory.resolve("opengate.db"));
        var cryptoExecutor = Executors.newFixedThreadPool(2, Thread.ofPlatform()
                .name("opengate-crypto-", 0)
                .factory());
        var accounts = new AccountService(
                repository,
                new Argon2idPasswordHasher(),
                cryptoExecutor,
                clock,
                config.minimumPasswordLength(),
                config.maximumPasswordLength());
        return new OpenGate(
                new SessionRegistry(clock),
                accounts,
                config,
                messages,
                new MojangProfileLookup(config.premiumLookupTimeout()));
    }

    public SessionRegistry sessions() {
        return sessions;
    }

    public AccountService accounts() {
        return accounts;
    }

    public OpenGateConfig config() {
        return config;
    }

    public OpenGateMessages messages() {
        return messages;
    }

    public ProfileLookup profiles() {
        return profiles;
    }

    @Override
    public void close() {
        accounts.close();
    }
}
