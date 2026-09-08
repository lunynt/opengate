package dev.lunynt.opengate.paper;

import dev.lunynt.opengate.OpenGate;
import dev.lunynt.opengate.identity.FloodgateApiIdentity;
import dev.lunynt.opengate.identity.FloodgateIdentity;
import org.bukkit.plugin.java.JavaPlugin;

public final class OpenGatePaperPlugin extends JavaPlugin {
    private final org.bukkit.NamespacedKey sessionCookie = new org.bukkit.NamespacedKey(this, "session");
    private OpenGate openGate;
    private FloodgateIdentity floodgate = FloodgateIdentity.unavailable();

    @Override
    public void onEnable() {
        openGate = OpenGate.create(getDataFolder().toPath());
        openGate.sessions().onInvalidated(connectionId -> getServer().getScheduler().runTask(this, () -> {
            var player = getServer().getPlayer(connectionId);
            if (player != null) player.kickPlayer(org.bukkit.ChatColor.translateAlternateColorCodes(
                    '&', openGate.messages().get(java.util.Locale.forLanguageTag(player.getLocale().replace('_', '-')),
                            "session-revoked")));
        }));
        getServer().getServicesManager().register(
                dev.lunynt.opengate.api.OpenGateApi.class,
                openGate.api(),
                this,
                org.bukkit.plugin.ServicePriority.Normal);
        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            try {
                floodgate = new FloodgateApiIdentity();
                getLogger().info("Floodgate integration enabled");
            } catch (LinkageError | RuntimeException exception) {
                getLogger().warning("Floodgate API unavailable; Bedrock authentication will fail closed");
            }
        }
        getServer().getPluginManager().registerEvents(new PaperAuthenticationListener(this), this);
        var commands = new PaperAuthenticationCommand(this);
        configureCommand("login", commands);
        configureCommand("register", commands);
        configureCommand("totp", commands);
        configureCommand("2fa", commands);
        configureCommand("account", commands);
        configureCommand("opengate", new PaperAdminCommand(this));
        getLogger().info("OpenGate authentication engine enabled on Paper");
    }

    private void configureCommand(String name, org.bukkit.command.CommandExecutor executor) {
        var command = java.util.Objects.requireNonNull(getCommand(name));
        command.setAliases(openGate.config().commands().aliases(name));
        command.setExecutor(executor);
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (openGate != null) {
            openGate.close();
        }
    }

    public OpenGate openGate() {
        return openGate;
    }

    public dev.lunynt.opengate.api.OpenGateApi api() {
        return openGate.api();
    }

    FloodgateIdentity floodgate() {
        return floodgate;
    }

    org.bukkit.NamespacedKey sessionCookie() {
        return sessionCookie;
    }

    void issueSessionCookie(org.bukkit.entity.Player player, java.util.UUID accountId) {
        openGate.cookieSessions().issue(accountId).thenAccept(token -> token.ifPresent(value ->
                getServer().getScheduler().runTask(this, () -> {
                    if (player.isOnline()) player.storeCookie(sessionCookie, value);
                })));
    }

    java.util.concurrent.CompletableFuture<Void> clearSessionCookie(
            org.bukkit.entity.Player player, java.util.UUID accountId) {
        if (player.isOnline()) {
            getServer().getScheduler().runTask(this, () -> {
                if (player.isOnline()) player.storeCookie(sessionCookie, new byte[0]);
            });
        }
        return openGate.cookieSessions().revokeAll(accountId);
    }

    void showAuthenticationDialog(org.bukkit.entity.Player player, boolean registration) {
        if (!openGate.config().minecraftDialogsEnabled()) return;
        var command = registration ? "/register " : "/login ";
        var locale = java.util.Locale.forLanguageTag(player.getLocale().replace('_', '-'));
        var title = new net.md_5.bungee.api.chat.TextComponent(openGate.messages().get(
                locale, registration ? "dialog-register-title" : "dialog-login-title"));
        var label = new net.md_5.bungee.api.chat.TextComponent(openGate.messages().get(
                locale, registration ? "dialog-register-button" : "dialog-login-button"));
        try {
            var actionType = Class.forName("net.md_5.bungee.api.dialog.action.Action");
            var staticActionType = Class.forName("net.md_5.bungee.api.dialog.action.StaticAction");
            var buttonType = Class.forName("net.md_5.bungee.api.dialog.action.ActionButton");
            var baseType = Class.forName("net.md_5.bungee.api.dialog.DialogBase");
            var dialogType = Class.forName("net.md_5.bungee.api.dialog.Dialog");
            var noticeType = Class.forName("net.md_5.bungee.api.dialog.NoticeDialog");
            var click = new net.md_5.bungee.api.chat.ClickEvent(
                    net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, command);
            var action = staticActionType.getConstructor(net.md_5.bungee.api.chat.ClickEvent.class)
                    .newInstance(click);
            var button = buttonType.getConstructor(net.md_5.bungee.api.chat.BaseComponent.class, actionType)
                    .newInstance(label, action);
            var base = baseType.getConstructor(net.md_5.bungee.api.chat.BaseComponent.class).newInstance(title);
            var notice = noticeType.getConstructor(baseType, buttonType).newInstance(base, button);
            player.getClass().getMethod("showDialog", dialogType).invoke(player, notice);
        } catch (ReflectiveOperationException | LinkageError | IllegalStateException
                | UnsupportedOperationException ignored) {
        }
    }
}
