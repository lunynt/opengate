package dev.lunynt.opengate.bungee;

import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.ResolvedIdentity;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.PreLoginEvent;
import net.md_5.bungee.api.event.ServerConnectEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

final class BungeeAuthenticationListener implements Listener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("login", "l", "register", "reg", "totp", "2fa");

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
                switch (plugin.openGate().identities().resolve(event.getConnection().getName())) {
                    case ONLINE -> event.getConnection().setOnlineMode(true);
                    case OFFLINE -> event.getConnection().setOnlineMode(false);
                    case DENY_INVALID_USERNAME -> deny(event, "invalid-username");
                    case DENY_CASE_MISMATCH -> deny(event, "username-case-mismatch");
                    case DENY_LOOKUP_UNAVAILABLE -> deny(event, "profile-lookup-unavailable");
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
                if (player.isConnected()) player.disconnect(plugin.message("profile-lookup-unavailable"));
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
            event.getPlayer().disconnect(plugin.message("limbo-missing"));
            return;
        }
        event.setTarget(limbo);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(ChatEvent event) {
        if (!(event.getSender() instanceof ProxiedPlayer player) || !isBlocked(player.getUniqueId())) return;
        var value = event.getMessage();
        if (value.startsWith("/")) {
            var command = value.substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
            if (ALLOWED_COMMANDS.contains(command)) return;
        }
        event.setCancelled(true);
        player.sendMessage(plugin.message("authenticate-first"));
    }

    private void initialize(ProxiedPlayer player) {
        if (!player.isConnected()) return;
        var playerId = player.getUniqueId();
        var address = address(player);
        plugin.openGate().sessions().close(playerId);
        var session = plugin.openGate().sessions().open(playerId);
        var account = plugin.openGate().accounts().find(playerId);
        var trusted = account.filter(value -> plugin.openGate().accounts()
                        .hasTrustedSession(value, address, plugin.openGate().config().trustedSessionLifetime()))
                .isPresent();
        session.resolve(new ResolvedIdentity(
                player.getName(),
                playerId,
                player.getPendingConnection().isOnlineMode() ? IdentityType.PREMIUM : IdentityType.OFFLINE,
                account.isPresent(),
                account.map(value -> value.passwordHash() != null).orElse(false),
                account.map(value -> value.totpSecret() != null).orElse(false),
                trusted));

        if (session.state() == AuthenticationState.AUTHENTICATED) {
            session.release();
            player.sendMessage(plugin.message("automatic-login"));
            plugin.connectToLobby(player);
        } else if (session.state() == AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(plugin.message("register-prompt"));
        } else {
            player.sendMessage(plugin.message("login-prompt"));
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
