package dev.lunynt.opengate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
        assertEquals(true, Files.exists(directory.resolve("config.properties")));
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
