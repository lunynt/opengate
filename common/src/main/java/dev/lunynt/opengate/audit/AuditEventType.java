package dev.lunynt.opengate.audit;

public enum AuditEventType {
    REGISTRATION,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_RATE_LIMITED,
    PASSWORD_CHANGED,
    SESSION_REVOKED,
    ACCOUNT_DELETED,
    TOTP_ENABLED,
    TOTP_DISABLED
}
