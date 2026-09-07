package dev.lunynt.opengate.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class AuthenticationSession {
    private final UUID connectionId;
    private final Instant createdAt;
    private AuthenticationState state = AuthenticationState.CONNECTING;
    private ResolvedIdentity identity;
    private AuthenticationMethod method;
    private int failedAttempts;
    private AuthenticationRequirements requirements = AuthenticationRequirements.NONE;
    private final java.util.function.Consumer<AuthenticationSession> releaseListener;

    public AuthenticationSession(UUID connectionId, Instant createdAt) {
        this(connectionId, createdAt, ignored -> {});
    }

    AuthenticationSession(
            UUID connectionId,
            Instant createdAt,
            java.util.function.Consumer<AuthenticationSession> releaseListener) {
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.releaseListener = Objects.requireNonNull(releaseListener, "releaseListener");
    }

    public synchronized void resolve(ResolvedIdentity resolvedIdentity) {
        resolve(resolvedIdentity, AuthenticationRequirements.NONE);
    }

    public synchronized void resolve(
            ResolvedIdentity resolvedIdentity, AuthenticationRequirements authenticationRequirements) {
        requireState(AuthenticationState.CONNECTING);
        identity = Objects.requireNonNull(resolvedIdentity, "resolvedIdentity");
        requirements = Objects.requireNonNull(authenticationRequirements, "authenticationRequirements");

        var automaticMethod = requirements.equals(AuthenticationRequirements.NONE)
                ? identity.automaticAuthentication()
                : Optional.<AuthenticationMethod>empty();
        if (automaticMethod.isPresent()) {
            authenticate(automaticMethod.orElseThrow());
        } else if (!identity.registered() || requirements.passwordRequired() && !identity.passwordRequired()) {
            state = AuthenticationState.AWAITING_REGISTRATION;
        } else if (identity.passwordRequired()) {
            state = AuthenticationState.AWAITING_PASSWORD;
        } else if (identity.totpRequired()) {
            state = AuthenticationState.AWAITING_TOTP;
        } else if (requirements.totpRequired()) {
            state = AuthenticationState.AWAITING_TOTP_ENROLLMENT;
        } else {
            authenticate(AuthenticationMethod.PREMIUM);
        }
    }

    public synchronized void beginRegistration() {
        requireState(AuthenticationState.AWAITING_REGISTRATION);
        state = AuthenticationState.REGISTERING;
    }

    public synchronized void registrationFailed() {
        requireState(AuthenticationState.REGISTERING);
        state = AuthenticationState.AWAITING_REGISTRATION;
    }

    public synchronized void register() {
        requireState(AuthenticationState.REGISTERING);
        if (identity.totpRequired()) {
            state = AuthenticationState.AWAITING_TOTP;
        } else if (requirements.totpRequired()) {
            state = AuthenticationState.AWAITING_TOTP_ENROLLMENT;
        } else {
            authenticate(AuthenticationMethod.REGISTRATION);
        }
    }

    public synchronized void beginPasswordVerification() {
        requireState(AuthenticationState.AWAITING_PASSWORD);
        state = AuthenticationState.VERIFYING_PASSWORD;
    }

    public synchronized boolean rejectPassword(int maximumAttempts) {
        requireState(AuthenticationState.VERIFYING_PASSWORD);
        failedAttempts++;
        state = failedAttempts >= maximumAttempts
                ? AuthenticationState.CLOSED
                : AuthenticationState.AWAITING_PASSWORD;
        return state == AuthenticationState.CLOSED;
    }

    public synchronized void acceptPassword() {
        requireState(AuthenticationState.VERIFYING_PASSWORD);
        if (identity.totpRequired()) {
            state = AuthenticationState.AWAITING_TOTP;
            return;
        }
        if (requirements.totpRequired()) {
            state = AuthenticationState.AWAITING_TOTP_ENROLLMENT;
        } else {
            authenticate(AuthenticationMethod.PASSWORD);
        }
    }

    public synchronized void acceptTotp() {
        requireState(AuthenticationState.VERIFYING_TOTP);
        authenticate(AuthenticationMethod.TOTP);
    }

    public synchronized void beginTotpVerification() {
        requireState(AuthenticationState.AWAITING_TOTP);
        state = AuthenticationState.VERIFYING_TOTP;
    }

    public synchronized void completeTotpEnrollment() {
        requireState(AuthenticationState.AWAITING_TOTP_ENROLLMENT);
        authenticate(AuthenticationMethod.TOTP);
    }

    public synchronized void resumeWithCookie() {
        if (identity == null || !identity.registered()
                || !(state == AuthenticationState.AWAITING_PASSWORD
                    || state == AuthenticationState.AWAITING_TOTP
                    || state == AuthenticationState.AUTHENTICATED)) {
            throw new IllegalStateException("cookie cannot resume this authentication session");
        }
        if (requirements.passwordRequired()) return;
        if (identity.totpRequired()) {
            state = AuthenticationState.AWAITING_TOTP;
            return;
        }
        if (requirements.totpRequired()) {
            state = AuthenticationState.AWAITING_TOTP_ENROLLMENT;
            return;
        }
        authenticate(AuthenticationMethod.COOKIE);
    }

    public synchronized boolean rejectTotp(int maximumAttempts) {
        requireState(AuthenticationState.VERIFYING_TOTP);
        failedAttempts++;
        state = failedAttempts >= maximumAttempts
                ? AuthenticationState.CLOSED
                : AuthenticationState.AWAITING_TOTP;
        return state == AuthenticationState.CLOSED;
    }

    public synchronized void release() {
        requireState(AuthenticationState.AUTHENTICATED);
        state = AuthenticationState.RELEASED;
        releaseListener.accept(this);
    }

    public synchronized void close() {
        state = AuthenticationState.CLOSED;
    }

    private void authenticate(AuthenticationMethod authenticationMethod) {
        method = authenticationMethod;
        state = AuthenticationState.AUTHENTICATED;
    }

    private void requireState(AuthenticationState expected) {
        if (state != expected) {
            throw new IllegalStateException("expected state " + expected + ", but was " + state);
        }
    }

    public UUID connectionId() {
        return connectionId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public synchronized AuthenticationState state() {
        return state;
    }

    public synchronized Optional<ResolvedIdentity> identity() {
        return Optional.ofNullable(identity);
    }

    public synchronized Optional<AuthenticationMethod> method() {
        return Optional.ofNullable(method);
    }

    public synchronized int failedAttempts() {
        return failedAttempts;
    }
}
