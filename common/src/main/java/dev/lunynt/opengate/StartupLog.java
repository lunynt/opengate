package dev.lunynt.opengate;

import dev.lunynt.opengate.config.OpenGateConfig;
import java.util.List;
import java.util.Locale;

public final class StartupLog {
    private static final String AQUA = "\u001B[96m";
    private static final String WHITE = "\u001B[97m";
    private static final String GRAY = "\u001B[90m";
    private static final String GREEN = "\u001B[92m";
    private static final String YELLOW = "\u001B[93m";
    private static final String BOLD = "\u001B[1m";
    private static final String RESET = "\u001B[0m";

    private StartupLog() {
    }

    public static List<String> lines(
            String platform, String version, OpenGateConfig config, boolean floodgate, boolean proxy) {
        var database = config.database().type().name().toLowerCase(Locale.ROOT);
        var routing = proxy
                ? config.limboServer() + " -> " + String.join(", ", config.lobbyServers())
                : "standalone";
        if (!config.consoleColors()) {
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
        return List.of(
                AQUA + BOLD + "┌─ ᴏᴘᴇɴɢᴀᴛᴇ " + WHITE + version + RESET,
                row("Platform", WHITE + platform),
                row("Database", WHITE + database),
                row("Routing", WHITE + routing),
                row("Floodgate", coloredState(floodgate)),
                row("Redis", coloredState(config.redis().enabled())),
                row("Sessions", coloredState(config.cookieSessionsEnabled())),
                AQUA + BOLD + "└─ " + GREEN + "Ready" + RESET);
    }

    private static String state(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    private static String coloredState(boolean enabled) {
        return (enabled ? GREEN : YELLOW) + state(enabled);
    }

    private static String row(String label, String value) {
        return AQUA + "│ " + GRAY + String.format("%-10s", label) + value + RESET;
    }
}
