package dev.lunynt.opengate;

import dev.lunynt.opengate.account.AccountService;
import dev.lunynt.opengate.api.DefaultOpenGateApi;
import dev.lunynt.opengate.api.OpenGateApi;
import dev.lunynt.opengate.account.JdbcAccountRepository;
import dev.lunynt.opengate.account.LoginRateLimiter;
import dev.lunynt.opengate.audit.AddressFingerprint;
import dev.lunynt.opengate.audit.AuditLog;
import dev.lunynt.opengate.audit.JdbcAuditLog;
import dev.lunynt.opengate.audit.ResilientAuditLog;
import dev.lunynt.opengate.admin.AdminService;
import dev.lunynt.opengate.auth.SessionRegistry;
import dev.lunynt.opengate.auth.CookieSessionService;
import dev.lunynt.opengate.crypto.Argon2idPasswordHasher;
import dev.lunynt.opengate.crypto.MigratingPasswordHasher;
import dev.lunynt.opengate.crypto.SecretCipher;
import dev.lunynt.opengate.crypto.SecretKeyFile;
import dev.lunynt.opengate.crypto.SecretKeyDerivation;
import dev.lunynt.opengate.crypto.SecureFiles;
import dev.lunynt.opengate.config.OpenGateConfig;
import dev.lunynt.opengate.config.OpenGateMessages;
import dev.lunynt.opengate.cluster.ClusterCoordinator;
import dev.lunynt.opengate.cluster.RedisClusterCoordinator;
import dev.lunynt.opengate.identity.MojangProfileLookup;
import dev.lunynt.opengate.identity.ProfileLookup;
import dev.lunynt.opengate.identity.IdentityResolver;
import dev.lunynt.opengate.identity.IdentityIdTranslator;
import dev.lunynt.opengate.database.DatabaseSchema;
import dev.lunynt.opengate.database.DatabaseType;
import dev.lunynt.opengate.database.OpenGateDataSource;
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
    private final OpenGateDataSource dataSource;
    private final IdentityIdTranslator identityIds;
    private final DefaultOpenGateApi api;
    private final CookieSessionService cookieSessions;
    private final ClusterCoordinator cluster;

    private OpenGate(
            SessionRegistry sessions,
            AccountService accounts,
            OpenGateConfig config,
            OpenGateMessages messages,
            ProfileLookup profiles,
            IdentityResolver identities,
            TotpEnrollmentService totp,
            AuditLog auditLog,
            AdminService adminService,
            OpenGateDataSource dataSource,
            IdentityIdTranslator identityIds,
            DefaultOpenGateApi api,
            CookieSessionService cookieSessions,
            ClusterCoordinator cluster) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.config = config;
        this.messages = messages;
        this.profiles = profiles;
        this.identities = identities;
        this.totp = totp;
        this.auditLog = auditLog;
        this.adminService = adminService;
        this.dataSource = dataSource;
        this.identityIds = identityIds;
        this.api = api;
        this.cookieSessions = cookieSessions;
        this.cluster = cluster;
    }

    public static OpenGate create(Path dataDirectory) {
        SecureFiles.createPrivateDirectory(dataDirectory);
        var clock = Clock.systemUTC();
        var config = OpenGateConfig.load(dataDirectory);
        var messages = OpenGateMessages.load(dataDirectory);
        var startupResources = new java.util.ArrayDeque<AutoCloseable>();
        try {
            var dataSource = new OpenGateDataSource(config.database());
            startupResources.push(dataSource);
            DatabaseSchema.migrate(dataSource);
            var repository = new JdbcAccountRepository(dataSource);
            if (config.database().type() == DatabaseType.SQLITE) {
                SecureFiles.makeOwnerOnly(dataDirectory.resolve("opengate.db"));
            }
            var secretKey = SecretKeyFile.loadOrCreate(dataDirectory.resolve("secret.key"));
            var addressFingerprint = new AddressFingerprint(
                    SecretKeyDerivation.derive(secretKey, "address-fingerprint", "HmacSHA256"));
            var auditLog = new ResilientAuditLog(new JdbcAuditLog(dataSource, clock, addressFingerprint));
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
                    new MigratingPasswordHasher(new Argon2idPasswordHasher()),
                    cryptoExecutor,
                    clock,
                    config.minimumPasswordLength(),
                    config.maximumPasswordLength(),
                    new LoginRateLimiter(config.maximumIpFailures(), config.ipFailureWindow(), clock),
                    new LoginRateLimiter(config.maximumAccountFailures(), config.ipFailureWindow(), clock),
                    new LoginRateLimiter(
                            config.maximumRegistrationsPerIp(), config.registrationWindow(), clock),
                    auditLog);
            startupResources.push(accounts);
            var profiles = new MojangProfileLookup(config.premiumLookupTimeout());
            var sessions = new SessionRegistry(clock);
            var cluster = config.redis().enabled()
                    ? new RedisClusterCoordinator(config.redis(), sessions)
                    : ClusterCoordinator.disabled();
            startupResources.push(cluster);
            sessions.onReleased(session -> session.identity().ifPresent(identity ->
                    cluster.authenticated(identity.playerId()).whenComplete((ignored, error) -> {
                        if (error != null) sessions.invalidate(session.connectionId());
                    })));
            var cookieSessions = new CookieSessionService(
                    dataSource, clock, config.cookieSessionLifetime(), config.cookieSessionsEnabled());
            startupResources.push(cookieSessions);
            var result = new OpenGate(
                    sessions,
                    accounts,
                    config,
                    messages,
                    profiles,
                    new IdentityResolver(
                            repository,
                            profiles,
                            config.premiumLookupEnabled(),
                            config.premiumAutoDetect(),
                            config.offlineWhitelist()::allows),
                    new TotpEnrollmentService(
                            repository,
                            new TotpService(clock),
                            new SecretCipher(
                                    SecretKeyDerivation.derive(secretKey, "totp-encryption", "AES"), secretKey),
                            clock,
                            auditLog),
                    auditLog,
                    new AdminService(
                            accounts,
                            sessions,
                            auditLog,
                            cluster,
                            accountId -> cookieSessions.revokeAll(accountId)),
                    dataSource,
                    new IdentityIdTranslator(dataSource, clock, config.translateUuid4ToUuid7()),
                    new DefaultOpenGateApi(accounts, sessions, cookieSessions, cluster),
                    cookieSessions,
                    cluster);
            startupResources.clear();
            return result;
        } catch (RuntimeException | Error failure) {
            while (!startupResources.isEmpty()) {
                try {
                    startupResources.pop().close();
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
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

    public IdentityIdTranslator identityIds() {
        return identityIds;
    }

    public OpenGateApi api() {
        return api;
    }

    public CookieSessionService cookieSessions() {
        return cookieSessions;
    }

    @Override
    public void close() {
        cluster.close();
        cookieSessions.close();
        api.close();
        accounts.close();
        dataSource.close();
    }
}
