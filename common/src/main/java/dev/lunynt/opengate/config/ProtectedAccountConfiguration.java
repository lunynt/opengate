package dev.lunynt.opengate.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public record ProtectedAccountConfiguration(List<String> permissions, List<String> accounts) {
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
        accounts = accounts.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .peek(value -> {
                    if (!value.matches("[a-z0-9_]{3,16}") && !isUuid(value)) {
                        throw new IllegalArgumentException("invalid protected account: " + value);
                    }
                })
                .distinct()
                .toList();
    }

    public ProtectedAccountConfiguration(List<String> permissions) {
        this(permissions, List.of());
    }

    static ProtectedAccountConfiguration from(Properties properties) {
        var values = new LinkedHashSet<String>();
        for (var raw : properties.getProperty("protected-account-permissions", "").split(",")) {
            var value = raw.trim();
            if (!value.isEmpty()) values.add(value);
        }
        var accounts = new LinkedHashSet<String>();
        for (var raw : properties.getProperty("protected-account-players", "").split(",")) {
            var value = raw.trim();
            if (!value.isEmpty()) accounts.add(value);
        }
        return new ProtectedAccountConfiguration(List.copyOf(values), List.copyOf(accounts));
    }

    public boolean protects(Predicate<String> permissionCheck) {
        return permissions.stream().anyMatch(permissionCheck);
    }

    public boolean protects(String username, java.util.UUID playerId) {
        return accounts.contains(username.toLowerCase(Locale.ROOT))
                || accounts.contains(playerId.toString().toLowerCase(Locale.ROOT));
    }

    public boolean permitsTotpAction(Predicate<String> permissionCheck, boolean enrollment, String action) {
        return !protects(permissionCheck) || enrollment
                && ("setup".equalsIgnoreCase(action) || "confirm".equalsIgnoreCase(action));
    }

    private static boolean isUuid(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
