package dev.lunynt.opengate.config;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

final class YamlConfiguration {
    private static final Map<String, String> KEYS = keys();

    private YamlConfiguration() {
    }

    static Properties load(Path file) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(12);
        options.setCodePointLimit(1_048_576);
        var yaml = new Yaml(new SafeConstructor(options));
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object document = yaml.load(reader);
            if (!(document instanceof Map<?, ?> root)) {
                throw new IllegalArgumentException("config.yml must contain a YAML map");
            }
            var values = new Properties();
            flatten(root, "", values);
            return values;
        } catch (IOException exception) {
            throw new IllegalStateException("could not read " + file, exception);
        }
    }

    static void migrateConfig(Path file, Properties legacy, String defaults) {
        var root = yamlMap(defaults, "built-in configuration");
        KEYS.forEach((path, property) -> {
            if (legacy.containsKey(property)) set(root, path, legacy.getProperty(property));
        });
        write(file, root);
    }

    static void migrateMessages(Path file, Properties legacy, String defaults) {
        var root = yamlMap(defaults, "built-in messages");
        legacy.stringPropertyNames().forEach(key -> root.put(key, legacy.getProperty(key)));
        write(file, root);
    }

    static Map<String, String> loadFlat(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return loadFlat(reader, file.toString());
        } catch (IOException exception) {
            throw new IllegalStateException("could not read " + file, exception);
        }
    }

    static Map<String, String> loadFlat(String contents) {
        return loadFlat(new StringReader(contents), "built-in messages");
    }

    private static Map<String, String> loadFlat(Reader reader, String source) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setAllowRecursiveKeys(false);
        options.setMaxAliasesForCollections(1);
        options.setNestingDepthLimit(4);
        options.setCodePointLimit(1_048_576);
        Object document = new Yaml(new SafeConstructor(options)).load(reader);
        if (!(document instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException(source + " must contain a YAML map");
        }
        var result = new LinkedHashMap<String, String>();
        root.forEach((key, value) -> {
            if (!(key instanceof String name) || value instanceof Map<?, ?> || value instanceof List<?>) {
                throw new IllegalArgumentException(source + " must contain string message values");
            }
            result.put(name, value == null ? "" : String.valueOf(value));
        });
        return Map.copyOf(result);
    }

    private static LinkedHashMap<String, Object> yamlMap(String contents, String source) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object document = new Yaml(new SafeConstructor(options)).load(contents);
        if (!(document instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException(source + " must contain a YAML map");
        }
        var root = new LinkedHashMap<String, Object>();
        values.forEach((key, value) -> root.put(String.valueOf(key), value));
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void set(Map<String, Object> root, String path, String value) {
        var parts = path.split("\\.");
        Map<String, Object> current = root;
        for (var index = 0; index < parts.length - 1; index++) {
            current = (Map<String, Object>) current.get(parts[index]);
        }
        var key = parts[parts.length - 1];
        var existing = current.get(key);
        if (existing instanceof List<?>) {
            current.put(key, value.isBlank() ? List.of() : java.util.Arrays.stream(value.split(","))
                    .map(String::trim).filter(item -> !item.isEmpty()).toList());
        } else if (existing instanceof Boolean) {
            current.put(key, Boolean.parseBoolean(value));
        } else if (existing instanceof Number) {
            current.put(key, Integer.parseInt(value));
        } else {
            current.put(key, value);
        }
    }

    private static void write(Path file, Map<String, Object> values) {
        var options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        try {
            Files.writeString(file, new Yaml(options).dump(values), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("could not migrate " + file, exception);
        }
    }

    private static void flatten(Map<?, ?> map, String parent, Properties values) {
        for (var entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("config.yml keys must be strings");
            }
            var path = parent.isEmpty() ? key : parent + "." + key;
            var value = entry.getValue();
            if (value instanceof Map<?, ?> child) {
                flatten(child, path, values);
            } else {
                var property = KEYS.get(path);
                if (property == null) throw new IllegalArgumentException("unknown configuration key: " + path);
                values.setProperty(property, scalar(value, path));
            }
        }
    }

    private static String scalar(Object value, String path) {
        if (value == null) return "";
        if (value instanceof List<?> list) {
            return list.stream().map(item -> {
                if (item instanceof Map<?, ?> || item instanceof List<?>) {
                    throw new IllegalArgumentException(path + " must contain scalar values");
                }
                return String.valueOf(item);
            }).reduce((left, right) -> left + "," + right).orElse("");
        }
        if (value instanceof Map<?, ?>) throw new IllegalArgumentException(path + " must be a scalar or list");
        return String.valueOf(value);
    }

    private static Map<String, String> keys() {
        var keys = new LinkedHashMap<String, String>();
        add(keys, "authentication.timeout-seconds", "authentication-timeout-seconds");
        add(keys, "authentication.maximum-login-attempts", "maximum-login-attempts");
        add(keys, "authentication.maximum-ip-failures", "maximum-ip-failures");
        add(keys, "authentication.maximum-account-failures", "maximum-account-failures");
        add(keys, "authentication.ip-failure-window-minutes", "ip-failure-window-minutes");
        add(keys, "authentication.maximum-registrations-per-ip", "maximum-registrations-per-ip");
        add(keys, "authentication.registration-window-minutes", "registration-window-minutes");
        add(keys, "authentication.password.minimum-length", "minimum-password-length");
        add(keys, "authentication.password.maximum-length", "maximum-password-length");
        add(keys, "authentication.premium-lookup.enabled", "premium-lookup-enabled");
        add(keys, "authentication.premium-lookup.auto-detect", "premium-auto-detect");
        add(keys, "authentication.premium-lookup.reserve-names", "premium-reserve-names");
        add(keys, "authentication.premium-lookup.timeout-millis", "premium-lookup-timeout-millis");
        add(keys, "authentication.require-login-permissions", "require-login-permissions");
        add(keys, "authentication.require-2fa-permissions", "require-2fa-permissions");
        add(keys, "authentication.protected-account-permissions", "protected-account-permissions");
        add(keys, "proxy.auth-server", "proxy-auth-server");
        add(keys, "proxy.lobby-servers", "proxy-lobby-servers");
        add(keys, "database.type", "database-type");
        add(keys, "database.url", "database-url");
        add(keys, "database.username", "database-username");
        add(keys, "database.password", "database-password");
        add(keys, "database.pool-size", "database-pool-size");
        add(keys, "database.connection-timeout-millis", "database-connection-timeout-millis");
        for (var command : List.of("login", "register", "totp", "2fa", "account", "premium", "cracked", "opengate")) {
            add(keys, "commands." + command + ".aliases", "command." + command + ".aliases");
        }
        add(keys, "identity.translate-uuid4-to-uuid7", "translate-uuid4-to-uuid7");
        add(keys, "sessions.cookies.enabled", "cookie-sessions-enabled");
        add(keys, "sessions.cookies.lifetime-hours", "cookie-session-hours");
        add(keys, "redis.enabled", "redis-enabled");
        add(keys, "redis.uri", "redis-uri");
        add(keys, "redis.channel", "redis-channel");
        add(keys, "redis.timeout-millis", "redis-timeout-millis");
        add(keys, "integrations.ajqueue.enabled", "ajqueue-enabled");
        add(keys, "integrations.ajqueue.target", "ajqueue-target");
        add(keys, "minecraft-dialogs.enabled", "minecraft-dialogs-enabled");
        add(keys, "offline-whitelist.enabled", "offline-whitelist-enabled");
        add(keys, "offline-whitelist.players", "offline-whitelist");
        return Map.copyOf(keys);
    }

    private static void add(Map<String, String> keys, String yaml, String property) {
        keys.put(yaml, property);
    }
}
