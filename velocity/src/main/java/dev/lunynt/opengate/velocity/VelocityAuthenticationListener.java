package dev.lunynt.opengate.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.ResolvedIdentity;
import java.util.Locale;
import java.util.Set;
import net.kyori.adventure.text.Component;

final class VelocityAuthenticationListener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("login", "l", "register", "reg", "totp", "2fa");

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
                });
            } catch (RuntimeException exception) {
                event.setResult(PreLoginEvent.PreLoginComponentResult.denied(message("profile-lookup-unavailable")));
            }
        });
    }

    @Subscribe
    public EventTask onPostLogin(PostLoginEvent event) {
        return EventTask.async(() -> initialize(event));
    }

    private void initialize(PostLoginEvent event) {
        var player = event.getPlayer();
        var playerId = player.getUniqueId();
        var address = player.getRemoteAddress().getAddress().getHostAddress();
        plugin.openGate().sessions().close(playerId);
        var session = plugin.openGate().sessions().open(playerId);
        var account = plugin.openGate().accounts().find(playerId);
        var trusted = account.filter(value -> plugin.openGate()
                        .accounts()
                        .hasTrustedSession(value, address, plugin.openGate().config().trustedSessionLifetime()))
                .isPresent();
        session.resolve(new ResolvedIdentity(
                player.getUsername(),
                playerId,
                player.isOnlineMode() ? IdentityType.PREMIUM : IdentityType.OFFLINE,
                account.isPresent(),
                account.map(value -> value.passwordHash() != null).orElse(false),
                account.map(value -> value.totpSecret() != null).orElse(false),
                trusted));

        if (session.state() == AuthenticationState.AUTHENTICATED) {
            session.release();
            player.sendMessage(message("automatic-login"));
        } else if (session.state() == AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(message("register-prompt"));
        } else {
            player.sendMessage(message("login-prompt"));
        }
        plugin.scheduleAuthenticationTimeout(player);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
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
                event.getPlayer().disconnect(message("limbo-missing"));
            }
        }
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof com.velocitypowered.api.proxy.Player player)
                || !isBlocked(player.getUniqueId())) {
            return;
        }
        var command = event.getCommand().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (!ALLOWED_COMMANDS.contains(command)) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
            player.sendMessage(message("authenticate-first"));
        }
    }

    private boolean isBlocked(java.util.UUID playerId) {
        return plugin.openGate()
                .sessions()
                .find(playerId)
                .map(session -> session.state() != AuthenticationState.RELEASED)
                .orElse(true);
    }

    private Component message(String key) {
        return Component.text(plugin.openGate().messages().get(key));
    }
}
