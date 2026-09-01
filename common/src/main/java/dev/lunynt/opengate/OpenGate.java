package dev.lunynt.opengate;

import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.account.SqliteAccountRepository;
import dev.lunynt.opengate.account.LoginRateLimiter;
import dev.lunynt.opengate.audit.AddressFingerprint;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.audit.SqliteAuditLog;
import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.crypto.Argon2idPasswordHasher;
import dev.lunynt.opengate.crypto.SecretCipher;
import dev.lunynt.opengate.crypto.SecretKeyFile;
import dev.lunynt.opengate.config.OpenGateConfig;
import dev.lunynt.opengate.config.OpenGateMessages;
import dev.lunynt.opengate.identity.MojangProfileLookup;
import dev.lunynt.opengate.identity.ProfileLookup;
import dev.lunynt.opengate.identity.IdentityResolver;
import dev.lunynt.opengate.totp.TotpEnrollmentService;
import dev.lunynt.opengate.totp.TotpService;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.Executors;

public final class OpenGate implements AutoCloseable {
    private final SessionRegistry sessions;
    private final AccountService accounts;
    private final OpenGateConfig config;
    private final OpenGateMessages messages;
    private final ProfileLookup profiles;
    private final IdentityResolver identities;
    private final TotpEnrollmentService totp;
    private final AuditLog auditLog;

    private OpenGate(
            SessionRegistry sessions,
            AccountService accounts,
            OpenGateConfig config,
            OpenGateMessages messages,
            ProfileLookup profiles,
            IdentityResolver identities,
            TotpEnrollmentService totp,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.identities = identities;
        this.totp = totp;
        this.auditLog = auditLog;
    }

    public static OpenGate create(Path dataDirectory) {
        var clock = Clock.systemUTC();
        var config = OpenGateConfig.load(dataDirectory);
        var messages = OpenGateMessages.load(dataDirectory);
        var databaseFile = dataDirectory.resolve("opengate.db");
        var repository = new SqliteAccountRepository(databaseFile);
        var secretKey = SecretKeyFile.loadOrCreate(dataDirectory.resolve("secret.key"));
        var auditLog = new SqliteAuditLog(databaseFile, clock, new AddressFingerprint(secretKey));
        var cryptoExecutor = Executors.newFixedThreadPool(2, Thread.ofPlatform()
                .name("opengate-crypto-", 0)
                .factory());
        var accounts = new AccountService(
                repository,
                new Argon2idPasswordHasher(),
                cryptoExecutor,
                clock,
                config.minimumPasswordLength(),
                config.maximumPasswordLength(),
                new LoginRateLimiter(config.maximumIpFailures(), config.ipFailureWindow(), clock),
                auditLog);
        var profiles = new MojangProfileLookup(config.premiumLookupTimeout());
        return new OpenGate(
                new SessionRegistry(clock),
                accounts,
                config,
                messages,
                profiles,
                new IdentityResolver(repository, profiles, config.premiumLookupEnabled()),
                new TotpEnrollmentService(
                        repository,
                        new TotpService(clock),
                        new SecretCipher(secretKey),
                        clock,
                        auditLog),
                auditLog);
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

    public IdentityResolver identities() {
        return identities;
    }

    public TotpEnrollmentService totp() {
        return totp;
    }

    public AuditLog auditLog() {
        return auditLog;
    }

    @Override
    public void close() {
        accounts.close();
    }
}
