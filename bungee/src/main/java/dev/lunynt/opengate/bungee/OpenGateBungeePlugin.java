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
    static final String SESSION_COOKIE = "opengate:session";
    private OpenGate openGate;
    private FloodgateIdentity floodgate = FloodgateIdentity.unavailable();
    private dev.lunynt.opengate.integration.AjQueueIntegration ajQueue;

    @Override
    public void onEnable() {
        openGate = OpenGate.create(getDataFolder().toPath());
        openGate.sessions().onInvalidated(connectionId -> {
            var player = getProxy().getPlayer(connectionId);
            if (player != null) player.disconnect(message(player, "session-revoked"));
        });
        if (openGate.config().ajQueue().enabled()) {
            try {
                if (getProxy().getPluginManager().getPlugin("ajQueue") == null) {
                    throw new IllegalStateException("ajQueue integration is enabled but ajQueue is not loaded");
                }
                ajQueue = new dev.lunynt.opengate.integration.AjQueueIntegration();
            } catch (RuntimeException | LinkageError failure) {
                closeAfterStartupFailure(failure);
                throw failure;
            }
        }
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
        plugins.registerCommand(this, authenticationCommand("login"));
        plugins.registerCommand(this, authenticationCommand("register"));
        plugins.registerCommand(this, authenticationCommand("totp"));
        plugins.registerCommand(this, authenticationCommand("2fa"));
        plugins.registerCommand(this, authenticationCommand("account"));
        plugins.registerCommand(this, authenticationCommand("premium"));
        plugins.registerCommand(this, authenticationCommand("cracked"));
        plugins.registerCommand(this, new BungeeAdminCommand(
                this, openGate.config().commands().aliases("opengate").toArray(String[]::new)));
        getLogger().info("OpenGate authentication engine enabled on BungeeCord");
    }

    private BungeeAuthenticationCommand authenticationCommand(String name) {
        return new BungeeAuthenticationCommand(
                this, name, openGate.config().commands().aliases(name).toArray(String[]::new));
    }

    @Override
    public void onDisable() {
        if (openGate != null) openGate.close();
    }

    OpenGate openGate() {
        return openGate;
    }

    public dev.lunynt.opengate.api.OpenGateApi api() {
        return openGate.api();
    }

    void connectToLobby(ProxiedPlayer player) {
        if (ajQueue != null) {
            try {
                if (ajQueue.enqueue(player.getUniqueId(), openGate.config().ajQueue().target())) return;
            } catch (RuntimeException | LinkageError failure) {
                getLogger().log(java.util.logging.Level.SEVERE,
                        "ajQueue rejected authenticated player routing", failure);
            }
            player.disconnect(message(player, "queue-unavailable"));
            return;
        }
        openGate.config().lobbyServers().stream()
                .map(name -> getProxy().getServerInfo(name))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .ifPresent(player::connect);
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

    BaseComponent message(ProxiedPlayer player, String key) {
        return TextComponent.fromLegacy(openGate.messages().get(player.getLocale(), key).replace('&', '§'));
    }

    ProxyServer proxy() {
        return getProxy();
    }

    FloodgateIdentity floodgate() {
        return floodgate;
    }

    void issueSessionCookie(ProxiedPlayer player, java.util.UUID accountId) {
        openGate.cookieSessions().issue(accountId).thenAccept(token -> token.ifPresent(value -> {
            if (player.isConnected()) player.storeCookie(SESSION_COOKIE, value);
        }));
    }

    java.util.concurrent.CompletableFuture<Void> clearSessionCookie(
            ProxiedPlayer player, java.util.UUID accountId) {
        if (player.isConnected()) player.storeCookie(SESSION_COOKIE, new byte[0]);
        return openGate.cookieSessions().revokeAll(accountId);
    }

    void showAuthenticationDialog(ProxiedPlayer player, boolean registration) {
        if (!openGate.config().minecraftDialogsEnabled()) return;
        var command = registration ? "/register " : "/login ";
        var title = new TextComponent(openGate.messages().get(
                player.getLocale(), registration ? "dialog-register-title" : "dialog-login-title"));
        var button = new net.md_5.bungee.api.dialog.action.ActionButton(
                new TextComponent(openGate.messages().get(
                        player.getLocale(), registration ? "dialog-register-button" : "dialog-login-button")),
                new net.md_5.bungee.api.dialog.action.StaticAction(new net.md_5.bungee.api.chat.ClickEvent(
                        net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, command)));
        try {
            player.showDialog(new net.md_5.bungee.api.dialog.NoticeDialog(
                    new net.md_5.bungee.api.dialog.DialogBase(title), button));
        } catch (IllegalStateException | UnsupportedOperationException ignored) {
        }
    }
}
