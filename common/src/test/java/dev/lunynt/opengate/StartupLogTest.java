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
        var lines = StartupLog.lines("Velocity", "0.1.0", OpenGateConfig.load(directory), true, true);

        assertEquals("┌─ ᴏᴘᴇɴɢᴀᴛᴇ 0.1.0", lines.getFirst());
        assertTrue(lines.contains("│ Platform   Velocity"));
        assertTrue(lines.contains("│ Database   sqlite"));
        assertTrue(lines.contains("│ Routing    limbo -> lobby"));
        assertTrue(lines.contains("│ Floodgate  enabled"));
        assertEquals("└─ Ready", lines.getLast());
    }
}
