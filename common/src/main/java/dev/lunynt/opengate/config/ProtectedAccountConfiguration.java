package dev.lunynt.opengate.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public record ProtectedAccountConfiguration(List<String> permissions) {
    private static final Pattern VALID_PERMISSION = Pattern.compile("[a-z0-9][a-z0-9_.-]{0,127}");

    public ProtectedAccountConfiguration {
        permissions = permissions.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .peek(value -> {
                    if (!VALID_PERMISSION.matcher(value).matches()) {
                        throw new IllegalArgumentException("invalid protected account permission: " + value);
                    }
                })
                .distinct()
                .toList();
    }

    static ProtectedAccountConfiguration from(Properties properties) {
        var values = new LinkedHashSet<String>();
        for (var raw : properties.getProperty("protected-account-permissions", "").split(",")) {
            var value = raw.trim();
            if (!value.isEmpty()) values.add(value);
        }
        return new ProtectedAccountConfiguration(List.copyOf(values));
    }

    public boolean protects(Predicate<String> permissionCheck) {
        return permissions.stream().anyMatch(permissionCheck);
    }

    public boolean permitsTotpAction(Predicate<String> permissionCheck, boolean enrollment, String action) {
        return !protects(permissionCheck) || enrollment
                && ("setup".equalsIgnoreCase(action) || "confirm".equalsIgnoreCase(action));
    }
}
