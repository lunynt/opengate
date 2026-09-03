package dev.lunynt.opengate.bungee;

import java.time.format.DateTimeFormatter;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

final class BungeeAdminCommand extends Command {
    private final OpenGateBungeePlugin plugin;

    BungeeAdminCommand(OpenGateBungeePlugin plugin) {
        super("opengate", "opengate.admin");
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] arguments) {
        if (arguments.length < 2) {
            usage(sender);
            return;
        }
        var actor = sender instanceof ProxiedPlayer player ? player.getUniqueId().toString() : "console";
        plugin.proxy().getScheduler().runAsync(plugin, () -> {
            switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
                case "lookup" -> lookup(sender, arguments[1], actor);
                case "audit" -> audit(sender, arguments, actor);
                case "revoke" -> revoke(sender, arguments[1], actor);
                default -> usage(sender);
            }
        });
    }

    private void lookup(CommandSender sender, String username, String actor) {
        var account = plugin.openGate().admin().lookup(username, actor);
        if (account.isEmpty()) {
            send(sender, "Account not found.");
            return;
        }
        var value = account.orElseThrow();
        send(sender, "OpenGate account " + value.username());
        send(sender, "UUID: " + value.playerId());
        send(sender, "Identity: " + value.identityType());
        send(sender, "Created: " + DateTimeFormatter.ISO_INSTANT.format(value.createdAt()));
        send(sender, "TOTP: " + (value.totpEnabled() ? "enabled" : "disabled"));
    }

    private void audit(CommandSender sender, String[] arguments, String actor) {
        var limit = 10;
        if (arguments.length >= 3) {
            try {
                limit = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                send(sender, "Audit limit must be a number from 1 to 100.");
                return;
            }
        }
        try {
            var records = plugin.openGate().admin().audit(arguments[1], limit, actor);
            send(sender, "Recent OpenGate events for " + arguments[1] + ":");
            for (var record : records) {
                send(sender, DateTimeFormatter.ISO_INSTANT.format(record.occurredAt()) + " " + record.type()
                        + (record.detail() == null ? "" : " " + record.detail()));
            }
        } catch (IllegalArgumentException exception) {
            send(sender, exception.getMessage());
        }
    }

    private void revoke(CommandSender sender, String username, String actor) {
        var account = plugin.openGate().admin().revoke(username, actor);
        if (account.isEmpty()) {
            send(sender, "Account not found.");
            return;
        }
        var value = account.orElseThrow();
        var player = plugin.proxy().getPlayer(value.playerId());
        if (player != null) {
            player.disconnect(new TextComponent("Your OpenGate session was revoked by an administrator."));
        }
        send(sender, "Revoked sessions for " + value.username() + ".");
    }

    private static void usage(CommandSender sender) {
        send(sender, "Usage: /opengate lookup <player> | audit <player> [limit] | revoke <player>");
    }

    private static void send(CommandSender sender, String message) {
        sender.sendMessage(new TextComponent(message));
    }
}
