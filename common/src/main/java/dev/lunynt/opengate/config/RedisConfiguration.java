package dev.lunynt.opengate.config;

import java.time.Duration;
import java.util.Properties;
import java.util.regex.Pattern;

public record RedisConfiguration(boolean enabled, String uri, String channel, Duration timeout) {
    private static final Pattern VALID_CHANNEL = Pattern.compile("[A-Za-z0-9:_.-]{1,128}");

    public RedisConfiguration {
        if (enabled && (uri == null || !(uri.startsWith("redis://") || uri.startsWith("rediss://")))) {
            throw new IllegalArgumentException("redis-uri must use redis:// or rediss://");
        }
        if (!VALID_CHANNEL.matcher(channel).matches()) {
            throw new IllegalArgumentException("invalid Redis channel");
        }
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("Redis timeout must be between 1 millisecond and 30 seconds");
        }
    }

    static RedisConfiguration from(Properties properties) {
        return new RedisConfiguration(
                OpenGateConfig.bool(properties, "redis-enabled", false),
                properties.getProperty("redis-uri", "redis://localhost:6379").trim(),
                properties.getProperty("redis-channel", "opengate:cluster").trim(),
                Duration.ofMillis(integer(properties.getProperty("redis-timeout-millis", "3000"))));
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("redis-timeout-millis must be an integer", exception);
        }
    }
}
