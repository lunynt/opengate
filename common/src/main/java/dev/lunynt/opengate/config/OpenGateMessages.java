package dev.lunynt.opengate.config;

import java.nio.file.Path;
import java.util.Map;
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
