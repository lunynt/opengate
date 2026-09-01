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
}
