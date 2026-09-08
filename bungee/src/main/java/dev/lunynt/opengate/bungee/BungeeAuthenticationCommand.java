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
            sender.sendMessage(new TextComponent(plugin.openGate().messages().get("players-only")));
            return;
        }
        switch (getName()) {
            case "register" -> register(player, arguments);
            case "login" -> login(player, arguments);
            case "totp" -> totp(player, arguments);
            case "2fa" -> manageTotp(player, arguments);
            case "account" -> manageAccount(player, arguments);
            case "premium" -> changeIdentity(player, arguments, IdentityType.PREMIUM);
            case "cracked" -> changeIdentity(player, arguments, IdentityType.OFFLINE);
            default -> throw new IllegalStateException("unknown authentication command");
        }
    }

    private void changeIdentity(ProxiedPlayer player, String[] arguments, IdentityType target) {
        if (!isReleased(player) || arguments.length != 1) {
            player.sendMessage(plugin.message(player,
                    target == IdentityType.PREMIUM ? "usage-premium" : "usage-cracked"));
            return;
        }
        if (isProtected(player)) {
            player.sendMessage(plugin.message(player, "protected-account"));
            return;
        }
        var account = plugin.openGate().accounts().find(accountId(player)).orElse(null);
        if (account == null) {
            player.sendMessage(plugin.message(player, "account-action-failed"));
            return;
        }
        if (account.identityType() == target) {
            player.sendMessage(plugin.message(player,
                    target == IdentityType.PREMIUM ? "premium-already-enabled" : "cracked-already-enabled"));
            return;
        }
        plugin.proxy().getScheduler().runAsync(plugin, () -> {
            if (target == IdentityType.PREMIUM) {
                var profile = plugin.openGate().identities().premiumProfile(account.username());
                if (profile.status() != dev.lunynt.opengate.identity.ProfileLookupResult.Status.FOUND) {
                    player.sendMessage(plugin.message(player, profile.status()
                            == dev.lunynt.opengate.identity.ProfileLookupResult.Status.NOT_FOUND
                            ? "premium-profile-not-found" : "premium-profile-unavailable"));
                    return;
                }
                if (!profile.profile().username().equals(account.username())) {
                    player.sendMessage(plugin.message(player, "premium-name-mismatch"));
                    return;
                }
            }
            var password = arguments[0].toCharArray();
            try {
                plugin.openGate().accounts().changeIdentityType(
                        account.playerId(), password, target, BungeeAuthenticationListener.address(player))
                        .whenComplete((result, error) -> accountCallback(player, result, error, () -> {
                        plugin.clearSessionCookie(player, account.playerId());
                        plugin.openGate().sessions().close(player.getUniqueId());
                        player.disconnect(plugin.message(player,
                                target == IdentityType.PREMIUM ? "premium-enabled" : "cracked-enabled"));
                        }));
            } finally {
                Arrays.fill(password, '\0');
            }
        });
    }

    private void register(ProxiedPlayer player, String[] arguments) {
        if (arguments.length != 2 || !arguments[0].equals(arguments[1])) {
            player.sendMessage(plugin.message(player, "usage-register"));
            return;
        }
        if (!validPasswordLength(arguments[0])) {
            player.sendMessage(plugin.message(player, "password-policy-invalid"));
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(plugin.message(player, "registration-not-required"));
            return;
        }
        var password = arguments[0].toCharArray();
        session.beginRegistration();
        try {
            plugin.openGate().accounts()
                    .register(
                            accountId(player),
                            player.getName(),
                            session.identity().orElseThrow().type(),
                            password,
                            BungeeAuthenticationListener.address(player))
                    .whenComplete((account, error) -> {
                        if (!isCurrent(player, session)) return;
                        if (error != null) {
                            session.registrationFailed();
                            player.sendMessage(plugin.message(player, "account-action-failed"));
                            return;
                        }
                        session.register();
                        if (session.state() == AuthenticationState.AUTHENTICATED) {
                            session.release();
                            plugin.issueSessionCookie(player, accountId(player));
                            player.sendMessage(plugin.message(player, "registration-success"));
                            plugin.connectToLobby(player);
                        } else {
                        player.sendMessage(plugin.message(player, session.state() == AuthenticationState.AWAITING_TOTP
                                ? "totp-prompt" : "totp-enrollment-required"));
                        }
                    });
        } catch (IllegalArgumentException error) {
            session.registrationFailed();
            player.sendMessage(plugin.message(player, "account-action-failed"));
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    private void login(ProxiedPlayer player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(plugin.message(player, "usage-login"));
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_PASSWORD) {
            player.sendMessage(plugin.message(player, "password-not-required"));
            return;
        }
        var password = arguments[0].toCharArray();
        session.beginPasswordVerification();
        plugin.openGate().accounts()
                .authenticate(accountId(player), password, BungeeAuthenticationListener.address(player))
                .whenComplete((result, error) -> finishLogin(player, session, result, error));
        Arrays.fill(password, '\0');
    }

    private void finishLogin(
            ProxiedPlayer player,
            dev.lunynt.opengate.auth.AuthenticationSession session,
            AuthenticationResult result,
            Throwable error) {
        if (!isCurrent(player, session)) return;
        if (result == AuthenticationResult.RATE_LIMITED) {
            session.close();
            player.disconnect(plugin.message(player, "rate-limited"));
            return;
        }
        if (result == AuthenticationResult.SERVICE_BUSY) {
            session.close();
            player.disconnect(plugin.message(player, "service-busy"));
            return;
        }
        if (error != null || result != AuthenticationResult.SUCCESS) {
            if (session.rejectPassword(plugin.openGate().config().maximumLoginAttempts())) {
                player.disconnect(plugin.message(player, "too-many-attempts"));
            } else {
                player.sendMessage(plugin.message(player, "incorrect-password"));
            }
            return;
        }
        session.acceptPassword();
        if (session.state() == AuthenticationState.AUTHENTICATED) {
            session.release();
            plugin.issueSessionCookie(player, accountId(player));
            player.sendMessage(plugin.message(player, "login-success"));
            plugin.connectToLobby(player);
        } else if (session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT) {
            player.sendMessage(plugin.message(player, "totp-enrollment-required"));
        } else {
            player.sendMessage(plugin.message(player, "totp-prompt"));
        }
    }

    private void totp(ProxiedPlayer player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(plugin.message(player, "usage-totp"));
            return;
        }
        plugin.proxy().getScheduler().runAsync(plugin, () -> {
            var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
            var account = plugin.openGate().accounts().find(accountId(player)).orElse(null);
            if (session == null || account == null || session.state() != AuthenticationState.AWAITING_TOTP) {
                player.sendMessage(plugin.message(player, "totp-not-required"));
                return;
            }
            session.beginTotpVerification();
            if (!plugin.openGate().totp().verify(account, arguments[0], BungeeAuthenticationListener.address(player))) {
                if (session.rejectTotp(plugin.openGate().config().maximumLoginAttempts())) {
                    player.disconnect(plugin.message(player, "too-many-attempts"));
                } else {
                    player.sendMessage(plugin.message(player, "totp-invalid"));
                }
                return;
            }
            session.acceptTotp();
            session.release();
            plugin.issueSessionCookie(player, accountId(player));
            player.sendMessage(plugin.message(player, "totp-success"));
            plugin.connectToLobby(player);
        });
    }

    private void manageTotp(ProxiedPlayer player, String[] arguments) {
        var enrollment = isTotpEnrollment(player);
        if (!plugin.openGate().config().protectedAccounts().permitsTotpAction(
                player::hasPermission, enrollment, arguments.length == 0 ? "" : arguments[0])) {
            player.sendMessage(plugin.message(player, "protected-account"));
            return;
        }
        if ((!isReleased(player) && !enrollment) || arguments.length == 0) {
            player.sendMessage(plugin.message(player, "usage-2fa"));
            return;
        }
        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "setup" -> verifyPasswordThen(player, arguments, () -> {
                var account = plugin.openGate().accounts().find(accountId(player)).orElseThrow();
                var uri = plugin.openGate().totp().begin(account);
                send(player, plugin.openGate().messages().get("totp-setup") + " " + uri);
            });
            case "confirm" -> plugin.proxy().getScheduler().runAsync(plugin, () -> {
                if (arguments.length != 2 || !plugin.openGate().totp().confirm(accountId(player), arguments[1])) {
                    player.sendMessage(plugin.message(player, "totp-invalid"));
                } else {
                    if (isTotpEnrollment(player)) {
                        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElseThrow();
                        session.completeTotpEnrollment();
                        session.release();
                        plugin.issueSessionCookie(player, accountId(player));
                        plugin.connectToLobby(player);
                    } else {
                        plugin.clearSessionCookie(player, accountId(player));
                    }
                    player.sendMessage(plugin.message(player, "totp-enabled"));
                }
            });
            case "disable" -> verifyPasswordThen(player, arguments, () -> {
                var account = plugin.openGate().accounts().find(accountId(player)).orElseThrow();
                plugin.openGate().totp().disable(account);
                plugin.clearSessionCookie(player, accountId(player));
                player.sendMessage(plugin.message(player, "totp-disabled"));
            });
            default -> player.sendMessage(plugin.message(player, "usage-2fa"));
        }
    }

    private void verifyPasswordThen(ProxiedPlayer player, String[] arguments, Runnable action) {
        if (arguments.length != 2) {
            player.sendMessage(plugin.message(player, "current-password-required"));
            return;
        }
        var password = arguments[1].toCharArray();
        plugin.openGate().accounts()
                .authenticate(accountId(player), password, BungeeAuthenticationListener.address(player))
                .whenComplete((result, error) -> {
                    if (!player.isConnected()) return;
                    if (error != null || result != AuthenticationResult.SUCCESS) {
                        player.sendMessage(plugin.message(player, "incorrect-password"));
                    } else {
                        action.run();
                    }
                });
        Arrays.fill(password, '\0');
    }

    private void manageAccount(ProxiedPlayer player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            player.sendMessage(plugin.message(player, "usage-account"));
            return;
        }
        switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "password" -> changePassword(player, arguments);
            case "logout" -> {
                plugin.clearSessionCookie(player, accountId(player)).whenComplete((ignored, error) ->
                        plugin.proxy().getScheduler().runAsync(plugin, () -> {
                            if (!player.isConnected()) return;
                            if (error != null) {
                                player.sendMessage(plugin.message(player, "account-action-failed"));
                                return;
                            }
                            plugin.openGate().sessions().close(player.getUniqueId());
                            player.disconnect(plugin.message(player, "logged-out"));
                        }));
            }
            case "delete" -> deleteAccount(player, arguments);
            default -> player.sendMessage(plugin.message(player, "usage-account"));
        }
    }

    private void changePassword(ProxiedPlayer player, String[] arguments) {
        if (isProtected(player)) {
            player.sendMessage(plugin.message(player, "protected-account"));
            return;
        }
        if (arguments.length != 3) {
            player.sendMessage(plugin.message(player, "usage-account-password"));
            return;
        }
        if (!validPasswordLength(arguments[2])) {
            player.sendMessage(plugin.message(player, "password-policy-invalid"));
            return;
        }
        var current = arguments[1].toCharArray();
        var replacement = arguments[2].toCharArray();
        try {
            plugin.openGate().accounts()
                    .changePassword(
                            accountId(player), current, replacement, BungeeAuthenticationListener.address(player))
                    .whenComplete((result, error) -> accountCallback(
                            player, result, error, () -> {
                                plugin.clearSessionCookie(player, accountId(player));
                                player.sendMessage(plugin.message(player, "password-changed"));
                            }));
        } catch (IllegalArgumentException error) {
            player.sendMessage(plugin.message(player, "account-action-failed"));
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
    }

    private boolean validPasswordLength(String password) {
        return password.length() >= plugin.openGate().config().minimumPasswordLength()
                && password.length() <= plugin.openGate().config().maximumPasswordLength();
    }

    private void deleteAccount(ProxiedPlayer player, String[] arguments) {
        if (isProtected(player)) {
            player.sendMessage(plugin.message(player, "protected-account"));
            return;
        }
        if (arguments.length != 3 || !arguments[2].equalsIgnoreCase("confirm")) {
            player.sendMessage(plugin.message(player, "usage-account-delete"));
            return;
        }
        var password = arguments[1].toCharArray();
        plugin.openGate().accounts()
                .delete(accountId(player), password, BungeeAuthenticationListener.address(player))
                .whenComplete((result, error) -> accountCallback(player, result, error, () -> {
                    plugin.clearSessionCookie(player, accountId(player));
                    plugin.openGate().sessions().close(player.getUniqueId());
                    player.disconnect(plugin.message(player, "account-deleted"));
                }));
        Arrays.fill(password, '\0');
    }

    private void accountCallback(
            ProxiedPlayer player, AccountActionResult result, Throwable error, Runnable success) {
        if (!player.isConnected()) return;
        if (error != null || result != AccountActionResult.SUCCESS) {
            if (result == AccountActionResult.RATE_LIMITED) {
                player.disconnect(plugin.message(player, "rate-limited"));
            } else if (result == AccountActionResult.SERVICE_BUSY) {
                player.sendMessage(plugin.message(player, "service-busy"));
            } else {
                player.sendMessage(plugin.message(player, "account-action-failed"));
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

    private boolean isTotpEnrollment(ProxiedPlayer player) {
        return plugin.openGate().sessions().find(player.getUniqueId())
                .map(session -> session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT)
                .orElse(false);
    }

    private boolean isCurrent(
            ProxiedPlayer player, dev.lunynt.opengate.auth.AuthenticationSession session) {
        return player.isConnected()
                && plugin.openGate().sessions().find(player.getUniqueId()).orElse(null) == session;
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(new TextComponent(message));
    }

    private java.util.UUID accountId(ProxiedPlayer player) {
        return plugin.openGate().sessions().find(player.getUniqueId())
                .flatMap(dev.lunynt.opengate.auth.AuthenticationSession::identity)
                .map(dev.lunynt.opengate.auth.ResolvedIdentity::playerId)
                .orElseGet(() -> plugin.openGate().identityIds().translate(player.getUniqueId()));
    }

    private boolean isProtected(ProxiedPlayer player) {
        return plugin.openGate().config().protectedAccounts().protects(player::hasPermission);
    }

}
