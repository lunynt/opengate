package dev.lunynt.opengate.paper;

import dev.lunynt.opengate.account.AuthenticationResult;
import dev.lunynt.opengate.account.AccountActionResult;
import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import java.util.Arrays;
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
            case "totp" -> totp(player, arguments);
            case "2fa" -> manageTotp(player, arguments);
            case "account" -> manageAccount(player, arguments);
            default -> false;
        };
    }

    private boolean register(Player player, String[] arguments) {
        if (arguments.length != 2 || !arguments[0].equals(arguments[1])) {
            player.sendMessage("Usage: /register <password> <password>");
            return true;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage("Registration is not required.");
            return true;
        }
        var password = arguments[0].toCharArray();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        session.beginRegistration();
        plugin.openGate()
                .accounts()
                .register(player.getUniqueId(), player.getName(), IdentityType.OFFLINE, password, address)
                .whenComplete((account, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!isCurrent(player, session)) return;
                    if (error != null) {
                        session.registrationFailed();
                        plugin.getLogger().warning("Registration failed for " + player.getName());
                        player.sendMessage(message("account-action-failed"));
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
            player.sendMessage("Usage: /login <password>");
            return true;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_PASSWORD) {
            player.sendMessage("Password login is not required.");
            return true;
        }
        var password = arguments[0].toCharArray();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        session.beginPasswordVerification();
        plugin.openGate().accounts().authenticate(player.getUniqueId(), password, address).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!isCurrent(player, session)) return;
                    if (result == AuthenticationResult.RATE_LIMITED) {
                        session.close();
                        player.kickPlayer(message("rate-limited"));
                        return;
                    }
                    if (result == AuthenticationResult.SERVICE_BUSY) {
                        session.close();
                        player.kickPlayer(message("service-busy"));
                        return;
                    }
                    if (error != null || result != AuthenticationResult.SUCCESS) {
                        if (session.rejectPassword(plugin.openGate().config().maximumLoginAttempts())) {
                            player.kickPlayer(message("too-many-attempts"));
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
                        player.sendMessage(message("totp-prompt"));
                    }
                }));
        Arrays.fill(password, '\0');
        return true;
    }

    private boolean totp(Player player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage("Usage: /totp <code>");
            return true;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_TOTP) {
            player.sendMessage("Two-factor authentication is not required.");
            return true;
        }
        session.beginTotpVerification();
        var code = arguments[0];
        var clientAddress = address(player);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean verified;
            try {
                var account = plugin.openGate().accounts().find(player.getUniqueId()).orElse(null);
                verified = account != null && plugin.openGate().totp().verify(account, code, clientAddress);
            } catch (RuntimeException exception) {
                verified = false;
            }
            var result = verified;
            plugin.getServer().getScheduler().runTask(plugin, () -> finishTotp(player, session, result));
        });
        return true;
    }

    private void finishTotp(
            Player player, dev.lunynt.opengate.auth.AuthenticationSession session, boolean verified) {
        if (!player.isOnline() || plugin.openGate().sessions().find(player.getUniqueId()).orElse(null) != session) return;
        if (!verified) {
            if (session.rejectTotp(plugin.openGate().config().maximumLoginAttempts())) {
                player.kickPlayer(message("too-many-attempts"));
            } else {
                player.sendMessage(message("totp-invalid"));
            }
            return;
        }
        session.acceptTotp();
        session.release();
        player.sendMessage(message("totp-success"));
    }

    private boolean manageTotp(Player player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            player.sendMessage("Usage: /2fa setup <password> | confirm <code> | disable <password>");
            return true;
        }
        return switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "setup" -> verifyPasswordThen(player, arguments, () -> {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    var account = plugin.openGate().accounts().find(player.getUniqueId()).orElseThrow();
                    var uri = plugin.openGate().totp().begin(account);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.sendMessage(message("totp-setup") + " " + uri));
                });
            });
            case "confirm" -> {
                if (arguments.length != 2) {
                    player.sendMessage(message("totp-invalid"));
                    yield true;
                }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    var confirmed = plugin.openGate().totp().confirm(player.getUniqueId(), arguments[1]);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.sendMessage(message(confirmed ? "totp-enabled" : "totp-invalid")));
                });
                yield true;
            }
            case "disable" -> verifyPasswordThen(player, arguments, () -> {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    var account = plugin.openGate().accounts().find(player.getUniqueId()).orElseThrow();
                    plugin.openGate().totp().disable(account);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.sendMessage(message("totp-disabled")));
                });
            });
            default -> {
                player.sendMessage("Usage: /2fa setup <password> | confirm <code> | disable <password>");
                yield true;
            }
        };
    }

    private boolean verifyPasswordThen(Player player, String[] arguments, Runnable action) {
        if (arguments.length != 2) {
            player.sendMessage("This action requires your current password.");
            return true;
        }
        var password = arguments[1].toCharArray();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        plugin.openGate().accounts().authenticate(player.getUniqueId(), password, address).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null || result != AuthenticationResult.SUCCESS) {
                        player.sendMessage(message("incorrect-password"));
                    } else {
                        action.run();
                    }
                }));
        Arrays.fill(password, '\0');
        return true;
    }

    private boolean isReleased(Player player) {
        return plugin.openGate().sessions().find(player.getUniqueId())
                .map(session -> session.state() == AuthenticationState.RELEASED)
                .orElse(false);
    }

    private boolean isCurrent(Player player, dev.lunynt.opengate.auth.AuthenticationSession session) {
        return player.isOnline()
                && plugin.openGate().sessions().find(player.getUniqueId()).orElse(null) == session;
    }

    private boolean manageAccount(Player player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            player.sendMessage("Usage: /account password <current> <new> | logout | delete <password> confirm");
            return true;
        }
        return switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "password" -> changePassword(player, arguments);
            case "logout" -> {
                plugin.openGate().sessions().close(player.getUniqueId());
                player.kickPlayer(message("logged-out"));
                yield true;
            }
            case "delete" -> deleteAccount(player, arguments);
            default -> {
                player.sendMessage("Usage: /account password <current> <new> | logout | delete <password> confirm");
                yield true;
            }
        };
    }

    private boolean changePassword(Player player, String[] arguments) {
        if (arguments.length != 3) {
            player.sendMessage("Usage: /account password <current> <new>");
            return true;
        }
        var current = arguments[1].toCharArray();
        var replacement = arguments[2].toCharArray();
        try {
            plugin.openGate().accounts()
                    .changePassword(player.getUniqueId(), current, replacement, address(player))
                    .whenComplete((result, error) -> runAccountCallback(player, result, error, () ->
                            player.sendMessage(message("password-changed"))));
        } catch (IllegalArgumentException error) {
            player.sendMessage(error.getMessage());
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
        return true;
    }

    private boolean deleteAccount(Player player, String[] arguments) {
        if (arguments.length != 3 || !arguments[2].equalsIgnoreCase("confirm")) {
            player.sendMessage("Usage: /account delete <password> confirm");
            return true;
        }
        var password = arguments[1].toCharArray();
        plugin.openGate().accounts().delete(player.getUniqueId(), password, address(player))
                .whenComplete((result, error) -> runAccountCallback(player, result, error, () -> {
                    plugin.openGate().sessions().close(player.getUniqueId());
                    player.kickPlayer(message("account-deleted"));
                }));
        Arrays.fill(password, '\0');
        return true;
    }

    private void runAccountCallback(
            Player player, AccountActionResult result, Throwable error, Runnable success) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            if (error != null || result != AccountActionResult.SUCCESS) {
                if (result == AccountActionResult.RATE_LIMITED) {
                    player.kickPlayer(message("rate-limited"));
                } else if (result == AccountActionResult.SERVICE_BUSY) {
                    player.sendMessage(message("service-busy"));
                } else {
                    player.sendMessage(message("account-action-failed"));
                }
                return;
            }
            success.run();
        });
    }

    private static String address(Player player) {
        return player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
    }

    private String message(String key) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.openGate().messages().get(key));
    }
}
