package dev.lunynt.opengate.account;

public enum AuthenticationResult {
    SUCCESS,
    ACCOUNT_NOT_FOUND,
    WRONG_PASSWORD,
    RATE_LIMITED,
    SERVICE_BUSY
}
