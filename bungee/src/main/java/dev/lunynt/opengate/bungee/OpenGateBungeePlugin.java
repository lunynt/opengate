package dev.lunynt.opengate.bungee;

import dev.lunynt.opengate.OpenGate;
import dev.lunynt.opengate.identity.FloodgateApiIdentity;
import dev.lunynt.opengate.identity.FloodgateIdentity;
import dev.lunynt.opengate.auth.AuthenticationState;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

public final class OpenGateBungeePlugin extends Plugin {
    private OpenGate openGate;
    private FloodgateIdentity floodgate = FloodgateIdentity.unavailable();

    @Override
    public void onEnable() {
        openGate = OpenGate.create(getDataFolder().toPath());
        if (getProxy().getPluginManager().getPlugin("floodgate") != null) {
            try {
                floodgate = new FloodgateApiIdentity();
                getLogger().info("Floodgate integration enabled");
            } catch (LinkageError | RuntimeException exception) {
                getLogger().warning("Floodgate API unavailable; Bedrock authentication will fail closed");
            }
        }
        var plugins = getProxy().getPluginManager();
        plugins.registerListener(this, new BungeeAuthenticationListener(this));
        plugins.registerCommand(this, new BungeeAuthenticationCommand(this, "login", "l"));
        plugins.registerCommand(this, new BungeeAuthenticationCommand(this, "register", "reg"));
        plugins.registerCommand(this, new BungeeAuthenticationCommand(this, "totp"));
        plugins.registerCommand(this, new BungeeAuthenticationCommand(this, "2fa"));
        plugins.registerCommand(this, new BungeeAuthenticationCommand(this, "account"));
        plugins.registerCommand(this, new BungeeAdminCommand(this));
        getLogger().info("OpenGate authentication engine enabled on BungeeCord");
    }

    @Override
    public void onDisable() {
        if (openGate != null) openGate.close();
    }

    OpenGate openGate() {
        return openGate;
    }

    void connectToLobby(ProxiedPlayer player) {
        openGate.config().lobbyServers().stream()
                .map(name -> getProxy().getServerInfo(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .ifPresent(player::connect);
    }

    void scheduleAuthenticationTimeout(ProxiedPlayer player) {
        getProxy().getScheduler().schedule(this, () -> {
            if (player.isConnected() && openGate.sessions()
                    .find(player.getUniqueId())
                    .map(session -> session.state() != AuthenticationState.RELEASED)
                    .orElse(true)) {
                player.disconnect(message("authentication-timeout"));
            }
        }, openGate.config().authenticationTimeout().toMillis(), TimeUnit.MILLISECONDS);
    }

    BaseComponent message(String key) {
        return TextComponent.fromLegacy(openGate.messages().get(key).replace('&', '§'));
    }

    ProxyServer proxy() {
        return getProxy();
    }

    FloodgateIdentity floodgate() {
        return floodgate;
    }
}
