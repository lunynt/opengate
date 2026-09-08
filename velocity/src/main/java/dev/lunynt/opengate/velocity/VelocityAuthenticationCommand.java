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
        ACCOUNT,
        PREMIUM,
        CRACKED
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
            invocation.source().sendMessage(Component.text(plugin.openGate().messages().get("players-only")));
            return;
        }
        switch (type) {
            case REGISTER -> register(player, invocation.arguments());
            case LOGIN -> login(player, invocation.arguments());
            case TOTP -> totp(player, invocation.arguments());
            case MANAGE_TOTP -> manageTotp(player, invocation.arguments());
            case ACCOUNT -> manageAccount(player, invocation.arguments());
            case PREMIUM -> changeIdentity(player, invocation.arguments(), IdentityType.PREMIUM);
            case CRACKED -> changeIdentity(player, invocation.arguments(), IdentityType.OFFLINE);
        }
    }

    private void changeIdentity(Player player, String[] arguments, IdentityType target) {
        if (!isReleased(player) || arguments.length != 1) {
            player.sendMessage(message(player, target == IdentityType.PREMIUM ? "usage-premium" : "usage-cracked"));
            return;
        }
        if (isProtected(player)) {
            player.sendMessage(message(player, "protected-account"));
            return;
        }
        var account = plugin.openGate().accounts().find(accountId(player)).orElse(null);
        if (account == null) {
            player.sendMessage(message(player, "account-action-failed"));
            return;
        }
        if (account.identityType() == target) {
            player.sendMessage(message(player,
                    target == IdentityType.PREMIUM ? "premium-already-enabled" : "cracked-already-enabled"));
            return;
        }
        plugin.server().getScheduler().buildTask(plugin, () -> {
            if (target == IdentityType.PREMIUM) {
                var profile = plugin.openGate().identities().premiumProfile(account.username());
                if (profile.status() != dev.lunynt.opengate.identity.ProfileLookupResult.Status.FOUND) {
                    player.sendMessage(message(player, profile.status()
                            == dev.lunynt.opengate.identity.ProfileLookupResult.Status.NOT_FOUND
                            ? "premium-profile-not-found" : "premium-profile-unavailable"));
                    return;
                }
                if (!profile.profile().username().equals(account.username())) {
                    player.sendMessage(message(player, "premium-name-mismatch"));
                    return;
                }
            }
            var password = arguments[0].toCharArray();
            try {
                plugin.openGate().accounts().changeIdentityType(account.playerId(), password, target, address(player))
                        .whenComplete((result, error) -> accountCallback(player, result, error, () -> {
                        plugin.clearSessionCookie(player, account.playerId());
                        plugin.openGate().sessions().close(player.getUniqueId());
                        player.disconnect(message(player,
                                target == IdentityType.PREMIUM ? "premium-enabled" : "cracked-enabled"));
                        }));
            } finally {
                Arrays.fill(password, '\0');
            }
        }).schedule();
    }

    private void register(Player player, String[] arguments) {
        if (arguments.length != 2 || !arguments[0].equals(arguments[1])) {
            player.sendMessage(message(player, "usage-register"));
            return;
        }
        if (!validPasswordLength(arguments[0])) {
            player.sendMessage(message(player, "password-policy-invalid"));
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(message(player, "registration-not-required"));
            return;
        }
        var password = arguments[0].toCharArray();
        var address = player.getRemoteAddress().getAddress().getHostAddress();
        session.beginRegistration();
        plugin.openGate()
                .accounts()
                .register(accountId(player), player.getUsername(), session.identity().orElseThrow().type(), password, address)
                .whenComplete((account, error) -> {
                    if (!isCurrent(player, session)) return;
                    if (error != null) {
                        session.registrationFailed();
                        player.sendMessage(message(player, "account-action-failed"));
                        return;
                    }
                    session.register();
                    if (session.state() == AuthenticationState.AUTHENTICATED) {
                        session.release();
                        plugin.issueSessionCookie(player, accountId(player));
                        player.sendMessage(message(player, "registration-success"));
                        plugin.connectToLobby(player);
                    } else {
                        player.sendMessage(message(player, session.state() == AuthenticationState.AWAITING_TOTP
                                ? "totp-prompt" : "totp-enrollment-required"));
                    }
                });
        Arrays.fill(password, '\0');
    }

    private void login(Player player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(message(player, "usage-login"));
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_PASSWORD) {
            player.sendMessage(message(player, "password-not-required"));
            return;
        }
        var password = arguments[0].toCharArray();
        var address = player.getRemoteAddress().getAddress().getHostAddress();
        session.beginPasswordVerification();
        plugin.openGate().accounts().authenticate(accountId(player), password, address).whenComplete((result, error) -> {
            if (!isCurrent(player, session)) return;
            if (result == AuthenticationResult.RATE_LIMITED) {
                session.close();
                player.disconnect(message(player, "rate-limited"));
                return;
            }
            if (result == AuthenticationResult.SERVICE_BUSY) {
                session.close();
                player.disconnect(message(player, "service-busy"));
                return;
            }
            if (error != null || result != AuthenticationResult.SUCCESS) {
                if (session.rejectPassword(plugin.openGate().config().maximumLoginAttempts())) {
                    player.disconnect(message(player, "too-many-attempts"));
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
                plugin.connectToLobby(player);
            } else if (session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT) {
                player.sendMessage(message(player, "totp-enrollment-required"));
            } else {
                player.sendMessage(message(player, "totp-prompt"));
            }
        });
        Arrays.fill(password, '\0');
    }

    private void totp(Player player, String[] arguments) {
        if (arguments.length != 1) {
            player.sendMessage(message(player, "usage-totp"));
            return;
        }
        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElse(null);
        if (session == null || session.state() != AuthenticationState.AWAITING_TOTP) {
            player.sendMessage(message(player, "totp-not-required"));
            return;
        }
        session.beginTotpVerification();
        var code = arguments[0];
        plugin.server().getScheduler().buildTask(plugin, () -> {
            var account = plugin.openGate().accounts().find(accountId(player)).orElse(null);
            var verified = account != null && plugin.openGate().totp().verify(account, code, address(player));
            if (!player.isActive() || plugin.openGate().sessions().find(player.getUniqueId()).orElse(null) != session) return;
            if (!verified) {
                if (session.rejectTotp(plugin.openGate().config().maximumLoginAttempts())) {
                    player.disconnect(message(player, "too-many-attempts"));
                } else {
                    player.sendMessage(message(player, "totp-invalid"));
                }
                return;
            }
            session.acceptTotp();
            session.release();
            plugin.issueSessionCookie(player, accountId(player));
            player.sendMessage(message(player, "totp-success"));
            plugin.connectToLobby(player);
        }).schedule();
    }

    private void manageTotp(Player player, String[] arguments) {
        var enrollment = isTotpEnrollment(player);
        if (!plugin.openGate().config().protectedAccounts().permitsTotpAction(
                player::hasPermission, enrollment, arguments.length == 0 ? "" : arguments[0])) {
            player.sendMessage(message(player, "protected-account"));
            return;
        }
        if ((!isReleased(player) && !enrollment) || arguments.length == 0) {
            player.sendMessage(message(player, "usage-2fa"));
            return;
        }
        switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "setup" -> verifyPasswordThen(player, arguments, () -> {
                var account = plugin.openGate().accounts().find(accountId(player)).orElseThrow();
                var uri = plugin.openGate().totp().begin(account);
                player.sendMessage(message(player, "totp-setup").append(Component.space()).append(
                        Component.text(uri).clickEvent(net.kyori.adventure.text.event.ClickEvent.copyToClipboard(uri))));
            });
            case "confirm" -> {
                if (arguments.length != 2) {
                    player.sendMessage(message(player, "totp-invalid"));
                    return;
                }
                plugin.server().getScheduler().buildTask(plugin, () -> {
                    var confirmed = plugin.openGate().totp().confirm(accountId(player), arguments[1]);
                    if (confirmed && isTotpEnrollment(player)) {
                        var session = plugin.openGate().sessions().find(player.getUniqueId()).orElseThrow();
                        session.completeTotpEnrollment();
                        session.release();
                        plugin.issueSessionCookie(player, accountId(player));
                        plugin.connectToLobby(player);
                    } else if (confirmed) {
                        plugin.clearSessionCookie(player, accountId(player));
                    }
                    player.sendMessage(message(player, confirmed ? "totp-enabled" : "totp-invalid"));
                }).schedule();
            }
            case "disable" -> verifyPasswordThen(player, arguments, () -> {
                var account = plugin.openGate().accounts().find(accountId(player)).orElseThrow();
                plugin.openGate().totp().disable(account);
                plugin.clearSessionCookie(player, accountId(player));
                player.sendMessage(message(player, "totp-disabled"));
            });
            default -> player.sendMessage(message(player, "usage-2fa"));
        }
    }

    private void verifyPasswordThen(Player player, String[] arguments, Runnable action) {
        if (arguments.length != 2) {
            player.sendMessage(message(player, "current-password-required"));
            return;
        }
        var password = arguments[1].toCharArray();
        var address = player.getRemoteAddress().getAddress().getHostAddress();
        plugin.openGate().accounts().authenticate(accountId(player), password, address).whenComplete((result, error) -> {
            if (!player.isActive()) return;
            if (error != null || result != AuthenticationResult.SUCCESS) {
                player.sendMessage(message(player, "incorrect-password"));
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

    private boolean isTotpEnrollment(Player player) {
        return plugin.openGate().sessions().find(player.getUniqueId())
                .map(session -> session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT)
                .orElse(false);
    }

    private boolean isCurrent(Player player, dev.lunynt.opengate.auth.AuthenticationSession session) {
        return player.isActive()
                && plugin.openGate().sessions().find(player.getUniqueId()).orElse(null) == session;
    }

    private void manageAccount(Player player, String[] arguments) {
        if (!isReleased(player) || arguments.length == 0) {
            player.sendMessage(message(player, "usage-account"));
            return;
        }
        switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "password" -> changePassword(player, arguments);
            case "logout" -> {
                plugin.clearSessionCookie(player, accountId(player)).whenComplete((ignored, error) ->
                        plugin.server().getScheduler().buildTask(plugin, () -> {
                            if (!player.isActive()) return;
                            if (error != null) {
                                player.sendMessage(message(player, "account-action-failed"));
                                return;
                            }
                            plugin.openGate().sessions().close(player.getUniqueId());
                            player.disconnect(message(player, "logged-out"));
                        }).schedule());
            }
            case "delete" -> deleteAccount(player, arguments);
            default -> player.sendMessage(message(player, "usage-account"));
        }
    }

    private void changePassword(Player player, String[] arguments) {
        if (isProtected(player)) {
            player.sendMessage(message(player, "protected-account"));
            return;
        }
        if (arguments.length != 3) {
            player.sendMessage(message(player, "usage-account-password"));
            return;
        }
        if (!validPasswordLength(arguments[2])) {
            player.sendMessage(message(player, "password-policy-invalid"));
            return;
        }
        var current = arguments[1].toCharArray();
        var replacement = arguments[2].toCharArray();
        try {
            plugin.openGate().accounts()
                    .changePassword(accountId(player), current, replacement, address(player))
                    .whenComplete((result, error) -> accountCallback(player, result, error, () ->
                            {
                                plugin.clearSessionCookie(player, accountId(player));
                                player.sendMessage(message(player, "password-changed"));
                            }));
        } catch (IllegalArgumentException error) {
            player.sendMessage(message(player, "account-action-failed"));
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
        }
    }

    private boolean validPasswordLength(String password) {
        return password.length() >= plugin.openGate().config().minimumPasswordLength()
                && password.length() <= plugin.openGate().config().maximumPasswordLength();
    }

    private void deleteAccount(Player player, String[] arguments) {
        if (isProtected(player)) {
            player.sendMessage(message(player, "protected-account"));
            return;
        }
        if (arguments.length != 3 || !arguments[2].equalsIgnoreCase("confirm")) {
            player.sendMessage(message(player, "usage-account-delete"));
            return;
        }
        var password = arguments[1].toCharArray();
        plugin.openGate().accounts().delete(accountId(player), password, address(player))
                .whenComplete((result, error) -> accountCallback(player, result, error, () -> {
                    plugin.clearSessionCookie(player, accountId(player));
                    plugin.openGate().sessions().close(player.getUniqueId());
                    player.disconnect(message(player, "account-deleted"));
                }));
        Arrays.fill(password, '\0');
    }

    private void accountCallback(
            Player player, AccountActionResult result, Throwable error, Runnable success) {
        if (!player.isActive()) return;
        if (error != null || result != AccountActionResult.SUCCESS) {
            if (result == AccountActionResult.RATE_LIMITED) {
                player.disconnect(message(player, "rate-limited"));
            } else if (result == AccountActionResult.SERVICE_BUSY) {
                player.sendMessage(message(player, "service-busy"));
            } else {
                player.sendMessage(message(player, "account-action-failed"));
            }
            return;
        }
        success.run();
    }

    private static String address(Player player) {
        return player.getRemoteAddress().getAddress().getHostAddress();
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

    private Component message(Player player, String key) {
        return plugin.message(player, key);
    }
}
