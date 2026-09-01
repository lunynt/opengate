package dev.lunynt.opengate.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.lunynt.opengate.account.AuthenticationResult;
import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import java.util.Arrays;
import net.kyori.adventure.text.Component;

final class VelocityAuthenticationCommand implements SimpleCommand {
    enum Type {
        LOGIN,
        REGISTER
    }

    private final OpenGateVelocityPlugin plugin;
    private final Type type;

    VelocityAuthenticationCommand(OpenGateVelocityPlugin plugin, Type type) {
        this.plugin = plugin;
        this.type = type;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("This command can only be used by players."));
            return;
        }
        if (type == Type.REGISTER) {
            register(player, invocation.arguments());
        } else {
            login(player, invocation.arguments());
        }
    }

    private void register(Player player, String[] arguments) {
        if (arguments.length != 2 || !arguments[0].equals(arguments[1])) {
            player.sendMessage(Component.text("Usage: /register <password> <password>"));
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(Component.text("Registration is not required."));
            return;
        }
        var password = arguments[0].toCharArray();
        var address = player.getRemoteAddress().getAddress().getHostAddress();
        session.beginRegistration();
        plugin.openGate()
                .accounts()
                .register(player.getUniqueId(), player.getUsername(), IdentityType.OFFLINE, password, address)
                .whenComplete((account, error) -> {
                    if (!player.isActive()) return;
                    if (error != null) {
                        session.registrationFailed();
                        player.sendMessage(Component.text("Registration failed: " + rootMessage(error)));
                        return;
                    }
                    session.register();
                    session.release();
                    player.sendMessage(message("registration-success"));
                    plugin.connectToLobby(player);
                });
        Arrays.fill(password, '\0');
    }

    private void login(Player player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(Component.text("Usage: /login <password>"));
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_PASSWORD) {
            player.sendMessage(Component.text("Password login is not required."));
            return;
        }
        var password = arguments[0].toCharArray();
        var address = player.getRemoteAddress().getAddress().getHostAddress();
        session.beginPasswordVerification();
        plugin.openGate().accounts().authenticate(player.getUniqueId(), password, address).whenComplete((result, error) -> {
            if (!player.isActive()) return;
            if (error != null || result != AuthenticationResult.SUCCESS) {
                if (session.rejectPassword(plugin.openGate().config().maximumLoginAttempts())) {
                    player.disconnect(message("too-many-attempts"));
                    return;
                }
                player.sendMessage(message("incorrect-password"));
                return;
            }
            session.acceptPassword();
            if (session.state() == AuthenticationState.AUTHENTICATED) {
                session.release();
                player.sendMessage(message("login-success"));
                plugin.connectToLobby(player);
            } else {
                player.sendMessage(Component.text("Enter your TOTP code to continue."));
            }
        });
        Arrays.fill(password, '\0');
    }

    private static String rootMessage(Throwable error) {
        var cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "internal error" : cause.getMessage();
    }

    private Component message(String key) {
        return Component.text(plugin.openGate().messages().get(key));
    }
}
