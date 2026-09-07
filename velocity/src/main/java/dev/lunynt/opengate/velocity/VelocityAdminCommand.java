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
            source.sendMessage(message(source, "admin-account-not-found"));
            return;
        }
        var value = account.orElseThrow();
        source.sendMessage(message(source, "admin-account-header").append(Component.text(value.username())));
        source.sendMessage(message(source, "admin-uuid").append(Component.text(value.playerId().toString())));
        source.sendMessage(message(source, "admin-identity").append(Component.text(value.identityType().toString())));
        source.sendMessage(message(source, "admin-created").append(
                Component.text(DateTimeFormatter.ISO_INSTANT.format(value.createdAt()))));
        source.sendMessage(message(source, "admin-totp").append(message(source,
                value.totpEnabled() ? "admin-enabled" : "admin-disabled")));
    }

    private void audit(CommandSource source, String[] arguments, String actor) {
        var limit = 10;
        if (arguments.length >= 3) {
            try {
                limit = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                source.sendMessage(message(source, "admin-audit-limit"));
                return;
            }
        }
        try {
            var records = plugin.openGate().admin().audit(arguments[1], limit, actor);
            source.sendMessage(message(source, "admin-audit-header")
                    .append(Component.text(arguments[1] + ":")));
            for (var record : records) {
                source.sendMessage(Component.text(
                        DateTimeFormatter.ISO_INSTANT.format(record.occurredAt()) + " " + record.type()
                                + (record.detail() == null ? "" : " " + record.detail())));
            }
        } catch (IllegalArgumentException exception) {
            source.sendMessage(message(source, "admin-account-not-found"));
        }
    }

    private void revoke(CommandSource source, String username, String actor) {
        java.util.Optional<dev.lunynt.opengate.admin.AccountSummary> account;
        try {
            account = plugin.openGate().admin().revoke(username, actor);
        } catch (RuntimeException exception) {
            source.sendMessage(message(source, "admin-revocation-failed"));
            return;
        }
        if (account.isEmpty()) {
            source.sendMessage(message(source, "admin-account-not-found"));
            return;
        }
        var value = account.orElseThrow();
        plugin.server().getPlayer(value.playerId()).ifPresent(player ->
                player.disconnect(plugin.message(player, "session-revoked")));
        source.sendMessage(message(source, "admin-revoked").append(Component.text(value.username() + ".")));
    }

    private void usage(CommandSource source) {
        source.sendMessage(message(source, "admin-usage"));
    }

    private Component message(CommandSource source, String key) {
        return source instanceof Player player ? plugin.message(player, key) : plugin.message(key);
    }
}
