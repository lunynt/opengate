package dev.lunynt.opengate.bungee;

import java.time.format.DateTimeFormatter;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

final class BungeeAdminCommand extends Command {
    private final OpenGateBungeePlugin plugin;

    BungeeAdminCommand(OpenGateBungeePlugin plugin, String... aliases) {
        super("opengate", "opengate.admin", aliases);
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
                case "recover" -> recover(sender, arguments, actor);
                default -> usage(sender);
            }
        });
    }

    private void recover(CommandSender sender, String[] arguments, String actor) {
        if (!sender.hasPermission("opengate.admin.recover")) {
            send(sender, "admin-no-permission");
            return;
        }
        if (arguments.length != 3) {
            usage(sender);
            return;
        }
        var target = plugin.proxy().getPlayer(arguments[1]);
        var protectedAccount = plugin.openGate().accounts().find(arguments[1])
                .map(account -> plugin.openGate().config().protectedAccounts()
                        .protects(account.username(), account.playerId()))
                .orElse(false);
        if ((protectedAccount || target != null
                && plugin.openGate().config().protectedAccounts().protects(target::hasPermission))
                && !sender.hasPermission("opengate.admin.recover.protected")) {
            send(sender, "protected-account");
            return;
        }
        var password = arguments[2].toCharArray();
        try {
            var account = plugin.openGate().admin().recover(arguments[1], password, actor);
            if (account.isEmpty()) {
                send(sender, "admin-account-not-found");
                return;
            }
            sendValue(sender, "admin-recovered", account.orElseThrow().username() + ".");
        } catch (IllegalArgumentException exception) {
            send(sender, exception.getMessage().startsWith("only offline")
                    ? "admin-recovery-offline-only" : "password-policy-invalid");
        } catch (RuntimeException exception) {
            send(sender, "admin-request-failed");
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    private void lookup(CommandSender sender, String username, String actor) {
        var account = plugin.openGate().admin().lookup(username, actor);
        if (account.isEmpty()) {
            send(sender, "admin-account-not-found");
            return;
        }
        var value = account.orElseThrow();
        sendValue(sender, "admin-account-header", value.username());
        sendValue(sender, "admin-uuid", value.playerId().toString());
        sendValue(sender, "admin-identity", value.identityType().toString());
        sendValue(sender, "admin-created", DateTimeFormatter.ISO_INSTANT.format(value.createdAt()));
        sendValue(sender, "admin-totp", messageText(sender,
                value.totpEnabled() ? "admin-enabled" : "admin-disabled"));
    }

    private void audit(CommandSender sender, String[] arguments, String actor) {
        var limit = 10;
        if (arguments.length >= 3) {
            try {
                limit = Integer.parseInt(arguments[2]);
            } catch (NumberFormatException exception) {
                send(sender, "admin-audit-limit");
                return;
            }
        }
        try {
            var records = plugin.openGate().admin().audit(arguments[1], limit, actor);
            sendValue(sender, "admin-audit-header", arguments[1] + ":");
            for (var record : records) {
                sender.sendMessage(new TextComponent(DateTimeFormatter.ISO_INSTANT.format(record.occurredAt())
                        + " " + record.type() + (record.detail() == null ? "" : " " + record.detail())));
            }
        } catch (IllegalArgumentException exception) {
            send(sender, "admin-account-not-found");
        }
    }

    private void revoke(CommandSender sender, String username, String actor) {
        java.util.Optional<dev.lunynt.opengate.admin.AccountSummary> account;
        try {
            account = plugin.openGate().admin().revoke(username, actor);
        } catch (RuntimeException exception) {
            send(sender, "admin-revocation-failed");
            return;
        }
        if (account.isEmpty()) {
            send(sender, "admin-account-not-found");
            return;
        }
        var value = account.orElseThrow();
        var player = plugin.proxy().getPlayer(value.playerId());
        if (player != null) {
            player.disconnect(plugin.message(player, "session-revoked"));
        }
        sendValue(sender, "admin-revoked", value.username() + ".");
    }

    private void usage(CommandSender sender) {
        send(sender, "admin-usage");
    }

    private void send(CommandSender sender, String key) {
        sender.sendMessage(new TextComponent(messageText(sender, key)));
    }

    private void sendValue(CommandSender sender, String key, String value) {
        sender.sendMessage(new TextComponent(messageText(sender, key) + value));
    }

    private String messageText(CommandSender sender, String key) {
        var raw = sender instanceof ProxiedPlayer player
                ? plugin.openGate().messages().get(player.getLocale(), key)
                : plugin.openGate().messages().get(key);
        return raw.replace('&', '§');
    }
}
