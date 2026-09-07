package dev.lunynt.opengate.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

public record CommandConfiguration(Map<String, List<String>> aliases) {
    private static final Pattern VALID_LABEL = Pattern.compile("[a-z0-9][a-z0-9_-]{0,31}");
    private static final List<String> COMMANDS = List.of("login", "register", "totp", "2fa", "account", "opengate");

    public CommandConfiguration {
        var copy = new LinkedHashMap<String, List<String>>();
        var claimed = new LinkedHashSet<String>(COMMANDS);
        for (var command : COMMANDS) {
            var values = List.copyOf(aliases.getOrDefault(command, List.of()));
            for (var alias : values) {
                if (!VALID_LABEL.matcher(alias).matches()) {
                    throw new IllegalArgumentException("invalid alias for " + command + ": " + alias);
                }
                if (!claimed.add(alias)) {
                    throw new IllegalArgumentException("command label is configured more than once: " + alias);
                }
            }
            copy.put(command, values);
        }
        aliases = Map.copyOf(copy);
    }

    static CommandConfiguration from(Properties properties) {
        var defaults = Map.of("login", "l", "register", "reg");
        var result = new LinkedHashMap<String, List<String>>();
        for (var command : COMMANDS) {
            var raw = properties.getProperty("command." + command + ".aliases", defaults.getOrDefault(command, ""));
            var values = new ArrayList<String>();
            for (var value : raw.split(",")) {
                var alias = value.trim().toLowerCase(Locale.ROOT);
                if (!alias.isEmpty() && !values.contains(alias)) values.add(alias);
            }
            result.put(command, values);
        }
        return new CommandConfiguration(result);
    }

    public List<String> aliases(String command) {
        var result = aliases.get(command);
        if (result == null) throw new IllegalArgumentException("unknown command: " + command);
        return result;
    }

    public Set<String> authenticationLabels() {
        var labels = new LinkedHashSet<>(List.of("login", "register", "totp", "2fa"));
        labels.addAll(aliases("login"));
        labels.addAll(aliases("register"));
        labels.addAll(aliases("totp"));
        labels.addAll(aliases("2fa"));
        return Set.copyOf(labels);
    }

    public boolean isAuthenticationLabel(String label) {
        var command = commandName(label);
        return command != null && authenticationLabels().contains(command);
    }

    public String canonical(String label) {
        var normalized = commandName(label);
        if (normalized == null) return label.toLowerCase(Locale.ROOT);
        if (COMMANDS.contains(normalized)) return normalized;
        return aliases.entrySet().stream()
                .filter(entry -> entry.getValue().contains(normalized))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(normalized);
    }

    private static String commandName(String label) {
        var normalized = label.toLowerCase(Locale.ROOT);
        var separator = normalized.indexOf(':');
        if (separator < 0) return normalized;
        if (separator != normalized.lastIndexOf(':')
                || !normalized.substring(0, separator).equals("opengate")) {
            return null;
        }
        return normalized.substring(separator + 1);
    }
}
