package dev.lunynt.opengate.auth;

public record AuthenticationRequirements(boolean passwordRequired, boolean totpRequired) {
    public static final AuthenticationRequirements NONE = new AuthenticationRequirements(false, false);
}
