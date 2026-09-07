package dev.lunynt.opengate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import dev.lunynt.opengate.database.DatabaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenGateConfigTest {
    @TempDir
    Path directory;

    @Test
    void createsAndLoadsDefaults() {
        var config = OpenGateConfig.load(directory);

        assertEquals(Duration.ofSeconds(60), config.authenticationTimeout());
        assertEquals(3, config.maximumLoginAttempts());
        assertEquals("limbo", config.limboServer());
        assertEquals(java.util.List.of("lobby"), config.lobbyServers());
        assertEquals(DatabaseType.SQLITE, config.database().type());
        assertEquals(true, Files.exists(directory.resolve("config.properties")));
    }

    @Test
    void loadsRemoteDatabaseConfiguration() throws Exception {
        OpenGateConfig.load(directory);
        var file = directory.resolve("config.properties");
        Files.writeString(file, Files.readString(file)
                .replace("database-type=sqlite",
                        "database-type=postgresql\n"
                                + "database-url=jdbc:postgresql://database.internal/opengate\n"
                                + "database-username=opengate\ndatabase-password=secret"));

        var config = OpenGateConfig.load(directory);

        assertEquals(DatabaseType.POSTGRESQL, config.database().type());
        assertEquals("jdbc:postgresql://database.internal/opengate", config.database().jdbcUrl());
        assertEquals("opengate", config.database().username());
    }

    @Test
    void environmentOverridesFileBackedInfrastructureSecrets() throws Exception {
        var config = OpenGateConfig.load(directory, java.util.Map.of(
                "OPENGATE_DATABASE_TYPE", "postgresql",
                "OPENGATE_DATABASE_URL", "jdbc:postgresql://database.internal/cloud",
                "OPENGATE_DATABASE_USERNAME", "runtime-user",
                "OPENGATE_DATABASE_PASSWORD", "runtime-secret",
                "OPENGATE_REDIS_ENABLED", "true",
                "OPENGATE_REDIS_URI", "rediss://redis.internal:6380"));

        assertEquals(DatabaseType.POSTGRESQL, config.database().type());
        assertEquals("jdbc:postgresql://database.internal/cloud", config.database().jdbcUrl());
        assertEquals("runtime-user", config.database().username());
        assertEquals("runtime-secret", config.database().password());
        assertEquals(true, config.redis().enabled());
        assertEquals("rediss://redis.internal:6380", config.redis().uri());
        var fileContents = Files.readString(directory.resolve("config.properties"));
        assertEquals(false, fileContents.contains("runtime-secret"));
    }

    @Test
    void rejectsUnsafeAttemptLimit() throws Exception {
        OpenGateConfig.load(directory);
        var file = directory.resolve("config.properties");
        Files.writeString(file, Files.readString(file).replace("maximum-login-attempts=3", "maximum-login-attempts=0"));

        assertThrows(IllegalArgumentException.class, () -> OpenGateConfig.load(directory));
    }

    @Test
    void loadsLegacyProxyNames() throws Exception {
        Files.writeString(directory.resolve("config.properties"), """
                authentication-timeout-seconds=60
                trusted-session-hours=6
                maximum-login-attempts=3
                maximum-ip-failures=10
                ip-failure-window-minutes=10
                minimum-password-length=8
                maximum-password-length=128
                premium-lookup-enabled=true
                premium-lookup-timeout-millis=3000
                velocity-limbo-server=auth
                velocity-lobby-servers=survival,creative
                """);

        var config = OpenGateConfig.load(directory);

        assertEquals("auth", config.limboServer());
        assertEquals(java.util.List.of("survival", "creative"), config.lobbyServers());
    }

    @Test
    void rejectsInvalidBoolean() throws Exception {
        OpenGateConfig.load(directory);
        var file = directory.resolve("config.properties");
        Files.writeString(file, Files.readString(file).replace("premium-lookup-enabled=true", "premium-lookup-enabled=yes"));

        assertThrows(IllegalArgumentException.class, () -> OpenGateConfig.load(directory));
    }
}
