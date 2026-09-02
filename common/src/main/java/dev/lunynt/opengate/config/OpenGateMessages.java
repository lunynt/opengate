package dev.lunynt.opengate.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public final class OpenGateMessages {
    private static final String DEFAULTS = """
            register-prompt=Register with /register <password> <password>.
            login-prompt=Log in with /login <password>.
            automatic-login=Authenticated automatically.
            login-success=Login successful.
            registration-success=Registration complete.
            incorrect-password=Incorrect password.
            too-many-attempts=Too many failed login attempts.
            rate-limited=Too many failed logins from your address. Try again later.
            authentication-timeout=Authentication timed out.
            authenticate-first=Authenticate before doing that.
            limbo-missing=OpenGate requires the configured authentication server.
            invalid-username=Username must contain 3-16 letters, numbers, or underscores.
            username-case-mismatch=Use the exact capitalization registered for this username.
            profile-lookup-unavailable=Could not verify account ownership. Try again shortly.
            totp-prompt=Enter your authenticator code with /totp <code>.
            totp-invalid=That authenticator code is invalid.
            totp-success=Two-factor authentication complete.
            totp-setup=Copy this setup URI into your authenticator, then use /2fa confirm <code>:
            totp-enabled=Two-factor authentication enabled.
            totp-disabled=Two-factor authentication disabled.
            password-changed=Password changed successfully.
            logged-out=Logged out. Reconnect to authenticate again.
            account-deleted=Your OpenGate account was deleted.
            account-action-failed=Account action failed. Check your password and try again.
            """;

    private final Map<String, String> messages;

    private OpenGateMessages(Map<String, String> messages) {
        this.messages = Map.copyOf(messages);
    }

    public static OpenGateMessages load(Path dataDirectory) {
        var file = dataDirectory.resolve("messages.properties");
        OpenGateConfig.createDefault(file, DEFAULTS);
        var properties = OpenGateConfig.loadProperties(file);
        return new OpenGateMessages(properties.stringPropertyNames().stream()
                .collect(Collectors.toMap(key -> key, properties::getProperty)));
    }

    public String get(String key) {
        var value = messages.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown message key: " + key);
        }
        return value;
    }
}
