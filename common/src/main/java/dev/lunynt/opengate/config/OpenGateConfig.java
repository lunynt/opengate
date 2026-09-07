package dev.lunynt.opengate.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import dev.lunynt.opengate.database.DatabaseConfig;

public record OpenGateConfig(
        Duration authenticationTimeout,
        int maximumLoginAttempts,
        int maximumIpFailures,
        int maximumAccountFailures,
        Duration ipFailureWindow,
        int maximumRegistrationsPerIp,
        Duration registrationWindow,
        int minimumPasswordLength,
        int maximumPasswordLength,
        boolean premiumLookupEnabled,
        Duration premiumLookupTimeout,
        String limboServer,
        List<String> lobbyServers,
        DatabaseConfig database,
        CommandConfiguration commands,
        boolean translateUuid4ToUuid7,
        ProtectedAccountConfiguration protectedAccounts,
        AuthenticationRequirementConfiguration authenticationRequirements,
        boolean cookieSessionsEnabled,
        Duration cookieSessionLifetime,
        RedisConfiguration redis,
        AjQueueConfiguration ajQueue,
        boolean minecraftDialogsEnabled,
        OfflineWhitelistConfiguration offlineWhitelist) {

    private static final String DEFAULTS = """
            authentication-timeout-seconds=60
            maximum-login-attempts=3
            maximum-ip-failures=10
            maximum-account-failures=10
            ip-failure-window-minutes=10
            maximum-registrations-per-ip=5
            registration-window-minutes=60
            minimum-password-length=8
            maximum-password-length=128
            premium-lookup-enabled=true
            premium-lookup-timeout-millis=3000
            proxy-auth-server=limbo
            proxy-lobby-servers=lobby
            database-type=sqlite
            database-pool-size=10
            database-connection-timeout-millis=5000
            command.login.aliases=l
            command.register.aliases=reg
            command.totp.aliases=
            command.2fa.aliases=
            command.account.aliases=
            command.opengate.aliases=
            translate-uuid4-to-uuid7=false
            protected-account-permissions=
            require-login-permissions=
            require-2fa-permissions=
            cookie-sessions-enabled=true
            cookie-session-hours=12
            redis-enabled=false
            redis-uri=redis://localhost:6379
            redis-channel=opengate:cluster
            redis-timeout-millis=3000
            ajqueue-enabled=false
            ajqueue-target=lobby
            minecraft-dialogs-enabled=true
            offline-whitelist-enabled=false
            offline-whitelist=
            """;

    public OpenGateConfig {
        if (authenticationTimeout.isNegative() || authenticationTimeout.isZero()) {
            throw new IllegalArgumentException("authentication timeout must be positive");
        }
        if (maximumLoginAttempts < 1 || maximumLoginAttempts > 20) {
            throw new IllegalArgumentException("maximum login attempts must be between 1 and 20");
        }
        if (maximumIpFailures < maximumLoginAttempts || maximumIpFailures > 1_000) {
            throw new IllegalArgumentException("maximum IP failures must be at least the session limit and at most 1000");
        }
        if (maximumAccountFailures < maximumLoginAttempts || maximumAccountFailures > 1_000) {
            throw new IllegalArgumentException("maximum account failures must be at least the session limit and at most 1000");
        }
        if (ipFailureWindow.isNegative() || ipFailureWindow.isZero()) {
            throw new IllegalArgumentException("IP failure window must be positive");
        }
        if (maximumRegistrationsPerIp < 1 || maximumRegistrationsPerIp > 100) {
            throw new IllegalArgumentException("maximum registrations per IP must be between 1 and 100");
        }
        if (registrationWindow.isNegative() || registrationWindow.isZero()) {
            throw new IllegalArgumentException("registration window must be positive");
        }
        if (minimumPasswordLength < 8 || maximumPasswordLength < minimumPasswordLength) {
            throw new IllegalArgumentException("invalid password length range");
        }
        if (premiumLookupTimeout.isNegative() || premiumLookupTimeout.isZero()) {
            throw new IllegalArgumentException("premium lookup timeout must be positive");
        }
        if (cookieSessionLifetime.isNegative() || cookieSessionLifetime.isZero()
                || cookieSessionLifetime.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException("cookie session lifetime must be between 1 hour and 30 days");
        }
        if (limboServer.isBlank()) {
            throw new IllegalArgumentException("proxy authentication server must not be blank");
        }
        lobbyServers = List.copyOf(lobbyServers);
        if (lobbyServers.isEmpty()) {
            throw new IllegalArgumentException("at least one proxy lobby server is required");
        }
    }

    public static OpenGateConfig load(Path dataDirectory) {
        return load(dataDirectory, System.getenv());
    }

    static OpenGateConfig load(Path dataDirectory, Map<String, String> environment) {
        var file = dataDirectory.resolve("config.properties");
        createDefault(file, DEFAULTS);
        var properties = loadProperties(file);
        applyEnvironmentOverrides(properties, environment);
        return new OpenGateConfig(
                Duration.ofSeconds(integer(properties, "authentication-timeout-seconds")),
                integer(properties, "maximum-login-attempts"),
                integer(properties, "maximum-ip-failures"),
                integer(properties, "maximum-account-failures", 10),
                Duration.ofMinutes(integer(properties, "ip-failure-window-minutes")),
                integer(properties, "maximum-registrations-per-ip", 5),
                Duration.ofMinutes(integer(properties, "registration-window-minutes", 60)),
                integer(properties, "minimum-password-length"),
                integer(properties, "maximum-password-length"),
                bool(properties, "premium-lookup-enabled"),
                Duration.ofMillis(integer(properties, "premium-lookup-timeout-millis")),
                compatible(properties, "proxy-auth-server", "velocity-limbo-server", "limbo"),
                compatible(properties, "proxy-lobby-servers", "velocity-lobby-servers", "lobby").lines()
                        .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList(),
                DatabaseConfig.from(properties, dataDirectory),
                CommandConfiguration.from(properties),
                bool(properties, "translate-uuid4-to-uuid7", false),
                ProtectedAccountConfiguration.from(properties),
                AuthenticationRequirementConfiguration.from(properties),
                bool(properties, "cookie-sessions-enabled", true),
                Duration.ofHours(integer(properties, "cookie-session-hours", 12)),
                RedisConfiguration.from(properties),
                AjQueueConfiguration.from(properties),
                bool(properties, "minecraft-dialogs-enabled", true),
                OfflineWhitelistConfiguration.from(properties));
    }

    private static void applyEnvironmentOverrides(Properties properties, Map<String, String> environment) {
        var keys = Map.ofEntries(
                Map.entry("OPENGATE_DATABASE_TYPE", "database-type"),
                Map.entry("OPENGATE_DATABASE_URL", "database-url"),
                Map.entry("OPENGATE_DATABASE_USERNAME", "database-username"),
                Map.entry("OPENGATE_DATABASE_PASSWORD", "database-password"),
                Map.entry("OPENGATE_DATABASE_POOL_SIZE", "database-pool-size"),
                Map.entry("OPENGATE_DATABASE_CONNECTION_TIMEOUT_MILLIS", "database-connection-timeout-millis"),
                Map.entry("OPENGATE_REDIS_ENABLED", "redis-enabled"),
                Map.entry("OPENGATE_REDIS_URI", "redis-uri"),
                Map.entry("OPENGATE_REDIS_CHANNEL", "redis-channel"),
                Map.entry("OPENGATE_REDIS_TIMEOUT_MILLIS", "redis-timeout-millis"));
        keys.forEach((environmentKey, propertyKey) -> {
            if (environment.containsKey(environmentKey)) {
                properties.setProperty(propertyKey, environment.get(environmentKey));
            }
        });
    }

    static Properties loadProperties(Path file) {
        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("could not read " + file, exception);
        }
    }

    static void createDefault(Path file, String contents) {
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                Files.writeString(file, contents, StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("could not create " + file, exception);
        }
    }

    private static int integer(Properties properties, String key) {
        try {
            return Integer.parseInt(required(properties, key));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }

    private static int integer(Properties properties, String key, int fallback) {
        var value = properties.getProperty(key);
        return value == null ? fallback : integer(properties, key);
    }

    private static boolean bool(Properties properties, String key) {
        return switch (required(properties, key).toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
    }

    static boolean bool(Properties properties, String key, boolean fallback) {
        return properties.containsKey(key) ? bool(properties, key) : fallback;
    }

    private static String required(Properties properties, String key) {
        var value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing configuration value: " + key);
        }
        return value.trim();
    }

    private static String compatible(Properties properties, String key, String legacyKey, String defaultValue) {
        return properties.getProperty(key, properties.getProperty(legacyKey, defaultValue)).trim();
    }
}
