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
            sender.sendMessage(plugin.openGate().messages().get("players-only"));
            return true;
        }
        return switch (command.getName()) {
            case "register" -> register(player, arguments);
            case "login" -> login(player, arguments);
            case "totp" -> totp(player, arguments);
            case "2fa" -> manageTotp(player, arguments);
            case "account" -> manageAccount(player, arguments);
            case "premium" -> changeIdentity(player, arguments, IdentityType.PREMIUM);
            case "cracked" -> changeIdentity(player, arguments, IdentityType.OFFLINE);
            default -> false;
        };
    }

    private boolean changeIdentity(Player player, String[] arguments, IdentityType target) {
        if (!isReleased(player) || arguments.length != 1) {
            player.sendMessage(message(player, target == IdentityType.PREMIUM ? "usage-premium" : "usage-cracked"));
            return true;
        }
        if (isProtected(player)) {
            player.sendMessage(message(player, "protected-account"));
            return true;
        }
        var account = plugin.openGate().accounts().find(accountId(player)).orElse(null);
        if (account == null) {
            player.sendMessage(message(player, "account-action-failed"));
            return true;
        }
        if (account.identityType() == target) {
            player.sendMessage(message(player,
                    target == IdentityType.PREMIUM ? "premium-already-enabled" : "cracked-already-enabled"));
            return true;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (target == IdentityType.PREMIUM) {
                var profile = plugin.openGate().identities().premiumProfile(account.username());
                if (profile.status() != dev.lunynt.opengate.identity.ProfileLookupResult.Status.FOUND) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(message(player,
                            profile.status() == dev.lunynt.opengate.identity.ProfileLookupResult.Status.NOT_FOUND
                                    ? "premium-profile-not-found" : "premium-profile-unavailable")));
                    return;
                }
                if (!profile.profile().username().equals(account.username())) {
                    plugin.getServer().getScheduler().runTask(plugin,
                            () -> player.sendMessage(message(player, "premium-name-mismatch")));
                    return;
                }
            }
            var password = arguments[0].toCharArray();
            try {
                plugin.openGate().accounts().changeIdentityType(account.playerId(), password, target, address(player))
                        .whenComplete((result, error) -> runAccountCallback(player, result, error, () -> {
                        plugin.clearSessionCookie(player, account.playerId());
                        plugin.openGate().sessions().close(player.getUniqueId());
                        player.kickPlayer(message(player,
                                target == IdentityType.PREMIUM ? "premium-enabled" : "cracked-enabled"));
                        }));
            } finally {
                Arrays.fill(password, '\0');
            }
        });
        return true;
    }

    private boolean register(Player player, String[] arguments) {
        if (arguments.length != 2 || !arguments[0].equals(arguments[1])) {
            player.sendMessage(message(player, "usage-register"));
            return true;
        }
        if (!validPasswordLength(arguments[0])) {
            player.sendMessage(message(player, "password-policy-invalid"));
            return true;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(message(player, "registration-not-required"));
            return true;
        }
        var password = arguments[0].toCharArray();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        session.beginRegistration();
        plugin.openGate()
                .accounts()
                .register(accountId(player), player.getName(), session.identity().orElseThrow().type(), password, address)
                .whenComplete((account, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!isCurrent(player, session)) return;
                    if (error != null) {
                        session.registrationFailed();
                        plugin.getLogger().warning("Registration failed for " + player.getName());
                        player.sendMessage(message(player, "account-action-failed"));
                        return;
                    }
                    session.register();
                    if (session.state() == AuthenticationState.AUTHENTICATED) {
                        session.release();
                        plugin.issueSessionCookie(player, accountId(player));
                        player.sendMessage(message(player, "registration-success"));
                    } else {
                        player.sendMessage(message(player, session.state() == AuthenticationState.AWAITING_TOTP
                                ? "totp-prompt" : "totp-enrollment-required"));
                    }
                }));
        Arrays.fill(password, '\0');
        return true;
    }

    private boolean login(Player player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(message(player, "usage-login"));
            return true;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_PASSWORD) {
            player.sendMessage(message(player, "password-not-required"));
            return true;
        }
        var password = arguments[0].toCharArray();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        session.beginPasswordVerification();
        plugin.openGate().accounts().authenticate(accountId(player), password, address).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!isCurrent(player, session)) return;
                    if (result == AuthenticationResult.RATE_LIMITED) {
                        session.close();
                        player.kickPlayer(message(player, "rate-limited"));
                        return;
                    }
                    if (result == AuthenticationResult.SERVICE_BUSY) {
                        session.close();
                        player.kickPlayer(message(player, "service-busy"));
                        return;
                    }
                    if (error != null || result != AuthenticationResult.SUCCESS) {
                        if (session.rejectPassword(plugin.openGate().config().maximumLoginAttempts())) {
                            player.kickPlayer(message(player, "too-many-attempts"));
                            return;
                        }
                        player.sendMessage(message(player, "incorrect-password"));
                        return;
                    }
                    session.acceptPassword();
                    if (session.state() == AuthenticationState.AUTHENTICATED) {
                        session.release();
                        plugin.issueSessionCookie(player, accountId(player));
                        player.sendMessage(message(player, "login-success"));
                    } else if (session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT) {
                        player.sendMessage(message(player, "totp-enrollment-required"));
                    } else {
                        player.sendMessage(message(player, "totp-prompt"));
                    }
                }));
        Arrays.fill(password, '\0');
        return true;
    }

    private boolean totp(Player player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(message(player, "usage-totp"));
            return true;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_TOTP) {
            player.sendMessage(message(player, "totp-not-required"));
            return true;
        }
        session.beginTotpVerification();
        var code = arguments[0];
        var clientAddress = address(player);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean verified;
            try {
                var account = plugin.openGate().accounts().find(accountId(player)).orElse(null);
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
                player.kickPlayer(message(player, "too-many-attempts"));
            } else {
                player.sendMessage(message(player, "totp-invalid"));
            }
            return;
        }
        session.acceptTotp();
        session.release();
        plugin.issueSessionCookie(player, accountId(player));
        player.sendMessage(message(player, "totp-success"));
    }

    private boolean manageTotp(Player player, String[] arguments) {
        var enrollment = isTotpEnrollment(player);
        if (!plugin.openGate().config().protectedAccounts().permitsTotpAction(
                player::hasPermission, enrollment, arguments.length == 0 ? "" : arguments[0])) {
            player.sendMessage(message(player, "protected-account"));
            return true;
        }
        if ((!isReleased(player) && !enrollment) || arguments.length == 0) {
            player.sendMessage(message(player, "usage-2fa"));
            return true;
        }
        return switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "setup" -> verifyPasswordThen(player, arguments, () -> {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    var account = plugin.openGate().accounts().find(accountId(player)).orElseThrow();
                    var uri = plugin.openGate().totp().begin(account);
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.sendMessage(message(player, "totp-setup") + " " + uri));
                });
            });
            case "confirm" -> {
                if (arguments.length != 2) {
                    player.sendMessage(message(player, "totp-invalid"));
                    yield true;
                }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    var confirmed = plugin.openGate().totp().confirm(accountId(player), arguments[1]);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (confirmed && isTotpEnrollment(player)) {
                            var session = plugin.openGate().sessions().find(player.getUniqueId()).orElseThrow();
                            session.completeTotpEnrollment();
                            session.release();
                            plugin.issueSessionCookie(player, accountId(player));
                        } else if (confirmed) {
                            plugin.clearSessionCookie(player, accountId(player));
                        }
                        player.sendMessage(message(player, confirmed ? "totp-enabled" : "totp-invalid"));
                    });
                });
                yield true;
            }
            case "disable" -> verifyPasswordThen(player, arguments, () -> {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    var account = plugin.openGate().accounts().find(accountId(player)).orElseThrow();
                    plugin.openGate().totp().disable(account);
                    plugin.clearSessionCookie(player, accountId(player));
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.sendMessage(message(player, "totp-disabled")));
                });
            });
            default -> {
                player.sendMessage(message(player, "usage-2fa"));
                yield true;
            }
        };
    }

    private boolean verifyPasswordThen(Player player, String[] arguments, Runnable action) {
        if (arguments.length != 2) {
            player.sendMessage(message(player, "current-password-required"));
            return true;
        }
        var password = arguments[1].toCharArray();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        plugin.openGate().accounts().authenticate(accountId(player), password, address).whenComplete((result, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;
                    if (error != null || result != AuthenticationResult.SUCCESS) {
                        player.sendMessage(message(player, "incorrect-password"));
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

    private boolean isTotpEnrollment(Player player) {
        return plugin.openGate().sessions().find(player.getUniqueId())
                .map(session -> session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT)
                .orElse(false);
    }

    private boolean isCurrent(Player player, dev.lunynt.opengate.auth.AuthenticationSession session) {
        return player.isOnline()
                && plugin.openGate().sessions().find(player.getUniqueId()).orElse(null) == session;
    }

    private boolean manageAccount(Player player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            player.sendMessage(message(player, "usage-account"));
            return true;
        }
        return switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "password" -> changePassword(player, arguments);
            case "logout" -> {
                plugin.clearSessionCookie(player, accountId(player)).whenComplete((ignored, error) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (error != null) {
                                player.sendMessage(message(player, "account-action-failed"));
                                return;
                            }
                            plugin.openGate().sessions().close(player.getUniqueId());
                            player.kickPlayer(message(player, "logged-out"));
                        }));
                yield true;
            }
            case "delete" -> deleteAccount(player, arguments);
            default -> {
                player.sendMessage(message(player, "usage-account"));
                yield true;
            }
        };
    }

    private boolean changePassword(Player player, String[] arguments) {
        if (isProtected(player)) {
            player.sendMessage(message(player, "protected-account"));
            return true;
        }
        if (arguments.length != 3) {
            player.sendMessage(message(player, "usage-account-password"));
            return true;
        }
        if (!validPasswordLength(arguments[2])) {
            player.sendMessage(message(player, "password-policy-invalid"));
            return true;
        }
        var current = arguments[1].toCharArray();
        var replacement = arguments[2].toCharArray();
        try {
            plugin.openGate().accounts()
                    .changePassword(accountId(player), current, replacement, address(player))
                    .whenComplete((result, error) -> runAccountCallback(player, result, error, () -> {
                        plugin.clearSessionCookie(player, accountId(player));
                        player.sendMessage(message(player, "password-changed"));
                    }));
        } catch (IllegalArgumentException error) {
            player.sendMessage(message(player, "account-action-failed"));
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
        return true;
    }

    private boolean validPasswordLength(String password) {
        return password.length() >= plugin.openGate().config().minimumPasswordLength()
                && password.length() <= plugin.openGate().config().maximumPasswordLength();
    }

    private boolean deleteAccount(Player player, String[] arguments) {
        if (isProtected(player)) {
            player.sendMessage(message(player, "protected-account"));
            return true;
        }
        if (arguments.length != 3 || !arguments[2].equalsIgnoreCase("confirm")) {
            player.sendMessage(message(player, "usage-account-delete"));
            return true;
        }
        var password = arguments[1].toCharArray();
        plugin.openGate().accounts().delete(accountId(player), password, address(player))
                .whenComplete((result, error) -> runAccountCallback(player, result, error, () -> {
                    plugin.clearSessionCookie(player, accountId(player));
                    plugin.openGate().sessions().close(player.getUniqueId());
                    player.kickPlayer(message(player, "account-deleted"));
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
                    player.kickPlayer(message(player, "rate-limited"));
                } else if (result == AccountActionResult.SERVICE_BUSY) {
                    player.sendMessage(message(player, "service-busy"));
                } else {
                    player.sendMessage(message(player, "account-action-failed"));
                }
                return;
            }
            success.run();
        });
    }

    private static String address(Player player) {
        return player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
    }

    private java.util.UUID accountId(Player player) {
        return plugin.openGate().sessions().find(player.getUniqueId())
                .flatMap(dev.lunynt.opengate.auth.AuthenticationSession::identity)
                .map(dev.lunynt.opengate.auth.ResolvedIdentity::playerId)
                .orElseGet(() -> plugin.openGate().identityIds().translate(player.getUniqueId()));
    }

    private boolean isProtected(Player player) {
        return plugin.openGate().config().protectedAccounts().protects(player::hasPermission);
    }

    private String message(Player player, String key) {
        var locale = java.util.Locale.forLanguageTag(player.getLocale().replace('_', '-'));
        return org.bukkit.ChatColor.translateAlternateColorCodes(
                '&', plugin.openGate().messages().get(locale, key));
    }
}
