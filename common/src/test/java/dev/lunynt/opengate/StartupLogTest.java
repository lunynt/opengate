package dev.lunynt.opengate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.lunynt.opengate.config.OpenGateConfig;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StartupLogTest {
    @TempDir
    Path directory;

    @Test
    void describesProxyWithoutExposingConnectionDetails() {
        OpenGateConfig.load(directory);
        var file = directory.resolve("config.yml");
        try {
            java.nio.file.Files.writeString(file,
                    java.nio.file.Files.readString(file).replace("colors: true", "colors: false"));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        var lines = StartupLog.lines("Velocity", "0.1.0", OpenGateConfig.load(directory), true, true);

        assertEquals("┌─ ᴏᴘᴇɴɢᴀᴛᴇ 0.1.0", lines.getFirst());
        assertTrue(lines.contains("│ Platform   Velocity"));
        assertTrue(lines.contains("│ Database   sqlite"));
        assertTrue(lines.contains("│ Routing    limbo -> lobby"));
        assertTrue(lines.contains("│ Floodgate  enabled"));
        assertEquals("└─ Ready", lines.getLast());
    }

    @Test
    void colorsStatusWithoutLeakingIntoFollowingLines() {
        var lines = StartupLog.lines("Paper", "0.1.0", OpenGateConfig.load(directory), false, false);

        assertTrue(lines.getFirst().startsWith("\u001B[96m\u001B[1m"));
        assertTrue(lines.stream().allMatch(line -> line.endsWith("\u001B[0m")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("\u001B[92menabled")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("\u001B[93mdisabled")));
    }
}
