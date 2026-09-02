package dev.lunynt.opengate.bungee;

import dev.lunynt.opengate.account.AccountActionResult;
import dev.lunynt.opengate.account.AuthenticationResult;
import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import java.util.Arrays;
import java.util.Locale;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

final class BungeeAuthenticationCommand extends Command {
    private final OpenGateBungeePlugin plugin;

    BungeeAuthenticationCommand(OpenGateBungeePlugin plugin, String name, String... aliases) {
        super(name, null, aliases);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] arguments) {
        if (!(sender instanceof ProxiedPlayer player)) {
            sender.sendMessage(new TextComponent("This command can only be used by players."));
            return;
        }
        switch (getName()) {
            case "register" -> register(player, arguments);
            case "login" -> login(player, arguments);
            case "totp" -> totp(player, arguments);
            case "2fa" -> manageTotp(player, arguments);
            case "account" -> manageAccount(player, arguments);
            default -> throw new IllegalStateException("unknown authentication command");
        }
    }

    private void register(ProxiedPlayer player, String[] arguments) {
        if (arguments.length != 2 || !arguments[0].equals(arguments[1])) {
            send(player, "Usage: /register <password> <password>");
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_REGISTRATION) {
            send(player, "Registration is not required.");
            return;
        }
        var password = arguments[0].toCharArray();
        session.beginRegistration();
        try {
            plugin.openGate().accounts()
                    .register(
                            player.getUniqueId(),
                            player.getName(),
                            IdentityType.OFFLINE,
                            password,
                            BungeeAuthenticationListener.address(player))
                    .whenComplete((account, error) -> {
                        if (!player.isConnected()) return;
                        if (error != null) {
                            session.registrationFailed();
                            send(player, "Registration failed: " + rootMessage(error));
                            return;
                        }
                        session.register();
                        session.release();
                        player.sendMessage(plugin.message("registration-success"));
                        plugin.connectToLobby(player);
                    });
        } catch (IllegalArgumentException error) {
            session.registrationFailed();
            send(player, error.getMessage());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void login(ProxiedPlayer player, String[] arguments) {
        if (arguments.length != 1) {
            send(player, "Usage: /login <password>");
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_PASSWORD) {
            send(player, "Password login is not required.");
            return;
        }
        var password = arguments[0].toCharArray();
        session.beginPasswordVerification();
        plugin.openGate().accounts()
                .authenticate(player.getUniqueId(), password, BungeeAuthenticationListener.address(player))
                .whenComplete((result, error) -> finishLogin(player, session, result, error));
        Arrays.fill(password, '\0');
    }

    private void finishLogin(
            ProxiedPlayer player,
            dev.lunynt.opengate.auth.AuthenticationSession session,
            AuthenticationResult result,
            Throwable error) {
        if (!player.isConnected()) return;
        if (result == AuthenticationResult.RATE_LIMITED) {
            session.close();
            player.disconnect(plugin.message("rate-limited"));
            return;
        }
        if (error != null || result != AuthenticationResult.SUCCESS) {
            if (session.rejectPassword(plugin.openGate().config().maximumLoginAttempts())) {
                player.disconnect(plugin.message("too-many-attempts"));
            } else {
                player.sendMessage(plugin.message("incorrect-password"));
            }
            return;
        }
        session.acceptPassword();
        if (session.state() == AuthenticationState.AUTHENTICATED) {
            session.release();
            player.sendMessage(plugin.message("login-success"));
            plugin.connectToLobby(player);
        } else {
            player.sendMessage(plugin.message("totp-prompt"));
        }
    }

    private void totp(ProxiedPlayer player, String[] arguments) {
        if (arguments.length != 1) {
            send(player, "Usage: /totp <code>");
            return;
        }
        plugin.proxy().getScheduler().runAsync(plugin, () -> {
            var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
            var account = plugin.openGate().accounts().find(player.getUniqueId()).orElse(null);
            if (session == null || account == null || session.state() != AuthenticationState.AWAITING_TOTP) {
                send(player, "Two-factor authentication is not required.");
                return;
            }
            if (!plugin.openGate().totp().verify(account, arguments[0])) {
                player.sendMessage(plugin.message("totp-invalid"));
                return;
            }
            session.acceptTotp();
            session.release();
            player.sendMessage(plugin.message("totp-success"));
            plugin.connectToLobby(player);
        });
    }

    private void manageTotp(ProxiedPlayer player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            send(player, "Usage: /2fa setup <password> | confirm <code> | disable <password>");
            return;
        }
        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "setup" -> verifyPasswordThen(player, arguments, () -> {
                var account = plugin.openGate().accounts().find(player.getUniqueId()).orElseThrow();
                var uri = plugin.openGate().totp().begin(account);
                send(player, plugin.openGate().messages().get("totp-setup") + " " + uri);
            });
            case "confirm" -> plugin.proxy().getScheduler().runAsync(plugin, () -> {
                if (arguments.length != 2 || !plugin.openGate().totp().confirm(player.getUniqueId(), arguments[1])) {
                    player.sendMessage(plugin.message("totp-invalid"));
                } else {
                    player.sendMessage(plugin.message("totp-enabled"));
                }
            });
            case "disable" -> verifyPasswordThen(player, arguments, () -> {
                var account = plugin.openGate().accounts().find(player.getUniqueId()).orElseThrow();
                plugin.openGate().totp().disable(account);
                player.sendMessage(plugin.message("totp-disabled"));
            });
            default -> send(player, "Usage: /2fa setup <password> | confirm <code> | disable <password>");
        }
    }

    private void verifyPasswordThen(ProxiedPlayer player, String[] arguments, Runnable action) {
        if (arguments.length != 2) {
            send(player, "This action requires your current password.");
            return;
        }
        var password = arguments[1].toCharArray();
        plugin.openGate().accounts()
                .authenticate(player.getUniqueId(), password, BungeeAuthenticationListener.address(player))
                .whenComplete((result, error) -> {
                    if (!player.isConnected()) return;
                    if (error != null || result != AuthenticationResult.SUCCESS) {
                        player.sendMessage(plugin.message("incorrect-password"));
                    } else {
                        action.run();
                    }
                });
        Arrays.fill(password, '\0');
    }

    private void manageAccount(ProxiedPlayer player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            send(player, "Usage: /account password <current> <new> | logout | delete <password> confirm");
            return;
        }
        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "password" -> changePassword(player, arguments);
            case "logout" -> {
                plugin.openGate().accounts().revokeTrustedSession(player.getUniqueId());
                plugin.openGate().sessions().close(player.getUniqueId());
                player.disconnect(plugin.message("logged-out"));
            }
            case "delete" -> deleteAccount(player, arguments);
            default -> send(player, "Usage: /account password <current> <new> | logout | delete <password> confirm");
        }
    }

    private void changePassword(ProxiedPlayer player, String[] arguments) {
        if (arguments.length != 3) {
            send(player, "Usage: /account password <current> <new>");
            return;
        }
        var current = arguments[1].toCharArray();
        var replacement = arguments[2].toCharArray();
        try {
            plugin.openGate().accounts()
                    .changePassword(
                            player.getUniqueId(), current, replacement, BungeeAuthenticationListener.address(player))
                    .whenComplete((result, error) -> accountCallback(
                            player, result, error, () -> player.sendMessage(plugin.message("password-changed"))));
        } catch (IllegalArgumentException error) {
            send(player, error.getMessage());
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
    }

    private void deleteAccount(ProxiedPlayer player, String[] arguments) {
        if (arguments.length != 3 || !arguments[2].equalsIgnoreCase("confirm")) {
            send(player, "Usage: /account delete <password> confirm");
            return;
        }
        var password = arguments[1].toCharArray();
        plugin.openGate().accounts()
                .delete(player.getUniqueId(), password, BungeeAuthenticationListener.address(player))
                .whenComplete((result, error) -> accountCallback(player, result, error, () -> {
                    plugin.openGate().sessions().close(player.getUniqueId());
                    player.disconnect(plugin.message("account-deleted"));
                }));
        Arrays.fill(password, '\0');
    }

    private void accountCallback(
            ProxiedPlayer player, AccountActionResult result, Throwable error, Runnable success) {
        if (!player.isConnected()) return;
        if (error != null || result != AccountActionResult.SUCCESS) {
            if (result == AccountActionResult.RATE_LIMITED) {
                player.disconnect(plugin.message("rate-limited"));
            } else {
                player.sendMessage(plugin.message("account-action-failed"));
            }
            return;
        }
        success.run();
    }

    private boolean isReleased(ProxiedPlayer player) {
        return plugin.openGate().sessions().find(player.getUniqueId())
                .map(session -> session.state() == AuthenticationState.RELEASED)
                .orElse(false);
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(new TextComponent(message));
    }

    private static String rootMessage(Throwable error) {
        var cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null ? "internal error" : cause.getMessage();
    }
}
