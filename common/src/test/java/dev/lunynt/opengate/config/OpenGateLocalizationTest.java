package dev.lunynt.opengate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenGateLocalizationTest {
    @TempDir
    Path directory;

    @Test
    void selectsExactThenLanguageThenDefaultBundle() throws Exception {
        Files.writeString(directory.resolve("messages_lt.yml"), "login-success: Prisijungta\n");
        Files.writeString(directory.resolve("messages_pt_BR.yml"), "login-success: Conectado BR\n");
        var messages = OpenGateMessages.load(directory);

        assertEquals("Prisijungta", messages.get(Locale.forLanguageTag("lt-LT"), "login-success"));
        assertEquals("Conectado BR", messages.get(Locale.forLanguageTag("pt-BR"), "login-success"));
        assertEquals(messages.get("login-success"), messages.get(Locale.FRENCH, "login-success"));
    }

    @Test
    void rejectsUnknownTranslationKeys() throws Exception {
        Files.writeString(directory.resolve("messages_lt.yml"), "typo-key: value\n");
        assertThrows(IllegalArgumentException.class, () -> OpenGateMessages.load(directory));
    }
}
