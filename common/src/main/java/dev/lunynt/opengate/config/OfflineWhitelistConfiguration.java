package dev.lunynt.opengate.config;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

public record OfflineWhitelistConfiguration(boolean enabled, Set<String> usernames) {
    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    public OfflineWhitelistConfiguration {
        var normalized = new LinkedHashSet<String>();
        for (var username : usernames) {
            if (!VALID_USERNAME.matcher(username).matches()) {
                throw new IllegalArgumentException("invalid offline whitelist username: " + username);
            }
            normalized.add(username.toLowerCase(Locale.ROOT));
        }
        usernames = Set.copyOf(normalized);
    }

    static OfflineWhitelistConfiguration from(Properties properties) {
        var values = new LinkedHashSet<String>();
        for (var raw : properties.getProperty("offline-whitelist", "").split(",")) {
            var value = raw.trim();
            if (!value.isEmpty()) values.add(value);
        }
        return new OfflineWhitelistConfiguration(
                OpenGateConfig.bool(properties, "offline-whitelist-enabled", false), values);
    }

    public boolean allows(String username) {
        return !enabled || usernames.contains(username.toLowerCase(Locale.ROOT));
    }
}
