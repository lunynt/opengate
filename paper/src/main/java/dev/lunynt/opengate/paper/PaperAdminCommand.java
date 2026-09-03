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
    private static final String USAGE = "Usage: /opengate lookup <player> | audit <player> [limit] | revoke <player>";

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
            sender.sendMessage(USAGE);
            return true;
        }
        var actor = sender instanceof org.bukkit.entity.Player player
                ? player.getUniqueId().toString()
                : "console";
        var ownedArguments = arguments.clone();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            AdminResponse response;
            try {
                response = execute(ownedArguments, actor);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("OpenGate admin command failed");
                response = new AdminResponse(List.of("OpenGate could not complete that request."), null);
            }
            var completed = response;
            plugin.getServer().getScheduler().runTask(plugin, () -> deliver(sender, completed));
        });
        return true;
    }

    private AdminResponse execute(String[] arguments, String actor) {
        return switch (arguments[0].toLowerCase(Locale.ROOT)) {
            case "lookup" -> lookup(arguments[1], actor);
            case "audit" -> audit(arguments, actor);
            case "revoke" -> revoke(arguments[1], actor);
            default -> new AdminResponse(List.of(USAGE), null);
        };
    }

    private AdminResponse lookup(String username, String actor) {
        var account = plugin.openGate().admin().lookup(username, actor);
        if (account.isEmpty()) return new AdminResponse(List.of("Account not found."), null);
        var value = account.orElseThrow();
        return new AdminResponse(List.of(
                "OpenGate account " + value.username(),
                "UUID: " + value.playerId(),
                "Identity: " + value.identityType(),
                "Created: " + DateTimeFormatter.ISO_INSTANT.format(value.createdAt()),
                "TOTP: " + (value.totpEnabled() ? "enabled" : "disabled")), null);
    }

    private AdminResponse audit(String[] arguments, String actor) {
        var limit = 10;
        if (arguments.length >= 3) {
            try {
                limit = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                return new AdminResponse(List.of("Audit limit must be a number from 1 to 100."), null);
            }
        }
        try {
            var records = plugin.openGate().admin().audit(arguments[1], limit, actor);
            var lines = new ArrayList<String>();
            lines.add("Recent OpenGate events for " + arguments[1] + ":");
            for (var record : records) {
                lines.add(DateTimeFormatter.ISO_INSTANT.format(record.occurredAt()) + " " + record.type()
                        + (record.detail() == null ? "" : " " + record.detail()));
            }
            return new AdminResponse(List.copyOf(lines), null);
        } catch (IllegalArgumentException exception) {
            return new AdminResponse(List.of(exception.getMessage()), null);
        }
    }

    private AdminResponse revoke(String username, String actor) {
        var account = plugin.openGate().admin().revoke(username, actor);
        if (account.isEmpty()) return new AdminResponse(List.of("Account not found."), null);
        var value = account.orElseThrow();
        return new AdminResponse(List.of("Revoked sessions for " + value.username() + "."), value.playerId());
    }

    private void deliver(CommandSender sender, AdminResponse response) {
        response.lines().forEach(sender::sendMessage);
        if (response.playerToDisconnect() == null) return;
        var online = plugin.getServer().getPlayer(response.playerToDisconnect());
        if (online != null) online.kickPlayer("Your OpenGate session was revoked by an administrator.");
    }

    private record AdminResponse(List<String> lines, UUID playerToDisconnect) {}
}
