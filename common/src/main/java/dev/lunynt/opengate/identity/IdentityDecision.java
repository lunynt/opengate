package dev.lunynt.opengate.identity;

public enum IdentityDecision {
    ONLINE,
    OFFLINE,
    DENY_INVALID_USERNAME,
    DENY_CASE_MISMATCH,
    DENY_LOOKUP_UNAVAILABLE,
    DENY_OFFLINE_NOT_WHITELISTED
}
