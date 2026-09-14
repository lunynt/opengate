package dev.lunynt.opengate.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import dev.lunynt.opengate.OpenGate;
import dev.lunynt.opengate.identity.FloodgateApiIdentity;
import dev.lunynt.opengate.identity.FloodgateIdentity;
import java.nio.file.Path;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

@Plugin(
        id = "opengate",
        name = "OpenGate",
        version = "0.1.0",
        description = "Authentication gateway for Paper and Velocity",
        dependencies = {
            @Dependency(id = "floodgate", optional = true),
            @Dependency(id = "ajqueue", optional = true)
        })
public final class OpenGateVelocityPlugin {
    static final net.kyori.adventure.key.Key SESSION_COOKIE = net.kyori.adventure.key.Key.key("opengate", "session");
    private final Logger logger;
    private final Path dataDirectory;
    private final ProxyServer server;
    private OpenGate openGate;
    private FloodgateIdentity floodgate = FloodgateIdentity.unavailable();
    private boolean floodgateEnabled;
    private dev.lunynt.opengate.integration.AjQueueIntegration ajQueue;
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, com.velocitypowered.api.scheduler.ScheduledTask>
            notificationTasks = new java.util.concurrent.ConcurrentHashMap<>();

    @Inject
    public OpenGateVelocityPlugin(Logger logger, @DataDirectory Path dataDirectory, ProxyServer server) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.server = server;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        openGate = OpenGate.create(dataDirectory);
        openGate.sessions().onInvalidated(connectionId -> server.getPlayer(connectionId)
                .ifPresent(player -> player.disconnect(message(player, "session-revoked"))));
        if (openGate.config().ajQueue().enabled()) {
            try {
                if (!server.getPluginManager().isLoaded("ajqueue")) {
                    throw new IllegalStateException("ajQueue integration is enabled but ajQueue is not loaded");
                }
                ajQueue = new dev.lunynt.opengate.integration.AjQueueIntegration();
            } catch (RuntimeException | LinkageError failure) {
                closeAfterStartupFailure(failure);
                throw failure;
            }
        }
        if (server.getPluginManager().isLoaded("floodgate")) {
            try {
                floodgate = new FloodgateApiIdentity();
                floodgateEnabled = true;
                logger.info("Floodgate integration enabled");
            } catch (LinkageError | RuntimeException exception) {
                logger.warn("Floodgate API unavailable; Bedrock authentication will fail closed");
            }
        }
        server.getEventManager().register(this, new VelocityAuthenticationListener(this));
        var commandManager = server.getCommandManager();
        commandManager.register(
                commandManager.metaBuilder("login")
                        .aliases(openGate.config().commands().aliases("login").toArray(String[]::new))
                        .plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.LOGIN));
        commandManager.register(
                commandManager.metaBuilder("register")
                        .aliases(openGate.config().commands().aliases("register").toArray(String[]::new))
                        .plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.REGISTER));
        commandManager.register(
                commandManager.metaBuilder("totp")
                        .aliases(openGate.config().commands().aliases("totp").toArray(String[]::new))
                        .plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.TOTP));
        commandManager.register(
                commandManager.metaBuilder("2fa")
                        .aliases(openGate.config().commands().aliases("2fa").toArray(String[]::new))
                        .plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.MANAGE_TOTP));
        commandManager.register(
                commandManager.metaBuilder("account")
                        .aliases(openGate.config().commands().aliases("account").toArray(String[]::new))
                        .plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.ACCOUNT));
        commandManager.register(
                commandManager.metaBuilder("premium")
                        .aliases(openGate.config().commands().aliases("premium").toArray(String[]::new))
                        .plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.PREMIUM));
        commandManager.register(
                commandManager.metaBuilder("cracked")
                        .aliases(openGate.config().commands().aliases("cracked").toArray(String[]::new))
                        .plugin(this).build(),
                new VelocityAuthenticationCommand(this, VelocityAuthenticationCommand.Type.CRACKED));
        commandManager.register(
                commandManager.metaBuilder("opengate")
                        .aliases(openGate.config().commands().aliases("opengate").toArray(String[]::new))
                        .plugin(this).build(),
                new VelocityAdminCommand(this));
        dev.lunynt.opengate.StartupLog.lines(
                "Velocity", "0.1.0", openGate.config(), floodgateEnabled, true)
                .forEach(logger::info);
    }

    public OpenGate openGate() {
        return openGate;
    }

    public dev.lunynt.opengate.api.OpenGateApi api() {
        return openGate.api();
    }

    ProxyServer server() {
        return server;
    }

    FloodgateIdentity floodgate() {
        return floodgate;
    }

    void issueSessionCookie(com.velocitypowered.api.proxy.Player player, java.util.UUID accountId) {
        openGate.cookieSessions().issue(accountId).thenAccept(token -> token.ifPresent(value -> {
            if (player.isActive()) player.storeCookie(SESSION_COOKIE, value);
        }));
    }

    java.util.concurrent.CompletableFuture<Void> clearSessionCookie(
            com.velocitypowered.api.proxy.Player player, java.util.UUID accountId) {
        if (player.isActive()) player.storeCookie(SESSION_COOKIE, new byte[0]);
        return openGate.cookieSessions().revokeAll(accountId);
    }

    void connectToLobby(com.velocitypowered.api.proxy.Player player) {
        if (ajQueue != null) {
            try {
                if (ajQueue.enqueue(player.getUniqueId(), openGate.config().ajQueue().target())) return;
            } catch (RuntimeException | LinkageError failure) {
                logger.error("ajQueue rejected authenticated player routing", failure);
            }
            player.disconnect(message(player, "queue-unavailable"));
            return;
        }
        var destination = openGate.config().lobbyServers().stream()
                .map(server::getServer)
                .flatMap(java.util.Optional::stream)
                .findFirst();
        if (destination.isEmpty()) {
            logger.error("None of the configured lobby servers exist: {}", openGate.config().lobbyServers());
            player.disconnect(message(player, "lobby-missing"));
            return;
        }
        player.createConnectionRequest(destination.orElseThrow()).fireAndForget();
    }

    private void closeAfterStartupFailure(Throwable failure) {
        try {
            openGate.close();
        } catch (RuntimeException | Error cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        } finally {
            openGate = null;
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        notificationTasks.values().forEach(com.velocitypowered.api.scheduler.ScheduledTask::cancel);
        notificationTasks.clear();
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

    Component message(com.velocitypowered.api.proxy.Player player, String key) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(openGate.messages().get(player.getEffectiveLocale(), key));
    }

    void startAuthenticationReminder(
            com.velocitypowered.api.proxy.Player player, String messageKey, String subtitleKey) {
        stopAuthenticationReminder(player.getUniqueId());
        showNotification(player, messageKey, "prompt-title", subtitleKey);
        var task = server.getScheduler()
                .buildTask(this, () -> {
                    if (!player.isActive() || openGate.sessions().find(player.getUniqueId())
                            .map(session -> session.state() == dev.lunynt.opengate.auth.AuthenticationState.RELEASED)
                            .orElse(true)) {
                        stopAuthenticationReminder(player.getUniqueId());
                        return;
                    }
                    showNotification(player, messageKey, "prompt-title", subtitleKey);
                })
                .repeat(openGate.config().notifications().reminderInterval())
                .schedule();
        notificationTasks.put(player.getUniqueId(), task);
    }

    void showAuthenticationSuccess(
            com.velocitypowered.api.proxy.Player player, String messageKey, String subtitleKey) {
        stopAuthenticationReminder(player.getUniqueId());
        showNotification(player, messageKey, "login-title", subtitleKey);
    }

    void stopAuthenticationReminder(java.util.UUID playerId) {
        var task = notificationTasks.remove(playerId);
        if (task != null) task.cancel();
    }

    private void showNotification(com.velocitypowered.api.proxy.Player player,
            String messageKey, String titleKey, String subtitleKey) {
        var settings = openGate.config().notifications();
        var body = message(player, messageKey);
        if (settings.chatEnabled()) player.sendMessage(body);
        if (settings.actionBarEnabled()) player.sendActionBar(body);
        if (!settings.titlesEnabled()) return;
        var serializer = LegacyComponentSerializer.legacyAmpersand();
        var locale = player.getEffectiveLocale();
        player.showTitle(net.kyori.adventure.title.Title.title(
                serializer.deserialize(openGate.messages().get(locale, titleKey)),
                serializer.deserialize(openGate.messages().get(locale, subtitleKey)),
                net.kyori.adventure.title.Title.Times.times(
                        settings.titleFadeIn(),
                        settings.titleStay(),
                        settings.titleFadeOut())));
    }
}
