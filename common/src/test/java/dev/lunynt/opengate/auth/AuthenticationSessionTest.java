package dev.lunynt.opengate.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthenticationSessionTest {
    private static final UUID CONNECTION_ID = UUID.fromString("f23a0f44-0f7a-43bb-a98c-6fd61537af25");
    private static final UUID PLAYER_ID = UUID.fromString("1bd94dd9-6461-485c-b444-255a87e17c90");

    @Test
    void premiumIdentityAuthenticatesAutomatically() {
        var session = new AuthenticationSession(CONNECTION_ID, Instant.EPOCH);

        session.resolve(identity(IdentityType.PREMIUM, true, false, false));

        assertEquals(AuthenticationState.AUTHENTICATED, session.state());
        assertEquals(AuthenticationMethod.PREMIUM, session.method().orElseThrow());
    }

    @Test
    void passwordAndTotpAreSeparateStages() {
        var session = new AuthenticationSession(CONNECTION_ID, Instant.EPOCH);
        session.resolve(identity(IdentityType.OFFLINE, true, true, true));

        session.beginPasswordVerification();
        session.acceptPassword();
        assertEquals(AuthenticationState.AWAITING_TOTP, session.state());

        session.beginTotpVerification();
        session.acceptTotp();
        assertEquals(AuthenticationState.AUTHENTICATED, session.state());
        assertEquals(AuthenticationMethod.TOTP, session.method().orElseThrow());
    }

    @Test
    void invalidTransitionsFailClosed() {
        var session = new AuthenticationSession(CONNECTION_ID, Instant.EPOCH);

        assertThrows(IllegalStateException.class, session::release);
        assertEquals(AuthenticationState.CONNECTING, session.state());
    }

    @Test
    void closesAfterMaximumPasswordAttempts() {
        var session = new AuthenticationSession(CONNECTION_ID, Instant.EPOCH);
        session.resolve(identity(IdentityType.OFFLINE, true, true, false));

        session.beginPasswordVerification();
        assertFalse(session.rejectPassword(2));
        session.beginPasswordVerification();
        assertTrue(session.rejectPassword(2));

        assertEquals(AuthenticationState.CLOSED, session.state());
        assertEquals(2, session.failedAttempts());
    }

    @Test
    void closesAfterMaximumTotpAttempts() {
        var session = new AuthenticationSession(CONNECTION_ID, Instant.EPOCH);
        session.resolve(identity(IdentityType.OFFLINE, true, true, true));
        session.beginPasswordVerification();
        session.acceptPassword();

        session.beginTotpVerification();
        assertFalse(session.rejectTotp(2));
        session.beginTotpVerification();
        assertTrue(session.rejectTotp(2));

        assertEquals(AuthenticationState.CLOSED, session.state());
    }

    private static ResolvedIdentity identity(
            IdentityType type,
            boolean registered,
            boolean passwordRequired,
            boolean totpRequired) {
        return new ResolvedIdentity("Player", PLAYER_ID, type, registered, passwordRequired, totpRequired);
    }
}
