package dev.lunynt.opengate.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CookieAuthenticationRequirementsTest {
    @Test
    void cookieResumesOrdinaryPasswordSession() {
        var session = session(false, AuthenticationRequirements.NONE);
        session.resumeWithCookie();
        assertEquals(AuthenticationState.AUTHENTICATED, session.state());
        assertEquals(AuthenticationMethod.COOKIE, session.method().orElseThrow());
    }

    @Test
    void cookieCannotBypassPermissionRequiredPassword() {
        var session = session(false, new AuthenticationRequirements(true, false));
        session.resumeWithCookie();
        assertEquals(AuthenticationState.AWAITING_PASSWORD, session.state());
    }

    @Test
    void cookieStillRequiresEnrolledSecondFactor() {
        var session = session(true, new AuthenticationRequirements(false, true));
        session.resumeWithCookie();
        assertEquals(AuthenticationState.AWAITING_TOTP, session.state());
        assertThrows(IllegalStateException.class, session::release);
        session.beginTotpVerification();
        session.acceptTotp();
        session.release();
        assertEquals(AuthenticationState.RELEASED, session.state());
    }

    @Test
    void cookieRequiresEnrollmentWhenPermissionWasAddedAfterIssuance() {
        var session = session(false, new AuthenticationRequirements(false, true));
        session.resumeWithCookie();
        assertEquals(AuthenticationState.AWAITING_TOTP_ENROLLMENT, session.state());
        assertThrows(IllegalStateException.class, session::release);
        assertThrows(IllegalStateException.class, session::resumeWithCookie);
    }

    @Test
    void cookieCannotInterruptPasswordVerificationOrReviveClosedSession() {
        var session = session(false, AuthenticationRequirements.NONE);
        session.beginPasswordVerification();
        assertThrows(IllegalStateException.class, session::resumeWithCookie);
        session.close();
        assertThrows(IllegalStateException.class, session::resumeWithCookie);
    }

    private static AuthenticationSession session(boolean totp, AuthenticationRequirements requirements) {
        var session = new AuthenticationSession(UUID.randomUUID(), Instant.EPOCH);
        session.resolve(new ResolvedIdentity("Player", UUID.randomUUID(), IdentityType.OFFLINE,
                true, true, totp), requirements);
        return session;
    }
}
