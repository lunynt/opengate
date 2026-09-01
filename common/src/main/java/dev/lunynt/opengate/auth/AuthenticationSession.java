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

    public AuthenticationSession(UUID connectionId, Instant createdAt) {
        this.connectionId = Objects.requireNonNull(connectionId, "connectionId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public synchronized void resolve(ResolvedIdentity resolvedIdentity) {
        requireState(AuthenticationState.CONNECTING);
        identity = Objects.requireNonNull(resolvedIdentity, "resolvedIdentity");

        var automaticMethod = identity.automaticAuthentication();
        if (automaticMethod.isPresent()) {
            authenticate(automaticMethod.orElseThrow());
        } else if (!identity.registered()) {
            state = AuthenticationState.AWAITING_REGISTRATION;
        } else if (identity.passwordRequired()) {
            state = AuthenticationState.AWAITING_PASSWORD;
        } else {
            state = AuthenticationState.AWAITING_TOTP;
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
        authenticate(AuthenticationMethod.REGISTRATION);
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
        authenticate(AuthenticationMethod.PASSWORD);
    }

    public synchronized void acceptTotp() {
        requireState(AuthenticationState.AWAITING_TOTP);
        authenticate(AuthenticationMethod.TOTP);
    }

    public synchronized void release() {
        requireState(AuthenticationState.AUTHENTICATED);
        state = AuthenticationState.RELEASED;
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
