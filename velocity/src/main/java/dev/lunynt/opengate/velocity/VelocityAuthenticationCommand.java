package dev.lunynt.opengate.velocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import dev.lunynt.opengate.account.AuthenticationResult;
import dev.lunynt.opengate.account.AccountActionResult;
import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import java.util.Arrays;
import net.kyori.adventure.text.Component;

final class VelocityAuthenticationCommand implements SimpleCommand {
    enum Type {
        LOGIN,
        REGISTER,
        TOTP,
        MANAGE_TOTP,
        ACCOUNT
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
        switch (type) {
            case REGISTER -> register(player, invocation.arguments());
            case LOGIN -> login(player, invocation.arguments());
            case TOTP -> totp(player, invocation.arguments());
            case MANAGE_TOTP -> manageTotp(player, invocation.arguments());
            case ACCOUNT -> manageAccount(player, invocation.arguments());
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
            if (result == AuthenticationResult.RATE_LIMITED) {
                session.close();
                player.disconnect(message("rate-limited"));
                return;
            }
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
                player.sendMessage(message("totp-prompt"));
            }
        });
        Arrays.fill(password, '\0');
    }

    private void totp(Player player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(Component.text("Usage: /totp <code>"));
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        var account = plugin.openGate().accounts().find(player.getUniqueId()).orElse(null);
        if (session == null || account == null || session.state() != AuthenticationState.AWAITING_TOTP) {
            player.sendMessage(Component.text("Two-factor authentication is not required."));
            return;
        }
        if (!plugin.openGate().totp().verify(account, arguments[0])) {
            player.sendMessage(message("totp-invalid"));
            return;
        }
        session.acceptTotp();
        session.release();
        player.sendMessage(message("totp-success"));
        plugin.connectToLobby(player);
    }

    private void manageTotp(Player player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            player.sendMessage(Component.text("Usage: /2fa setup <password> | confirm <code> | disable <password>"));
            return;
        }
        switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "setup" -> verifyPasswordThen(player, arguments, () -> {
                var account = plugin.openGate().accounts().find(player.getUniqueId()).orElseThrow();
                var uri = plugin.openGate().totp().begin(account);
                player.sendMessage(message("totp-setup").append(Component.space()).append(
                        Component.text(uri).clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(uri))));
            });
            case "confirm" -> {
                if (arguments.length != 2 || !plugin.openGate().totp().confirm(player.getUniqueId(), arguments[1])) {
                    player.sendMessage(message("totp-invalid"));
                } else {
                    player.sendMessage(message("totp-enabled"));
                }
            }
            case "disable" -> verifyPasswordThen(player, arguments, () -> {
                var account = plugin.openGate().accounts().find(player.getUniqueId()).orElseThrow();
                plugin.openGate().totp().disable(account);
                player.sendMessage(message("totp-disabled"));
            });
            default -> player.sendMessage(
                    Component.text("Usage: /2fa setup <password> | confirm <code> | disable <password>"));
        }
    }

    private void verifyPasswordThen(Player player, String[] arguments, Runnable action) {
        if (arguments.length != 2) {
            player.sendMessage(Component.text("This action requires your current password."));
            return;
        }
        var password = arguments[1].toCharArray();
        var address = player.getRemoteAddress().getAddress().getHostAddress();
        plugin.openGate().accounts().authenticate(player.getUniqueId(), password, address).whenComplete((result, error) -> {
            if (!player.isActive()) return;
            if (error != null || result != AuthenticationResult.SUCCESS) {
                player.sendMessage(message("incorrect-password"));
            } else {
                action.run();
            }
        });
        Arrays.fill(password, '\0');
    }

    private boolean isReleased(Player player) {
        return plugin.openGate().sessions().find(player.getUniqueId())
                .map(session -> session.state() == AuthenticationState.RELEASED)
                .orElse(false);
    }

    private void manageAccount(Player player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            player.sendMessage(Component.text("Usage: /account password <current> <new> | logout | delete <password> confirm"));
            return;
        }
        switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "password" -> changePassword(player, arguments);
            case "logout" -> {
                plugin.openGate().accounts().revokeTrustedSession(player.getUniqueId());
                plugin.openGate().sessions().close(player.getUniqueId());
                player.disconnect(message("logged-out"));
            }
            case "delete" -> deleteAccount(player, arguments);
            default -> player.sendMessage(
                    Component.text("Usage: /account password <current> <new> | logout | delete <password> confirm"));
        }
    }

    private void changePassword(Player player, String[] arguments) {
        if (arguments.length != 3) {
            player.sendMessage(Component.text("Usage: /account password <current> <new>"));
            return;
        }
        var current = arguments[1].toCharArray();
        var replacement = arguments[2].toCharArray();
        try {
            plugin.openGate().accounts()
                    .changePassword(player.getUniqueId(), current, replacement, address(player))
                    .whenComplete((result, error) -> accountCallback(player, result, error, () ->
                            player.sendMessage(message("password-changed"))));
        } catch (IllegalArgumentException error) {
            player.sendMessage(Component.text(error.getMessage()));
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
    }

    private void deleteAccount(Player player, String[] arguments) {
        if (arguments.length != 3 || !arguments[2].equalsIgnoreCase("confirm")) {
            player.sendMessage(Component.text("Usage: /account delete <password> confirm"));
            return;
        }
        var password = arguments[1].toCharArray();
        plugin.openGate().accounts().delete(player.getUniqueId(), password, address(player))
                .whenComplete((result, error) -> accountCallback(player, result, error, () -> {
                    plugin.openGate().sessions().close(player.getUniqueId());
                    player.disconnect(message("account-deleted"));
                }));
        Arrays.fill(password, '\0');
    }

    private void accountCallback(
            Player player, AccountActionResult result, Throwable error, Runnable success) {
        if (!player.isActive()) return;
        if (error != null || result != AccountActionResult.SUCCESS) {
            if (result == AccountActionResult.RATE_LIMITED) {
                player.disconnect(message("rate-limited"));
            } else {
                player.sendMessage(message("account-action-failed"));
            }
            return;
        }
        success.run();
    }

    private static String address(Player player) {
        return player.getRemoteAddress().getAddress().getHostAddress();
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
