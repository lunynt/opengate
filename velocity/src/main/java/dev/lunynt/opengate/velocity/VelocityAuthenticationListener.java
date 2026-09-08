package dev.lunynt.opengate.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.TabCompleteEvent;
import com.velocitypowered.api.event.player.CookieReceiveEvent;
import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.ResolvedIdentity;
import net.kyori.adventure.text.Component;

final class VelocityAuthenticationListener {
    private final java.util.concurrent.ConcurrentMap<java.util.UUID, java.util.concurrent.CompletableFuture<byte[]>>
            pendingCookies = new java.util.concurrent.ConcurrentHashMap<>();
    private final OpenGateVelocityPlugin plugin;

    VelocityAuthenticationListener(OpenGateVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Subscribe(priority = -100)
    public EventTask onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }
        return EventTask.async(() -> {
            try {
                if (plugin.floodgate().isUsername(event.getUsername())) {
                    event.setResult(PreLoginEvent.PreLoginComponentResult.forceOfflineMode());
                    return;
                }
                var decision = plugin.openGate().identities().resolve(event.getUsername());
                event.setResult(switch (decision) {
                    case ONLINE -> PreLoginEvent.PreLoginComponentResult.forceOnlineMode();
                    case OFFLINE -> PreLoginEvent.PreLoginComponentResult.forceOfflineMode();
                    case DENY_INVALID_USERNAME ->
                            PreLoginEvent.PreLoginComponentResult.denied(message("invalid-username"));
                    case DENY_CASE_MISMATCH ->
                            PreLoginEvent.PreLoginComponentResult.denied(message("username-case-mismatch"));
                    case DENY_LOOKUP_UNAVAILABLE ->
                            PreLoginEvent.PreLoginComponentResult.denied(message("profile-lookup-unavailable"));
                    case DENY_OFFLINE_NOT_WHITELISTED ->
                            PreLoginEvent.PreLoginComponentResult.denied(message("offline-not-whitelisted"));
                });
            } catch (RuntimeException exception) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(message("profile-lookup-unavailable")));
            }
        });
    }

    @Subscribe
    public EventTask onPostLogin(PostLoginEvent event) {
        var player = event.getPlayer();
        var response = new java.util.concurrent.CompletableFuture<byte[]>();
        pendingCookies.put(player.getUniqueId(), response);
        player.requestCookie(OpenGateVelocityPlugin.SESSION_COOKIE);
        plugin.scheduleAuthenticationTimeout(player);
        return EventTask.async(() -> {
            byte[] cookie;
            try {
                cookie = response.get(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
                cookie = null;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                cookie = null;
            } finally {
                pendingCookies.remove(player.getUniqueId(), response);
            }
            initialize(event, cookie);
        });
    }

    @Subscribe
    public void onCookie(CookieReceiveEvent event) {
        if (event.getOriginalKey().equals(OpenGateVelocityPlugin.SESSION_COOKIE)) {
            var pending = pendingCookies.get(event.getPlayer().getUniqueId());
            if (pending != null) pending.complete(event.getOriginalData());
        }
    }

    private void initialize(PostLoginEvent event, byte[] cookie) {
        var player = event.getPlayer();
        var playerId = player.getUniqueId();
        var address = player.getRemoteAddress().getAddress().getHostAddress();
        plugin.openGate().sessions().close(playerId);
        var session = plugin.openGate().sessions().open(playerId);
        var translatedId = plugin.openGate().identityIds().translate(playerId);
        var account = plugin.openGate().accounts().find(player.getUsername())
                .or(() -> plugin.openGate().accounts().find(translatedId));
        var accountId = account.map(dev.lunynt.opengate.account.Account::playerId).orElse(translatedId);
        var cookieValid = account.isPresent() && plugin.openGate().cookieSessions().verify(accountId, cookie).join();
        var identityType = plugin.floodgate().isPlayer(playerId)
                ? IdentityType.FLOODGATE
                : player.isOnlineMode() ? IdentityType.PREMIUM : IdentityType.OFFLINE;
        var requirements = plugin.openGate().config().authenticationRequirements()
                .forPermissions(player::hasPermission);
        session.resolve(new ResolvedIdentity(
                player.getUsername(),
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
        } else if (session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT) {
            player.sendMessage(plugin.message(player, "totp-enrollment-required"));
        } else {
            player.sendMessage(plugin.message(player, "login-prompt"));
        }
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        var pending = pendingCookies.remove(event.getPlayer().getUniqueId());
        if (pending != null) pending.complete(null);
        plugin.openGate().sessions().close(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerConnect(ServerPreConnectEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) {
            var limbo = plugin.server().getServer(plugin.openGate().config().limboServer());
            if (limbo.isPresent()) {
                event.setResult(ServerPreConnectEvent.ServerResult.allowed(limbo.orElseThrow()));
            } else {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                event.getPlayer().disconnect(plugin.message(event.getPlayer(), "limbo-missing"));
            }
        }
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof com.velocitypowered.api.proxy.Player player)
                || !isBlocked(player.getUniqueId())) {
            return;
        }
        var command = event.getCommand().split(" ", 2)[0];
        if (!plugin.openGate().config().commands().isAuthenticationLabel(command)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(plugin.message(player, "authenticate-first"));
        }
    }

    @Subscribe
    @SuppressWarnings("deprecation")
    public void onChat(PlayerChatEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    @Subscribe
    public void onTabComplete(TabCompleteEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.getSuggestions().clear();
    }

    private boolean isBlocked(java.util.UUID playerId) {
        return plugin.openGate()
                .sessions()
                .find(playerId)
                .map(session -> session.state() != AuthenticationState.RELEASED)
                .orElse(true);
    }

    private Component message(String key) {
        return plugin.message(key);
    }
}
