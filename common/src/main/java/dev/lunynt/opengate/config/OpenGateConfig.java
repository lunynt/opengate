package dev.lunynt.opengate.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

public record OpenGateConfig(
        Duration authenticationTimeout,
        Duration trustedSessionLifetime,
        int maximumLoginAttempts,
        int maximumIpFailures,
        Duration ipFailureWindow,
        int minimumPasswordLength,
        int maximumPasswordLength,
        boolean premiumLookupEnabled,
        Duration premiumLookupTimeout,
        String limboServer,
        List<String> lobbyServers) {

    private static final String DEFAULTS = """
            # opengate
            authentication-timeout-seconds=60
            trusted-session-hours=6
            maximum-login-attempts=3
            maximum-ip-failures=10
            ip-failure-window-minutes=10
            minimum-password-length=8
            maximum-password-length=128
            premium-lookup-enabled=true
            premium-lookup-timeout-millis=3000
            proxy-auth-server=limbo
            proxy-lobby-servers=lobby
            """;

    public OpenGateConfig {
        if (authenticationTimeout.isNegative() || authenticationTimeout.isZero()) {
            throw new IllegalArgumentException("authentication timeout must be positive");
        }
        if (trustedSessionLifetime.isNegative()) {
            throw new IllegalArgumentException("trusted session lifetime must not be negative");
        }
        if (maximumLoginAttempts < 1 || maximumLoginAttempts > 20) {
            throw new IllegalArgumentException("maximum login attempts must be between 1 and 20");
        }
        if (maximumIpFailures < maximumLoginAttempts || maximumIpFailures > 1_000) {
            throw new IllegalArgumentException("maximum IP failures must be at least the session limit and at most 1000");
        }
        if (ipFailureWindow.isNegative() || ipFailureWindow.isZero()) {
            throw new IllegalArgumentException("IP failure window must be positive");
        }
        if (minimumPasswordLength < 8 || maximumPasswordLength < minimumPasswordLength) {
            throw new IllegalArgumentException("invalid password length range");
        }
        if (premiumLookupTimeout.isNegative() || premiumLookupTimeout.isZero()) {
            throw new IllegalArgumentException("premium lookup timeout must be positive");
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
        var file = dataDirectory.resolve("config.properties");
        createDefault(file, DEFAULTS);
        var properties = loadProperties(file);
        return new OpenGateConfig(
                Duration.ofSeconds(integer(properties, "authentication-timeout-seconds")),
                Duration.ofHours(integer(properties, "trusted-session-hours")),
                integer(properties, "maximum-login-attempts"),
                integer(properties, "maximum-ip-failures"),
                Duration.ofMinutes(integer(properties, "ip-failure-window-minutes")),
                integer(properties, "minimum-password-length"),
                integer(properties, "maximum-password-length"),
                bool(properties, "premium-lookup-enabled"),
                Duration.ofMillis(integer(properties, "premium-lookup-timeout-millis")),
                compatible(properties, "proxy-auth-server", "velocity-limbo-server", "limbo"),
                compatible(properties, "proxy-lobby-servers", "velocity-lobby-servers", "lobby").lines()
                        .flatMap(line -> java.util.Arrays.stream(line.split(",")))
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .toList());
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

    private static boolean bool(Properties properties, String key) {
        return switch (required(properties, key).toLowerCase(java.util.Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException(key + " must be true or false");
        };
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
