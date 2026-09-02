package dev.lunynt.opengate.paper;

import dev.lunynt.opengate.OpenGate;
import java.io.IOException;
import java.nio.file.Files;
import org.bukkit.plugin.java.JavaPlugin;

public final class OpenGatePaperPlugin extends JavaPlugin {
    private OpenGate openGate;

    @Override
    public void onEnable() {
        try {
            Files.createDirectories(getDataFolder().toPath());
        } catch (IOException exception) {
            throw new IllegalStateException("could not create OpenGate data directory", exception);
        }
        openGate = OpenGate.create(getDataFolder().toPath());
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
}
