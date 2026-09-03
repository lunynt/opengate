package dev.lunynt.opengate.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lunynt.opengate.OpenGate;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

@Plugin(
        id = "opengate",
        name = "OpenGate",
        version = "0.1.0-SNAPSHOT",
        description = "Authentication gateway for Paper and Velocity")
public final class OpenGateVelocityPlugin {
    private final Logger logger;
    private final Path dataDirectory;
    private final ProxyServer server;
    private OpenGate openGate;

    @Inject
    public OpenGateVelocityPlugin(Logger logger, @DataDirectory Path dataDirectory, ProxyServer server) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.server = server;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        openGate = OpenGate.create(dataDirectory);
        server.getEventManager().register(this, new VelocityAuthenticationListener(this));
        var commandManager = server.getCommandManager();
        commandManager.register(
                commandManager.metaBuilder("login").aliases("l").plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.LOGIN));
        commandManager.register(
                commandManager.metaBuilder("register").aliases("reg").plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.REGISTER));
        commandManager.register(
                commandManager.metaBuilder("totp").plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.TOTP));
        commandManager.register(
                commandManager.metaBuilder("2fa").plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.MANAGE_TOTP));
        commandManager.register(
                commandManager.metaBuilder("account").plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.ACCOUNT));
        commandManager.register(
                commandManager.metaBuilder("opengate").plugin(this).build(),
                new VelocityAdminCommand(this));
        logger.info("OpenGate authentication engine enabled on Velocity");
    }

    public OpenGate openGate() {
        return openGate;
    }

    ProxyServer server() {
        return server;
    }

    void connectToLobby(com.velocitypowered.api.proxy.Player player) {
        openGate.config().lobbyServers().stream()
                .map(server::getServer)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .ifPresent(candidate -> player.createConnectionRequest(candidate).fireAndForget());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (openGate != null) {
            openGate.close();
        }
    }

    void scheduleAuthenticationTimeout(com.velocitypowered.api.proxy.Player player) {
        server.getScheduler()
                .buildTask(this, () -> {
                    if (player.isActive()
                            && openGate.sessions()
                                    .find(player.getUniqueId())
                                    .map(session -> session.state()
                                            != dev.lunynt.opengate.auth.AuthenticationState.RELEASED)
                                    .orElse(true)) {
                        player.disconnect(message("authentication-timeout"));
                    }
                })
                .delay(openGate.config().authenticationTimeout())
                .schedule();
    }

    Component message(String key) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(openGate.messages().get(key));
    }
}
