package dev.lunynt.opengate;

import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.account.SqliteAccountRepository;
import dev.lunynt.opengate.account.LoginRateLimiter;
import dev.lunynt.opengate.audit.AddressFingerprint;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.audit.SqliteAuditLog;
import dev.lunynt.opengate.audit.ResilientAuditLog;
import dev.lunynt.opengate.admin.AdminService;
import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.crypto.Argon2idPasswordHasher;
import dev.lunynt.opengate.crypto.SecretCipher;
import dev.lunynt.opengate.crypto.SecretKeyFile;
import dev.lunynt.opengate.crypto.SecretKeyDerivation;
import dev.lunynt.opengate.crypto.SecureFiles;
import dev.lunynt.opengate.config.OpenGateConfig;
import dev.lunynt.opengate.config.OpenGateMessages;
import dev.lunynt.opengate.identity.MojangProfileLookup;
import dev.lunynt.opengate.identity.ProfileLookup;
import dev.lunynt.opengate.identity.IdentityResolver;
import dev.lunynt.opengate.totp.TotpEnrollmentService;
import dev.lunynt.opengate.totp.TotpService;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class OpenGate implements AutoCloseable {
    private final SessionRegistry sessions;
    private final AccountService accounts;
    private final OpenGateConfig config;
    private final OpenGateMessages messages;
    private final ProfileLookup profiles;
    private final IdentityResolver identities;
    private final TotpEnrollmentService totp;
    private final AuditLog auditLog;
    private final AdminService adminService;

    private OpenGate(
            SessionRegistry sessions,
            AccountService accounts,
            OpenGateConfig config,
            OpenGateMessages messages,
            ProfileLookup profiles,
            IdentityResolver identities,
            TotpEnrollmentService totp,
            AuditLog auditLog,
            AdminService adminService) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.identities = identities;
        this.totp = totp;
        this.auditLog = auditLog;
        this.adminService = adminService;
    }

    public static OpenGate create(Path dataDirectory) {
        SecureFiles.createPrivateDirectory(dataDirectory);
        var clock = Clock.systemUTC();
        var config = OpenGateConfig.load(dataDirectory);
        var messages = OpenGateMessages.load(dataDirectory);
        var databaseFile = dataDirectory.resolve("opengate.db");
        var repository = new SqliteAccountRepository(databaseFile);
        SecureFiles.makeOwnerOnly(databaseFile);
        var secretKey = SecretKeyFile.loadOrCreate(dataDirectory.resolve("secret.key"));
        var addressFingerprint = new AddressFingerprint(
                SecretKeyDerivation.derive(secretKey, "address-fingerprint", "HmacSHA256"));
        var auditLog = new ResilientAuditLog(new SqliteAuditLog(databaseFile, clock, addressFingerprint));
        var cryptoExecutor = new ThreadPoolExecutor(
                2,
                2,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(32),
                Thread.ofPlatform().daemon().name("opengate-worker-", 0).factory(),
                new ThreadPoolExecutor.AbortPolicy());
        var accounts = new AccountService(
                repository,
                new Argon2idPasswordHasher(),
                cryptoExecutor,
                clock,
                config.minimumPasswordLength(),
                config.maximumPasswordLength(),
                new LoginRateLimiter(config.maximumIpFailures(), config.ipFailureWindow(), clock),
                new LoginRateLimiter(config.maximumAccountFailures(), config.ipFailureWindow(), clock),
                new LoginRateLimiter(
                        config.maximumRegistrationsPerIp(), config.registrationWindow(), clock),
                auditLog);
        var profiles = new MojangProfileLookup(config.premiumLookupTimeout());
        var sessions = new SessionRegistry(clock);
        return new OpenGate(
                sessions,
                accounts,
                config,
                messages,
                profiles,
                new IdentityResolver(repository, profiles, config.premiumLookupEnabled()),
                new TotpEnrollmentService(
                        repository,
                        new TotpService(clock),
                        new SecretCipher(
                                SecretKeyDerivation.derive(secretKey, "totp-encryption", "AES"), secretKey),
                        clock,
                        auditLog),
                auditLog,
                new AdminService(accounts, sessions, auditLog));
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

    public AdminService admin() {
        return adminService;
    }

    @Override
    public void close() {
        accounts.close();
    }
}
