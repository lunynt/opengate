package dev.lunynt.opengate.config;

import java.nio.file.Path;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;

public final class OpenGateMessages {
    private static final String DEFAULTS = """
            register-prompt: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʀᴇɢɪꜱᴛᴇʀ ᴡɪᴛʜ &f/register <password> <password>'
            login-prompt: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʟᴏɢ ɪɴ ᴡɪᴛʜ &f/login <password>'
            automatic-login: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛᴇᴅ'
            login-success: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ʟᴏɢɪɴ ᴄᴏᴍᴘʟᴇᴛᴇ'
            registration-success: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴀᴄᴄᴏᴜɴᴛ ᴄʀᴇᴀᴛᴇᴅ'
            incorrect-password: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ɪɴᴄᴏʀʀᴇᴄᴛ ᴘᴀꜱꜱᴡᴏʀᴅ'
            too-many-attempts: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛᴏᴏ ᴍᴀɴʏ ᴀᴛᴛᴇᴍᴘᴛꜱ'
            rate-limited: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛʀʏ ᴀɢᴀɪɴ ʟᴀᴛᴇʀ'
            authentication-timeout: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛɪᴏɴ ᴛɪᴍᴇᴅ ᴏᴜᴛ'
            authenticate-first: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛᴇ ꜰɪʀꜱᴛ'
            limbo-missing: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴜᴛʜ ꜱᴇʀᴠᴇʀ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ'
            invalid-username: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴜꜱᴇ 3-16 ʟᴇᴛᴛᴇʀꜱ, ɴᴜᴍʙᴇʀꜱ, ᴏʀ ᴜɴᴅᴇʀꜱᴄᴏʀᴇꜱ'
            username-case-mismatch: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴜꜱᴇ ʏᴏᴜʀ ʀᴇɢɪꜱᴛᴇʀᴇᴅ ɴᴀᴍᴇ ᴄᴀꜱɪɴɢ'
            profile-lookup-unavailable: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴄᴄᴏᴜɴᴛ ᴠᴇʀɪꜰɪᴄᴀᴛɪᴏɴ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ'
            totp-prompt: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴇɴᴛᴇʀ ʏᴏᴜʀ ᴄᴏᴅᴇ ᴡɪᴛʜ &f/totp <code>'
            totp-invalid: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ɪɴᴠᴀʟɪᴅ ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛᴏʀ ᴄᴏᴅᴇ'
            totp-success: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴛᴡᴏ-ꜰᴀᴄᴛᴏʀ ᴄᴏᴍᴘʟᴇᴛᴇ'
            totp-setup: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴄᴏᴘʏ ᴛʜɪꜱ ᴜʀɪ, ᴛʜᴇɴ ᴜꜱᴇ &f/2fa confirm <code>&7:'
            totp-enabled: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴛᴡᴏ-ꜰᴀᴄᴛᴏʀ ᴇɴᴀʙʟᴇᴅ'
            totp-disabled: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴛᴡᴏ-ꜰᴀᴄᴛᴏʀ ᴅɪꜱᴀʙʟᴇᴅ'
            password-changed: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴘᴀꜱꜱᴡᴏʀᴅ ᴄʜᴀɴɢᴇᴅ'
            logged-out: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʟᴏɢɢᴇᴅ ᴏᴜᴛ'
            account-deleted: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴀᴄᴄᴏᴜɴᴛ ᴅᴇʟᴇᴛᴇᴅ'
            account-action-failed: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴄᴄᴏᴜɴᴛ ᴀᴄᴛɪᴏɴ ꜰᴀɪʟᴇᴅ'
            protected-account: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴄʀᴇᴅᴇɴᴛɪᴀʟ ᴄʜᴀɴɢᴇꜱ ᴀʀᴇ ᴅɪꜱᴀʙʟᴇᴅ ꜰᴏʀ ᴛʜɪꜱ ᴀᴄᴄᴏᴜɴᴛ'
            totp-enrollment-required: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ꜱᴇᴛ ᴜᴘ 2ꜰᴀ ᴡɪᴛʜ &f/2fa setup <password>'
            offline-not-whitelisted: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛʜɪꜱ ᴏꜰꜰʟɪɴᴇ ᴀᴄᴄᴏᴜɴᴛ ɪꜱ ɴᴏᴛ ᴡʜɪᴛᴇʟɪꜱᴛᴇᴅ'
            session-revoked: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ʏᴏᴜʀ ᴀᴜᴛʜᴇɴᴛɪᴄᴀᴛɪᴏɴ ꜱᴇꜱꜱɪᴏɴ ᴡᴀꜱ ʀᴇᴠᴏᴋᴇᴅ'
            service-busy: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ꜱᴇʀᴠɪᴄᴇ ʙᴜꜱʏ, ᴛʀʏ ᴀɢᴀɪɴ'
            players-only: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴛʜɪꜱ ᴄᴏᴍᴍᴀɴᴅ ɪꜱ ꜰᴏʀ ᴘʟᴀʏᴇʀꜱ'
            usage-register: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/register <password> <password>'
            usage-login: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/login <password>'
            usage-totp: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/totp <code>'
            usage-2fa: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/2fa setup <password> | confirm <code> | disable <password>'
            usage-account: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/account password <current> <new> | logout | delete <password> confirm'
            usage-account-password: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/account password <current> <new>'
            usage-account-delete: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/account delete <password> confirm'
            usage-premium: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/premium <password>'
            usage-cracked: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/cracked <password>'
            premium-enabled: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴘʀᴇᴍɪᴜᴍ ʟᴏɢɪɴ ᴇɴᴀʙʟᴇᴅ. ʀᴇᴄᴏɴɴᴇᴄᴛ ᴡɪᴛʜ ᴍɪᴄʀᴏꜱᴏꜰᴛ'
            cracked-enabled: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ᴏꜰꜰʟɪɴᴇ ʟᴏɢɪɴ ᴇɴᴀʙʟᴇᴅ. ʀᴇᴄᴏɴɴᴇᴄᴛ ᴛᴏ ᴄᴏɴᴛɪɴᴜᴇ'
            premium-already-enabled: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴘʀᴇᴍɪᴜᴍ ʟᴏɢɪɴ ɪꜱ ᴀʟʀᴇᴀᴅʏ ᴇɴᴀʙʟᴇᴅ'
            cracked-already-enabled: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴏꜰꜰʟɪɴᴇ ʟᴏɢɪɴ ɪꜱ ᴀʟʀᴇᴀᴅʏ ᴇɴᴀʙʟᴇᴅ'
            premium-profile-not-found: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ɴᴏ ᴍɪᴄʀᴏꜱᴏꜰᴛ ᴍɪɴᴇᴄʀᴀꜰᴛ ᴘʀᴏꜰɪʟᴇ ᴡᴀꜱ ꜰᴏᴜɴᴅ ꜰᴏʀ ᴛʜɪꜱ ɴᴀᴍᴇ'
            premium-profile-unavailable: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴍᴏᴊᴀɴɢ ᴘʀᴏꜰɪʟᴇ ᴄʜᴇᴄᴋ ɪꜱ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ'
            premium-name-mismatch: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛʜᴇ ᴍɪᴄʀᴏꜱᴏꜰᴛ ᴘʀᴏꜰɪʟᴇ ᴜꜱᴇꜱ ᴅɪꜰꜰᴇʀᴇɴᴛ ɴᴀᴍᴇ ᴄᴀꜱɪɴɢ'
            registration-not-required: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʀᴇɢɪꜱᴛʀᴀᴛɪᴏɴ ɪꜱ ɴᴏᴛ ʀᴇqᴜɪʀᴇᴅ'
            password-not-required: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴘᴀꜱꜱᴡᴏʀᴅ ʟᴏɢɪɴ ɪꜱ ɴᴏᴛ ʀᴇqᴜɪʀᴇᴅ'
            totp-not-required: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴛᴡᴏ-ꜰᴀᴄᴛᴏʀ ɪꜱ ɴᴏᴛ ʀᴇqᴜɪʀᴇᴅ'
            current-password-required: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴛʜɪꜱ ᴀᴄᴛɪᴏɴ ʀᴇQᴜɪʀᴇꜱ ʏᴏᴜʀ ᴄᴜʀʀᴇɴᴛ ᴘᴀꜱꜱᴡᴏʀᴅ'
            password-policy-invalid: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴘᴀꜱꜱᴡᴏʀᴅ ᴅᴏᴇꜱ ɴᴏᴛ ᴍᴇᴇᴛ ᴛʜᴇ ᴄᴏɴꜰɪɢᴜʀᴇᴅ ʟᴇɴɢᴛʜ ʀᴇQᴜɪʀᴇᴍᴇɴᴛꜱ'
            dialog-register-title: 'ᴄʀᴇᴀᴛᴇ ᴀᴄᴄᴏᴜɴᴛ'
            dialog-login-title: 'ᴡᴇʟᴄᴏᴍᴇ ʙᴀᴄᴋ'
            dialog-register-button: 'ᴇɴᴛᴇʀ ʀᴇɢɪꜱᴛᴇʀ ᴄᴏᴍᴍᴀɴᴅ'
            dialog-login-button: 'ᴇɴᴛᴇʀ ʟᴏɢɪɴ ᴄᴏᴍᴍᴀɴᴅ'
            queue-unavailable: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴛʜᴇ ꜱᴇʀᴠᴇʀ Qᴜᴇᴜᴇ ɪꜱ ᴜɴᴀᴠᴀɪʟᴀʙʟᴇ'
            admin-no-permission: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ɴᴏ ᴘᴇʀᴍɪꜱꜱɪᴏɴ'
            admin-usage: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴜꜱᴀɢᴇ &8• &f/opengate lookup <player> | audit <player> [limit] | revoke <player> | recover <player> <new-password>'
            admin-request-failed: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ʀᴇqᴜᴇꜱᴛ ꜰᴀɪʟᴇᴅ'
            admin-account-not-found: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴀᴄᴄᴏᴜɴᴛ ɴᴏᴛ ꜰᴏᴜɴᴅ'
            admin-account-header: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ᴀᴄᴄᴏᴜɴᴛ &8• &f'
            admin-uuid: '&7ᴜᴜɪᴅ &8• &f'
            admin-identity: '&7ɪᴅᴇɴᴛɪᴛʏ &8• &f'
            admin-created: '&7ᴄʀᴇᴀᴛᴇᴅ &8• &f'
            admin-totp: '&7ᴛᴏᴛᴘ &8• &f'
            admin-enabled: 'ᴇɴᴀʙʟᴇᴅ'
            admin-disabled: 'ᴅɪꜱᴀʙʟᴇᴅ'
            admin-audit-limit: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ʟɪᴍɪᴛ ᴍᴜꜱᴛ ʙᴇ 1-100'
            admin-audit-header: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &7ʀᴇᴄᴇɴᴛ ᴇᴠᴇɴᴛꜱ &8• &f'
            admin-revocation-failed: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ꜱᴏᴍᴇ ꜱᴇꜱꜱɪᴏɴꜱ ᴄᴏᴜʟᴅ ɴᴏᴛ ʙᴇ ᴄʟᴏꜱᴇᴅ'
            admin-revoked: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ʀᴇᴠᴏᴋᴇᴅ ꜱᴇꜱꜱɪᴏɴꜱ ꜰᴏʀ &f'
            admin-recovered: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &a✓ &7ʀᴇꜱᴇᴛ ᴘᴀꜱꜱᴡᴏʀᴅ ᴀɴᴅ ʀᴇᴠᴏᴋᴇᴅ ꜱᴇꜱꜱɪᴏɴꜱ ꜰᴏʀ &f'
            admin-recovery-offline-only: '&8[&fᴏᴘᴇɴɢᴀᴛᴇ&8] &c✕ &7ᴘᴀꜱꜱᴡᴏʀᴅ ʀᴇᴄᴏᴠᴇʀʏ ɪꜱ ᴏɴʟʏ ꜰᴏʀ ᴏꜰꜰʟɪɴᴇ ᴀᴄᴄᴏᴜɴᴛꜱ'
            """;

    private final Map<String, String> messages;
    private final Map<String, Map<String, String>> localized;

    private OpenGateMessages(Map<String, String> messages, Map<String, Map<String, String>> localized) {
        this.messages = Map.copyOf(messages);
        this.localized = Map.copyOf(localized);
    }

    public static OpenGateMessages load(Path dataDirectory) {
        var file = dataDirectory.resolve("messages.yml");
        var legacy = dataDirectory.resolve("messages.properties");
        if (java.nio.file.Files.notExists(file) && java.nio.file.Files.exists(legacy)) {
            YamlConfiguration.migrateMessages(file, OpenGateConfig.loadProperties(legacy), DEFAULTS);
        } else {
            OpenGateConfig.createDefault(file, DEFAULTS);
        }
        var base = new LinkedHashMap<>(defaults());
        base.putAll(YamlConfiguration.loadFlat(file));
        return new OpenGateMessages(base, loadLocalized(dataDirectory, base));
    }

    private static Map<String, String> defaults() {
        return YamlConfiguration.loadFlat(DEFAULTS);
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
        try (var files = java.nio.file.Files.newDirectoryStream(directory, "messages_*.yml")) {
            for (var file : files) {
                var name = file.getFileName().toString();
                var tag = name.substring("messages_".length(), name.length() - ".yml".length())
                        .replace('_', '-').toLowerCase(Locale.ROOT);
                if (tag.isBlank() || Locale.forLanguageTag(tag).getLanguage().isBlank()) {
                    throw new IllegalArgumentException("invalid message locale filename: " + name);
                }
                var translated = new LinkedHashMap<>(base);
                var overrides = YamlConfiguration.loadFlat(file);
                for (var key : overrides.keySet()) {
                    if (!base.containsKey(key)) throw new IllegalArgumentException(
                            "unknown message key " + key + " in " + name);
                    translated.put(key, overrides.get(key));
                }
                bundles.put(tag, Map.copyOf(translated));
            }
            return Map.copyOf(bundles);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("could not load localized OpenGate messages", exception);
        }
    }
}
