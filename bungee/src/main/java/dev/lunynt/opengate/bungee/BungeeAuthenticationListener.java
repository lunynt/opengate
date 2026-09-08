package dev.lunynt.opengate.bungee;

import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.ResolvedIdentity;
import java.util.UUID;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.event.TabCompleteEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

final class BungeeAuthenticationListener implements Listener {
    private final OpenGateBungeePlugin plugin;

    BungeeAuthenticationListener(OpenGateBungeePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(PreLoginEvent event) {
        if (event.isCancelled()) return;
        event.registerIntent(plugin);
        plugin.proxy().getScheduler().runAsync(plugin, () -> {
            try {
                if (plugin.floodgate().isUsername(event.getConnection().getName())) {
                    event.getConnection().setOnlineMode(false);
                    return;
                }
                switch (plugin.openGate().identities().resolve(event.getConnection().getName())) {
                    case ONLINE -> event.getConnection().setOnlineMode(true);
                    case OFFLINE -> event.getConnection().setOnlineMode(false);
                    case DENY_INVALID_USERNAME -> deny(event, "invalid-username");
                    case DENY_CASE_MISMATCH -> deny(event, "username-case-mismatch");
                    case DENY_LOOKUP_UNAVAILABLE -> deny(event, "profile-lookup-unavailable");
                    case DENY_OFFLINE_NOT_WHITELISTED -> deny(event, "offline-not-whitelisted");
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Could not resolve identity for "
                        + event.getConnection().getName() + ": " + exception.getMessage());
                deny(event, "profile-lookup-unavailable");
            } finally {
                event.completeIntent(plugin);
            }
        });
    }

    @EventHandler
    public void onPostLogin(PostLoginEvent event) {
        var player = event.getPlayer();
        plugin.scheduleAuthenticationTimeout(player);
        plugin.proxy().getScheduler().runAsync(plugin, () -> {
            try {
                initialize(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning(
                        "Could not load account for " + player.getName() + ": " + exception.getMessage());
                if (player.isConnected()) player.disconnect(plugin.message(player, "profile-lookup-unavailable"));
            }
        });
    }

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        plugin.openGate().sessions().close(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerConnect(ServerConnectEvent event) {
        if (!isBlocked(event.getPlayer().getUniqueId())) return;
        var limbo = plugin.proxy().getServerInfo(plugin.openGate().config().limboServer());
        if (limbo == null) {
            event.setCancelled(true);
            event.getPlayer().disconnect(plugin.message(event.getPlayer(), "limbo-missing"));
            return;
        }
        event.setTarget(limbo);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer player) || !isBlocked(player.getUniqueId())) return;
        var value = event.getMessage();
        if (value.startsWith("/")) {
            var command = value.substring(1).split(" ", 2)[0];
            if (plugin.openGate().config().commands().isAuthenticationLabel(command)) return;
        }
        event.setCancelled(true);
            player.sendMessage(plugin.message(player, "authenticate-first"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTabComplete(TabCompleteEvent event) {
        if (event.getSender() instanceof ProxiedPlayer player && isBlocked(player.getUniqueId())) {
            event.setCancelled(true);
            event.getSuggestions().clear();
        }
    }

    private void initialize(ProxiedPlayer player) {
        if (!player.isConnected()) return;
        var playerId = player.getUniqueId();
        var address = address(player);
        plugin.openGate().sessions().close(playerId);
        var session = plugin.openGate().sessions().open(playerId);
        var translatedId = plugin.openGate().identityIds().translate(playerId);
        var account = plugin.openGate().accounts().find(player.getName())
                .or(() -> plugin.openGate().accounts().find(translatedId));
        var accountId = account.map(dev.lunynt.opengate.account.Account::playerId).orElse(translatedId);
        byte[] cookie;
        try {
            cookie = player.retrieveCookie(OpenGateBungeePlugin.SESSION_COOKIE)
                    .get(3, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            cookie = null;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            cookie = null;
        }
        var cookieValid = account.isPresent() && plugin.openGate().cookieSessions().verify(accountId, cookie).join();
        var identityType = plugin.floodgate().isPlayer(playerId)
                ? IdentityType.FLOODGATE
                : player.getPendingConnection().isOnlineMode() ? IdentityType.PREMIUM : IdentityType.OFFLINE;
        var requirements = plugin.openGate().config().authenticationRequirements()
                .forPermissions(player::hasPermission);
        session.resolve(new ResolvedIdentity(
                player.getName(),
                accountId,
                identityType,
                account.isPresent(),
                account.map(value -> value.passwordHash() != null
                        && (identityType == IdentityType.OFFLINE || requirements.passwordRequired())).orElse(false),
                account.map(value -> value.totpSecret() != null).orElse(false)),
                requirements);

        if (cookieValid && session.state() != AuthenticationState.AWAITING_REGISTRATION
                && session.state() != AuthenticationState.AWAITING_TOTP_ENROLLMENT) {
            session.resumeWithCookie();
        }

        if (session.state() == AuthenticationState.AUTHENTICATED) {
            session.release();
            player.sendMessage(plugin.message(player, "automatic-login"));
            plugin.connectToLobby(player);
        } else if (session.state() == AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(plugin.message(player, "register-prompt"));
            plugin.showAuthenticationDialog(player, true);
        } else if (session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT) {
            player.sendMessage(plugin.message(player, "totp-enrollment-required"));
        } else {
            player.sendMessage(plugin.message(player, "login-prompt"));
            plugin.showAuthenticationDialog(player, false);
        }
    }

    private boolean isBlocked(UUID playerId) {
        return plugin.openGate().sessions().find(playerId)
                .map(session -> session.state() != AuthenticationState.RELEASED)
                .orElse(true);
    }

    private void deny(PreLoginEvent event, String message) {
        event.setCancelled(true);
        event.setReason(plugin.message(message));
    }

    static String address(ProxiedPlayer player) {
        return player.getSocketAddress() instanceof java.net.InetSocketAddress socket
                ? socket.getAddress().getHostAddress()
                : player.getSocketAddress().toString();
    }
}
