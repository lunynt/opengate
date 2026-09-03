package dev.lunynt.opengate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenGateMessagesTest {
    @TempDir
    Path directory;

    @Test
    void createsStyledDefaultsWithoutManglingSmallCaps() throws Exception {
        var messages = OpenGateMessages.load(directory);

        assertTrue(messages.get("login-prompt").startsWith("&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7"));
        assertTrue(messages.get("login-success").contains("&a✓"));
        assertTrue(messages.get("incorrect-password").contains("&c✕"));
        assertEquals(messages.get("login-prompt"), OpenGateMessages.load(directory).get("login-prompt"));
        assertTrue(Files.exists(directory.resolve("messages.properties")));
    }
}
