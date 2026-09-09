package dev.lunynt.opengate.paper;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

final class PaperAdminCommand implements CommandExecutor {
    private static final String PERMISSION = "opengate.admin";
    private final OpenGatePaperPlugin plugin;

    PaperAdminCommand(OpenGatePaperPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] arguments) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(message(sender, "admin-no-permission"));
            return true;
        }
        if (arguments.length < 2) {
            sender.sendMessage(message(sender, "admin-usage"));
            return true;
        }
        if (arguments[0].equalsIgnoreCase("recover")) {
            if (!sender.hasPermission("opengate.admin.recover")) {
                sender.sendMessage(message(sender, "admin-no-permission"));
                return true;
            }
            var target = getTarget(arguments[1]);
            if (target != null
                    && plugin.openGate().config().protectedAccounts().protects(target::hasPermission)
                    && !sender.hasPermission("opengate.admin.recover.protected")) {
                sender.sendMessage(message(sender, "protected-account"));
                return true;
            }
        }
        var actor = sender instanceof org.bukkit.entity.Player player
                ? player.getUniqueId().toString()
                : "console";
        var locale = sender instanceof org.bukkit.entity.Player player
                ? Locale.forLanguageTag(player.getLocale().replace('_', '-'))
                : null;
        var ownedArguments = arguments.clone();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            AdminResponse response;
            try {
                response = execute(ownedArguments, actor, locale);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("OpenGate admin command failed");
                response = new AdminResponse(List.of(message(sender, "admin-request-failed")), null);
            }
            var completed = response;
            plugin.getServer().getScheduler().runTask(plugin, () -> deliver(sender, completed));
        });
        return true;
    }

    private AdminResponse execute(String[] arguments, String actor, Locale locale) {
        return switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "lookup" -> lookup(arguments[1], actor, locale);
            case "audit" -> audit(arguments, actor, locale);
            case "revoke" -> revoke(arguments[1], actor, locale);
            case "recover" -> recover(arguments, actor, locale);
            default -> new AdminResponse(List.of(message(locale, "admin-usage")), null);
        };
    }

    private AdminResponse recover(String[] arguments, String actor, Locale locale) {
        if (arguments.length != 3) return new AdminResponse(List.of(message(locale, "admin-usage")), null);
        var password = arguments[2].toCharArray();
        try {
            var account = plugin.openGate().admin().recover(arguments[1], password, actor);
            if (account.isEmpty()) return new AdminResponse(
                    List.of(message(locale, "admin-account-not-found")), null);
            var value = account.orElseThrow();
            return new AdminResponse(
                    List.of(message(locale, "admin-recovered") + value.username() + "."), value.playerId());
        } catch (IllegalArgumentException exception) {
            return new AdminResponse(List.of(message(locale, exception.getMessage().startsWith("only offline")
                    ? "admin-recovery-offline-only" : "password-policy-invalid")), null);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private org.bukkit.entity.Player getTarget(String username) {
        return plugin.getServer().getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(username))
                .findFirst().orElse(null);
    }

    private AdminResponse lookup(String username, String actor, Locale locale) {
        var account = plugin.openGate().admin().lookup(username, actor);
        if (account.isEmpty()) return new AdminResponse(List.of(message(locale, "admin-account-not-found")), null);
        var value = account.orElseThrow();
        return new AdminResponse(List.of(
                message(locale, "admin-account-header") + value.username(),
                message(locale, "admin-uuid") + value.playerId(),
                message(locale, "admin-identity") + value.identityType(),
                message(locale, "admin-created") + DateTimeFormatter.ISO_INSTANT.format(value.createdAt()),
                message(locale, "admin-totp")
                        + message(locale, value.totpEnabled() ? "admin-enabled" : "admin-disabled")), null);
    }

    private AdminResponse audit(String[] arguments, String actor, Locale locale) {
        var limit = 10;
        if (arguments.length >= 3) {
            try {
                limit = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                return new AdminResponse(List.of(message(locale, "admin-audit-limit")), null);
            }
        }
        try {
            var records = plugin.openGate().admin().audit(arguments[1], limit, actor);
            var lines = new ArrayList<String>();
            lines.add(message(locale, "admin-audit-header") + arguments[1] + ":");
            for (var record : records) {
                lines.add(DateTimeFormatter.ISO_INSTANT.format(record.occurredAt()) + " " + record.type()
                        + (record.detail() == null ? "" : " " + record.detail()));
            }
            return new AdminResponse(List.copyOf(lines), null);
        } catch (IllegalArgumentException exception) {
            return new AdminResponse(List.of(message(locale, "admin-account-not-found")), null);
        }
    }

    private AdminResponse revoke(String username, String actor, Locale locale) {
        java.util.Optional<dev.lunynt.opengate.admin.AccountSummary> account;
        try {
            account = plugin.openGate().admin().revoke(username, actor);
        } catch (RuntimeException exception) {
            return new AdminResponse(
                    List.of(message(locale, "admin-revocation-failed")), null);
        }
        if (account.isEmpty()) return new AdminResponse(List.of(message(locale, "admin-account-not-found")), null);
        var value = account.orElseThrow();
        return new AdminResponse(List.of(message(locale, "admin-revoked") + value.username() + "."), value.playerId());
    }

    private void deliver(CommandSender sender, AdminResponse response) {
        response.lines().forEach(sender::sendMessage);
        if (response.playerToDisconnect() == null) return;
        var online = plugin.getServer().getPlayer(response.playerToDisconnect());
        if (online != null) online.kickPlayer(message(online, "session-revoked"));
    }

    private String message(Locale locale, String key) {
        return color(locale == null
                ? plugin.openGate().messages().get(key)
                : plugin.openGate().messages().get(locale, key));
    }

    private String message(CommandSender sender, String key) {
        if (sender instanceof org.bukkit.entity.Player player) return message(player, key);
        return color(plugin.openGate().messages().get(key));
    }

    private String message(org.bukkit.entity.Player player, String key) {
        var locale = Locale.forLanguageTag(player.getLocale().replace('_', '-'));
        return color(plugin.openGate().messages().get(locale, key));
    }

    private static String color(String value) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', value);
    }

    private record AdminResponse(List<String> lines, UUID playerToDisconnect) {}
}
