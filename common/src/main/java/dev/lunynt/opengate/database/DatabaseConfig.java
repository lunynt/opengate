package dev.lunynt.opengate.database;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public record DatabaseConfig(
        DatabaseType type,
        String jdbcUrl,
        String username,
        String password,
        int maximumPoolSize,
        Duration connectionTimeout) {

    public DatabaseConfig {
        if (jdbcUrl == null || jdbcUrl.isBlank()) throw new IllegalArgumentException("database URL must not be blank");
        if (maximumPoolSize < 1 || maximumPoolSize > 64) {
            throw new IllegalArgumentException("database pool size must be between 1 and 64");
        }
        if (connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("database connection timeout must be positive");
        }
        username = username == null ? "" : username;
        password = password == null ? "" : password;
    }

    public static DatabaseConfig from(Properties properties, Path dataDirectory) {
        var type = DatabaseType.parse(properties.getProperty("database-type", "sqlite"));
        var defaultUrl = switch (type) {
            case SQLITE -> "jdbc:sqlite:" + dataDirectory.resolve("opengate.db").toAbsolutePath();
            case H2 -> "jdbc:h2:file:" + dataDirectory.resolve("opengate").toAbsolutePath();
            case POSTGRESQL -> "jdbc:postgresql://localhost:5432/opengate";
            case MYSQL -> "jdbc:mysql://localhost:3306/opengate";
            case MARIADB -> "jdbc:mariadb://localhost:3306/opengate";
        };
        return new DatabaseConfig(
                type,
                properties.getProperty("database-url", defaultUrl).trim(),
                properties.getProperty("database-username", "").trim(),
                properties.getProperty("database-password", ""),
                integer(properties, "database-pool-size", type == DatabaseType.SQLITE ? 1 : 10),
                Duration.ofMillis(integer(properties, "database-connection-timeout-millis", 5_000)));
    }

    private static int integer(Properties properties, String key, int fallback) {
        try {
            return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
