package dev.lunynt.opengate.paper;

import java.time.format.DateTimeFormatter;
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
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (arguments.length < 2) {
            usage(sender);
            return true;
        }
        var actor = sender instanceof org.bukkit.entity.Player player
                ? player.getUniqueId().toString()
                : "console";
        return switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
            case "lookup" -> lookup(sender, arguments[1], actor);
            case "audit" -> audit(sender, arguments, actor);
            case "revoke" -> revoke(sender, arguments[1], actor);
            default -> {
                usage(sender);
                yield true;
            }
        };
    }

    private boolean lookup(CommandSender sender, String username, String actor) {
        var account = plugin.openGate().admin().lookup(username, actor);
        if (account.isEmpty()) {
            sender.sendMessage("Account not found.");
            return true;
        }
        var value = account.orElseThrow();
        sender.sendMessage("OpenGate account " + value.username());
        sender.sendMessage("UUID: " + value.playerId());
        sender.sendMessage("Identity: " + value.identityType());
        sender.sendMessage("Created: " + DateTimeFormatter.ISO_INSTANT.format(value.createdAt()));
        sender.sendMessage("Last authentication: "
                + (value.lastAuthenticatedAt() == null
                        ? "never"
                        : DateTimeFormatter.ISO_INSTANT.format(value.lastAuthenticatedAt())));
        sender.sendMessage("TOTP: " + (value.totpSecret() != null ? "enabled" : "disabled"));
        return true;
    }

    private boolean audit(CommandSender sender, String[] arguments, String actor) {
        var limit = 10;
        if (arguments.length >= 3) {
            try {
                limit = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                sender.sendMessage("Audit limit must be a number from 1 to 100.");
                return true;
            }
        }
        try {
            var records = plugin.openGate().admin().audit(arguments[1], limit, actor);
            sender.sendMessage("Recent OpenGate events for " + arguments[1] + ":");
            for (var record : records) {
                sender.sendMessage(DateTimeFormatter.ISO_INSTANT.format(record.occurredAt()) + " " + record.type()
                        + (record.detail() == null ? "" : " " + record.detail()));
            }
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(exception.getMessage());
        }
        return true;
    }

    private boolean revoke(CommandSender sender, String username, String actor) {
        var account = plugin.openGate().admin().revoke(username, actor);
        if (account.isEmpty()) {
            sender.sendMessage("Account not found.");
            return true;
        }
        var value = account.orElseThrow();
        var online = plugin.getServer().getPlayer(value.playerId());
        if (online != null) {
            online.kickPlayer("Your OpenGate session was revoked by an administrator.");
        }
        sender.sendMessage("Revoked sessions for " + value.username() + ".");
        return true;
    }

    private static void usage(CommandSender sender) {
        sender.sendMessage("Usage: /opengate lookup <player> | audit <player> [limit] | revoke <player>");
    }
}
