package dev.lunynt.opengate.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProtectedAccountConfigurationTest {
    @Test
    void enrollmentExceptionAllowsOnlySetupAndConfirmation() {
        var configuration = new ProtectedAccountConfiguration(List.of("group.admin"));
        assertTrue(configuration.permitsTotpAction("group.admin"::equals, true, "setup"));
        assertTrue(configuration.permitsTotpAction("group.admin"::equals, true, "CONFIRM"));
        assertFalse(configuration.permitsTotpAction("group.admin"::equals, true, "disable"));
        assertFalse(configuration.permitsTotpAction("group.admin"::equals, true, "unknown"));
        assertFalse(configuration.permitsTotpAction("group.admin"::equals, false, "setup"));
        assertFalse(configuration.permitsTotpAction("group.admin"::equals, false, "confirm"));
        assertTrue(configuration.permitsTotpAction("group.member"::equals, false, "disable"));
    }

    @Test
    void protectsWhenAnyConfiguredPermissionMatches() {
        var configuration = new ProtectedAccountConfiguration(List.of("group.admin", "opengate.protected"));

        assertTrue(configuration.protects("group.admin"::equals));
        assertFalse(configuration.protects("group.member"::equals));
    }

    @Test
    void protectsConfiguredOfflineAccountsByNameOrUuid() {
        var playerId = java.util.UUID.randomUUID();
        var configuration = new ProtectedAccountConfiguration(
                List.of(), List.of("Owner", playerId.toString()));

        assertTrue(configuration.protects("owner", java.util.UUID.randomUUID()));
        assertTrue(configuration.protects("SomeoneElse", playerId));
        assertFalse(configuration.protects("Member", java.util.UUID.randomUUID()));
    }

    @Test
    void rejectsUnsafePermissionNodes() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProtectedAccountConfiguration(List.of("permission with spaces")));
    }
}
