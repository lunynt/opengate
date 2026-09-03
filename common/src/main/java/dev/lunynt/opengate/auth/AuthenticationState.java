package dev.lunynt.opengate.auth;

public enum AuthenticationState {
    CONNECTING,
    AWAITING_REGISTRATION,
    REGISTERING,
    AWAITING_PASSWORD,
    VERIFYING_PASSWORD,
    AWAITING_TOTP,
    VERIFYING_TOTP,
    AUTHENTICATED,
    RELEASED,
    CLOSED
}
