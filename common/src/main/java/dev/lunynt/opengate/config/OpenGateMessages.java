package dev.lunynt.opengate.config;

import java.nio.file.Path;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

public final class OpenGateMessages {
    private static final String DEFAULTS = """
            register-prompt=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʀᴇɢɪꜱᴛᴇʀ ᴡɪᴛʜ &f/register <password> <password>
            login-prompt=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʟᴏɢ ɪɴ ᴡɪᴛʜ &f/login <password>
            automatic-login=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛᴇᴅ
            login-success=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ʟᴏɢɪɴ ᴄᴏᴍᴘʟᴇᴛᴇ
            registration-success=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴀᴄᴄᴏᴜɴᴛ ᴄʀᴇᴀᴛᴇᴅ
            incorrect-password=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ɪɴᴄᴏʀʀᴇᴄᴛ ᴘᴀꜱꜱᴡᴏʀᴅ
            too-many-attempts=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛᴏᴏ ᴍᴀɴʏ ᴀᴛᴛᴇᴍᴘᴛꜱ
            rate-limited=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛʀʏ ᴀɢᴀɪɴ ʟᴀᴛᴇʀ
            authentication-timeout=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛɪᴏɴ ᴛɪᴍᴇᴅ ᴏᴜᴛ
            authenticate-first=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛᴇ ꜰɪʀꜱᴛ
            limbo-missing=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴜᴛʜ ꜱᴇʀᴠᴇʀ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ
            invalid-username=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴜꜱᴇ 3-16 ʟᴇᴛᴛᴇʀꜱ, ɴᴜᴍʙᴇʀꜱ, ᴏʀ ᴜɴᴅᴇʀꜱᴄᴏʀᴇꜱ
            username-case-mismatch=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴜꜱᴇ ʏᴏᴜʀ ʀᴇɢɪꜱᴛᴇʀᴇᴅ ɴᴀᴍᴇ ᴄᴀꜱɪɴɢ
            profile-lookup-unavailable=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴄᴄᴏᴜɴᴛ ᴠᴇʀɪꜰɪᴄᴀᴛɪᴏɴ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ
            totp-prompt=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴇɴᴛᴇʀ ʏᴏᴜʀ ᴄᴏᴅᴇ ᴡɪᴛʜ &f/totp <code>
            totp-invalid=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ɪɴᴠᴀʟɪᴅ ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛᴏʀ ᴄᴏᴅᴇ
            totp-success=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴛᴡᴏ-ꜰᴀᴄᴛᴏʀ ᴄᴏᴍᴘʟᴇᴛᴇ
            totp-setup=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴄᴏᴘʏ ᴛʜɪꜱ ᴜʀɪ, ᴛʜᴇɴ ᴜꜱᴇ &f/2fa confirm <code>&7:
            totp-enabled=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴛᴡᴏ-ꜰᴀᴄᴛᴏʀ ᴇɴᴀʙʟᴇᴅ
            totp-disabled=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴛᴡᴏ-ꜰᴀᴄᴛᴏʀ ᴅɪꜱᴀʙʟᴇᴅ
            password-changed=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴘᴀꜱꜱᴡᴏʀᴅ ᴄʜᴀɴɢᴇᴅ
            logged-out=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʟᴏɢɢᴇᴅ ᴏᴜᴛ
            account-deleted=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴀᴄᴄᴏᴜɴᴛ ᴅᴇʟᴇᴛᴇᴅ
            account-action-failed=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴄᴄᴏᴜɴᴛ ᴀᴄᴛɪᴏɴ ꜰᴀɪʟᴇᴅ
            protected-account=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴄʀᴇᴅᴇɴᴛɪᴀʟ ᴄʜᴀɴɢᴇꜱ ᴀʀᴇ ᴅɪꜱᴀʙʟᴇᴅ ꜰᴏʀ ᴛʜɪꜱ ᴀᴄᴄᴏᴜɴᴛ
            totp-enrollment-required=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ꜱᴇᴛ ᴜᴘ 2ꜰᴀ ᴡɪᴛʜ &f/2fa setup <password>
            offline-not-whitelisted=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛʜɪꜱ ᴏꜰꜰʟɪɴᴇ ᴀᴄᴄᴏᴜɴᴛ ɪꜱ ɴᴏᴛ ᴡʜɪᴛᴇʟɪꜱᴛᴇᴅ
            session-revoked=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ʏᴏᴜʀ ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛɪᴏɴ ꜱᴇꜱꜱɪᴏɴ ᴡᴀꜱ ʀᴇᴠᴏᴋᴇᴅ
            service-busy=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ꜱᴇʀᴠɪᴄᴇ ʙᴜꜱʏ, ᴛʀʏ ᴀɢᴀɪɴ
            players-only=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴛʜɪꜱ ᴄᴏᴍᴍᴀɴᴅ ɪꜱ ꜰᴏʀ ᴘʟᴀʏᴇʀꜱ
            usage-register=&7Usage: /register <password> <password>
            usage-login=&7Usage: /login <password>
            usage-totp=&7Usage: /totp <code>
            usage-2fa=&7Usage: /2fa setup <password> | confirm <code> | disable <password>
            usage-account=&7Usage: /account password <current> <new> | logout | delete <password> confirm
            usage-account-password=&7Usage: /account password <current> <new>
            usage-account-delete=&7Usage: /account delete <password> confirm
            registration-not-required=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʀᴇɢɪꜱᴛʀᴀᴛɪᴏɴ ɪꜱ ɴᴏᴛ ʀᴇQᴜɪʀᴇᴅ
            password-not-required=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴘᴀꜱꜱᴡᴏʀᴅ ʟᴏɢɪɴ ɪꜱ ɴᴏᴛ ʀᴇQᴜɪʀᴇᴅ
            totp-not-required=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴛᴡᴏ-ꜰᴀᴄᴛᴏʀ ɪꜱ ɴᴏᴛ ʀᴇQᴜɪʀᴇᴅ
            current-password-required=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴛʜɪꜱ ᴀᴄᴛɪᴏɴ ʀᴇQᴜɪʀᴇꜱ ʏᴏᴜʀ ᴄᴜʀʀᴇɴᴛ ᴘᴀꜱꜱᴡᴏʀᴅ
            password-policy-invalid=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴘᴀꜱꜱᴡᴏʀᴅ ᴅᴏᴇꜱ ɴᴏᴛ ᴍᴇᴇᴛ ᴛʜᴇ ᴄᴏɴꜰɪɢᴜʀᴇᴅ ʟᴇɴɢᴛʜ ʀᴇQᴜɪʀᴇᴍᴇɴᴛꜱ
            dialog-register-title=Register
            dialog-login-title=Login
            dialog-register-button=Enter registration command
            dialog-login-button=Enter login command
            queue-unavailable=&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛʜᴇ ꜱᴇʀᴠᴇʀ Qᴜᴇᴜᴇ ɪꜱ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ
            admin-no-permission=&cYou do not have permission to use this command.
            admin-usage=&7Usage: /opengate lookup <player> | audit <player> [limit] | revoke <player>
            admin-request-failed=&cOpenGate could not complete that request.
            admin-account-not-found=&cAccount not found.
            admin-account-header=&7OpenGate account: &f
            admin-uuid=&7UUID: &f
            admin-identity=&7Identity: &f
            admin-created=&7Created: &f
            admin-totp=&7TOTP: &f
            admin-enabled=enabled
            admin-disabled=disabled
            admin-audit-limit=&cAudit limit must be a number from 1 to 100.
            admin-audit-header=&7Recent OpenGate events for &f
            admin-revocation-failed=&cRevocation failed; some sessions may already be closed.
            admin-revoked=&aRevoked sessions for &f
            """;

    private final Map<String, String> messages;
    private final Map<String, Map<String, String>> localized;

    private OpenGateMessages(Map<String, String> messages, Map<String, Map<String, String>> localized) {
        this.messages = Map.copyOf(messages);
        this.localized = Map.copyOf(localized);
    }

    public static OpenGateMessages load(Path dataDirectory) {
        var file = dataDirectory.resolve("messages.properties");
        OpenGateConfig.createDefault(file, DEFAULTS);
        var properties = defaults();
        properties.putAll(OpenGateConfig.loadProperties(file));
        var base = properties.stringPropertyNames().stream()
                .collect(Collectors.toMap(key -> key, properties::getProperty));
        return new OpenGateMessages(base, loadLocalized(dataDirectory, base));
    }

    private static Properties defaults() {
        var properties = new Properties();
        try {
            properties.load(new StringReader(DEFAULTS));
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("invalid built-in OpenGate messages", exception);
        }
    }

    public String get(String key) {
        var value = messages.get(key);
        if (value == null) {
            throw new IllegalArgumentException("unknown message key: " + key);
        }
        return value;
    }

    public String get(Locale locale, String key) {
        if (locale != null) {
            var exact = localized.get(locale.toLanguageTag().toLowerCase(Locale.ROOT));
            if (exact != null && exact.containsKey(key)) return exact.get(key);
            var language = localized.get(locale.getLanguage().toLowerCase(Locale.ROOT));
            if (language != null && language.containsKey(key)) return language.get(key);
        }
        return get(key);
    }

    private static Map<String, Map<String, String>> loadLocalized(Path directory, Map<String, String> base) {
        var bundles = new LinkedHashMap<String, Map<String, String>>();
        try (var files = java.nio.file.Files.newDirectoryStream(directory, "messages_*.properties")) {
            for (var file : files) {
                var name = file.getFileName().toString();
                var tag = name.substring("messages_".length(), name.length() - ".properties".length())
                        .replace('_', '-').toLowerCase(Locale.ROOT);
                if (tag.isBlank() || Locale.forLanguageTag(tag).getLanguage().isBlank()) {
                    throw new IllegalArgumentException("invalid message locale filename: " + name);
                }
                var translated = new LinkedHashMap<>(base);
                var overrides = OpenGateConfig.loadProperties(file);
                for (var key : overrides.stringPropertyNames()) {
                    if (!base.containsKey(key)) throw new IllegalArgumentException(
                            "unknown message key " + key + " in " + name);
                    translated.put(key, overrides.getProperty(key));
                }
                bundles.put(tag, Map.copyOf(translated));
            }
            return Map.copyOf(bundles);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("could not load localized OpenGate messages", exception);
        }
    }
}
