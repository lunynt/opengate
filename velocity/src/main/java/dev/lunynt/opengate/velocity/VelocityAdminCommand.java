package dev.lunynt.opengate.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.kyori.adventure.text.Component;

final class VelocityAdminCommand implements SimpleCommand {
    private static final String PERMISSION = "opengate.admin";
    private final OpenGateVelocityPlugin plugin;

    VelocityAdminCommand(OpenGateVelocityPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }

    @Override
    public void execute(Invocation invocation) {
        var arguments = invocation.arguments();
        if (arguments.length < 2) {
            usage(invocation.source());
            return;
        }
        var actor = invocation.source() instanceof Player player
                ? player.getUniqueId().toString()
                : "console";
        plugin.server().getScheduler().buildTask(plugin, () -> {
            switch (arguments[0].toLowerCase(java.util.Locale.ROOT)) {
                case "lookup" -> lookup(invocation.source(), arguments[1], actor);
                case "audit" -> audit(invocation.source(), arguments, actor);
                case "revoke" -> revoke(invocation.source(), arguments[1], actor);
                default -> usage(invocation.source());
            }
        }).schedule();
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return List.of("lookup", "audit", "revoke");
        }
        return List.of();
    }

    private void lookup(CommandSource source, String username, String actor) {
        var account = plugin.openGate().admin().lookup(username, actor);
        if (account.isEmpty()) {
            source.sendMessage(Component.text("Account not found."));
            return;
        }
        var value = account.orElseThrow();
        source.sendMessage(Component.text("OpenGate account " + value.username()));
        source.sendMessage(Component.text("UUID: " + value.playerId()));
        source.sendMessage(Component.text("Identity: " + value.identityType()));
        source.sendMessage(Component.text("Created: " + DateTimeFormatter.ISO_INSTANT.format(value.createdAt())));
        source.sendMessage(Component.text("TOTP: " + (value.totpEnabled() ? "enabled" : "disabled")));
    }

    private void audit(CommandSource source, String[] arguments, String actor) {
        var limit = 10;
        if (arguments.length >= 3) {
            try {
                limit = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                source.sendMessage(Component.text("Audit limit must be a number from 1 to 100."));
                return;
            }
        }
        try {
            var records = plugin.openGate().admin().audit(arguments[1], limit, actor);
            source.sendMessage(Component.text("Recent OpenGate events for " + arguments[1] + ":"));
            for (var record : records) {
                source.sendMessage(Component.text(
                        DateTimeFormatter.ISO_INSTANT.format(record.occurredAt()) + " " + record.type()
                                + (record.detail() == null ? "" : " " + record.detail())));
            }
        } catch (IllegalArgumentException exception) {
            source.sendMessage(Component.text(exception.getMessage()));
        }
    }

    private void revoke(CommandSource source, String username, String actor) {
        var account = plugin.openGate().admin().revoke(username, actor);
        if (account.isEmpty()) {
            source.sendMessage(Component.text("Account not found."));
            return;
        }
        var value = account.orElseThrow();
        plugin.server().getPlayer(value.playerId()).ifPresent(player ->
                player.disconnect(Component.text("Your OpenGate session was revoked by an administrator.")));
        source.sendMessage(Component.text("Revoked sessions for " + value.username() + "."));
    }

    private static void usage(CommandSource source) {
        source.sendMessage(Component.text(
                "Usage: /opengate lookup <player> | audit <player> [limit] | revoke <player>"));
    }
}
