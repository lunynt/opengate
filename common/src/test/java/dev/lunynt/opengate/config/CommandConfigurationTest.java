package dev.lunynt.opengate.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class CommandConfigurationTest {
    @Test
    void loadsMultipleAliases() {
        var properties = new Properties();
        properties.setProperty("command.login.aliases", "l, signin,AUTH");

        var commands = CommandConfiguration.from(properties);

        assertEquals(List.of("l", "signin", "auth"), commands.aliases("login"));
    }

    @Test
    void rejectsAliasesClaimedByAnotherCommand() {
        assertThrows(IllegalArgumentException.class, () -> new CommandConfiguration(Map.of(
                "login", List.of("authenticate"), "register", List.of("authenticate"))));
    }

    @Test
    void rejectsAliasesThatShadowPrimaryCommands() {
        assertThrows(IllegalArgumentException.class,
                () -> new CommandConfiguration(Map.of("login", List.of("register"))));
    }

    @Test
    void authenticatingCommandLabelsRejectForeignNamespaces() {
        var commands = new CommandConfiguration(Map.of("login", List.of("l")));
        assertTrue(commands.isAuthenticationLabel("login"));
        assertTrue(commands.isAuthenticationLabel("L"));
        assertTrue(commands.isAuthenticationLabel("opengate:login"));
        assertTrue(commands.isAuthenticationLabel("OpenGate:L"));
        assertFalse(commands.isAuthenticationLabel("other:login"));
        assertFalse(commands.isAuthenticationLabel("other:l"));
        assertFalse(commands.isAuthenticationLabel("opengate:other:login"));
        assertFalse(commands.isAuthenticationLabel("opengate:"));
        assertEquals("other:l", commands.canonical("other:l"));
    }
}
