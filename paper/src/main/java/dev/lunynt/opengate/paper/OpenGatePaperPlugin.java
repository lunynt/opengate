package dev.lunynt.opengate.paper;

import dev.lunynt.opengate.OpenGate;
import dev.lunynt.opengate.identity.FloodgateApiIdentity;
import dev.lunynt.opengate.identity.FloodgateIdentity;
import org.bukkit.plugin.java.JavaPlugin;

public final class OpenGatePaperPlugin extends JavaPlugin {
    private OpenGate openGate;
    private FloodgateIdentity floodgate = FloodgateIdentity.unavailable();

    @Override
    public void onEnable() {
        openGate = OpenGate.create(getDataFolder().toPath());
        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            try {
                floodgate = new FloodgateApiIdentity();
                getLogger().info("Floodgate integration enabled");
            } catch (LinkageError | RuntimeException exception) {
                getLogger().warning("Floodgate API unavailable; Bedrock authentication will fail closed");
            }
        }
        getServer().getPluginManager().registerEvents(new PaperAuthenticationListener(this), this);
        var commands = new PaperAuthenticationCommand(this);
        java.util.Objects.requireNonNull(getCommand("login")).setExecutor(commands);
        java.util.Objects.requireNonNull(getCommand("register")).setExecutor(commands);
        java.util.Objects.requireNonNull(getCommand("totp")).setExecutor(commands);
        java.util.Objects.requireNonNull(getCommand("2fa")).setExecutor(commands);
        java.util.Objects.requireNonNull(getCommand("account")).setExecutor(commands);
        java.util.Objects.requireNonNull(getCommand("opengate")).setExecutor(new PaperAdminCommand(this));
        getLogger().info("OpenGate authentication engine enabled on Paper");
    }

    @Override
    public void onDisable() {
        if (openGate != null) {
            openGate.close();
        }
    }

    public OpenGate openGate() {
        return openGate;
    }

    FloodgateIdentity floodgate() {
        return floodgate;
    }
}
