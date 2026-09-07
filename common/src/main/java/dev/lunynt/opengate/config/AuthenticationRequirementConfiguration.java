package dev.lunynt.opengate.config;

import dev.lunynt.opengate.auth.AuthenticationRequirements;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public record AuthenticationRequirementConfiguration(
        List<String> passwordPermissions, List<String> totpPermissions) {
    private static final Pattern VALID_PERMISSION = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,127}");

    public AuthenticationRequirementConfiguration {
        passwordPermissions = validated(passwordPermissions);
        totpPermissions = validated(totpPermissions);
    }

    static AuthenticationRequirementConfiguration from(Properties properties) {
        return new AuthenticationRequirementConfiguration(
                list(properties, "require-login-permissions"),
                list(properties, "require-2fa-permissions"));
    }

    public AuthenticationRequirements forPermissions(Predicate<String> permissionCheck) {
        var totp = totpPermissions.stream().anyMatch(permissionCheck);
        var password = totp || passwordPermissions.stream().anyMatch(permissionCheck);
        return new AuthenticationRequirements(password, totp);
    }

    private static List<String> list(Properties properties, String key) {
        var values = new LinkedHashSet<String>();
        for (var raw : properties.getProperty(key, "").split(",")) {
            var value = raw.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) values.add(value);
        }
        return List.copyOf(values);
    }

    private static List<String> validated(List<String> values) {
        return values.stream().map(value -> value.toLowerCase(Locale.ROOT)).peek(value -> {
            if (!VALID_PERMISSION.matcher(value).matches()) {
                throw new IllegalArgumentException("invalid authentication requirement permission: " + value);
            }
        }).distinct().toList();
    }
}
