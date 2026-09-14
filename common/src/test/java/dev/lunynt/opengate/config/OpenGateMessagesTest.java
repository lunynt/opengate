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
        assertTrue(messages.get("premium-login").contains("ᴘʀᴇᴍɪᴜᴍ"));
        assertTrue(messages.get("geyser-login").contains("ɢᴇʏꜱᴇʀ"));
        assertTrue(messages.get("lobby-missing").contains("ʟᴏʙʙʏ"));
        assertTrue(messages.get("login-title").contains("ʟᴏɢɢᴇᴅ ɪɴ"));
        assertTrue(messages.get("login-subtitle-premium").contains("ᴘʀᴇᴍɪᴜᴍ"));
        assertTrue(messages.get("prompt-subtitle-login").contains("/login"));
        assertTrue(messages.get("prompt-subtitle-register").contains("/register"));
        assertTrue(messages.get("incorrect-password").contains("&c✕"));
        assertEquals(messages.get("login-prompt"), OpenGateMessages.load(directory).get("login-prompt"));
        assertTrue(Files.exists(directory.resolve("messages.yml")));
    }

    @Test
    void migratesLegacyMessageOverrides() throws Exception {
        Files.writeString(directory.resolve("messages.properties"), "login-success=Welcome back\n");

        var messages = OpenGateMessages.load(directory);

        assertEquals("Welcome back", messages.get("login-success"));
        assertTrue(Files.exists(directory.resolve("messages.yml")));
    }
}
