package dev.lunynt.opengate;

import dev.lunynt.opengate.config.OpenGateConfig;
import java.util.List;
import java.util.Locale;

public final class StartupLog {
    private StartupLog() {
    }

    public static List<String> lines(
            String platform, String version, OpenGateConfig config, boolean floodgate, boolean proxy) {
        var database = config.database().type().name().toLowerCase(Locale.ROOT);
        var routing = proxy
                ? config.limboServer() + " -> " + String.join(", ", config.lobbyServers())
                : "standalone";
        return List.of(
                "┌─ ᴏᴘᴇɴɢᴀᴛᴇ " + version,
                "│ Platform   " + platform,
                "│ Database   " + database,
                "│ Routing    " + routing,
                "│ Floodgate  " + state(floodgate),
                "│ Redis      " + state(config.redis().enabled()),
                "│ Sessions   " + state(config.cookieSessionsEnabled()),
                "└─ Ready");
    }

    private static String state(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }
}
