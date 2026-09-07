package dev.lunynt.opengate.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegistrationSecondFactorTest {
    @Test
    void addingRequiredPasswordPreservesExistingSecondFactor() {
        for (var requireTotpPermission : new boolean[] {false, true}) {
            var session = new AuthenticationSession(UUID.randomUUID(), Instant.EPOCH);
            session.resolve(new ResolvedIdentity("Admin", UUID.randomUUID(), IdentityType.PREMIUM,
                    true, false, true), new AuthenticationRequirements(true, requireTotpPermission));
            assertEquals(AuthenticationState.AWAITING_REGISTRATION, session.state());
            session.beginRegistration();
            session.register();
            assertEquals(AuthenticationState.AWAITING_TOTP, session.state());
            assertThrows(IllegalStateException.class, session::release);
            assertThrows(IllegalStateException.class, session::completeTotpEnrollment);
            session.beginTotpVerification();
            session.acceptTotp();
            session.release();
            assertEquals(AuthenticationMethod.TOTP, session.method().orElseThrow());
        }
    }
}
