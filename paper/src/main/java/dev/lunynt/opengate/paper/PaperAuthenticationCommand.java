package dev.lunynt.opengate.paper;

import dev.lunynt.opengate.account.AuthenticationResult;
import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import java.util.Arrays;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

final class PaperAuthenticationCommand implements CommandExecutor {
    private final OpenGatePaperPlugin plugin;

    PaperAuthenticationCommand(OpenGatePaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }
        return switch (command.getName()) {
            case "register" -> register(player, arguments);
            case "login" -> login(player, arguments);
            default -> false;
        };
    }

    private boolean register(Player player, String[] arguments) {
        if (arguments.length != 2 || !arguments[0].equals(arguments[1])) {
            player.sendMessage(Component.text("Usage: /register <password> <password>"));
            return true;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(Component.text("Registration is not required."));
            return true;
        }
        var password = arguments[0].toCharArray();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        session.beginRegistration();
        plugin.openGate()
                .accounts()
                .register(player.getUniqueId(), player.getName(), IdentityType.OFFLINE, password, address)
                .whenComplete((account, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        session.registrationFailed();
                        player.sendMessage(Component.text("Registration failed: " + rootMessage(error)));
                        return;
                    }
                    session.register();
                    session.release();
                    player.sendMessage(message("registration-success"));
                }));
        Arrays.fill(password, '\0');
        return true;
    }

    private boolean login(Player player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(Component.text("Usage: /login <password>"));
            return true;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_PASSWORD) {
            player.sendMessage(Component.text("Password login is not required."));
            return true;
        }
        var password = arguments[0].toCharArray();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        session.beginPasswordVerification();
        plugin.openGate().accounts().authenticate(player.getUniqueId(), password, address).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null || result != AuthenticationResult.SUCCESS) {
                        if (session.rejectPassword(plugin.openGate().config().maximumLoginAttempts())) {
                            player.kick(message("too-many-attempts"));
                            return;
                        }
                        player.sendMessage(message("incorrect-password"));
                        return;
                    }
                    session.acceptPassword();
                    if (session.state() == AuthenticationState.AUTHENTICATED) {
                        session.release();
                        player.sendMessage(message("login-success"));
                    } else {
                        player.sendMessage(Component.text("Enter your TOTP code to continue."));
                    }
                }));
        Arrays.fill(password, '\0');
        return true;
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
