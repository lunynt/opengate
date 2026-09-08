package dev.lunynt.opengate.paper;

import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.ResolvedIdentity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

final class PaperAuthenticationListener implements Listener {
    private final OpenGatePaperPlugin plugin;

    PaperAuthenticationListener(OpenGatePaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    @SuppressWarnings("deprecation")
    public void onJoin(PlayerJoinEvent event) {
        event.setJoinMessage(null);
        var player = event.getPlayer();
        var playerId = player.getUniqueId();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        plugin.openGate().sessions().close(playerId);
        var session = plugin.openGate().sessions().open(playerId);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                byte[] cookie;
                try {
                    cookie = player.retrieveCookie(plugin.sessionCookie()).get(3, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException exception) {
                    cookie = null;
                }
                var translatedId = plugin.openGate().identityIds().translate(playerId);
                var account = plugin.openGate().accounts().find(player.getName())
                        .or(() -> plugin.openGate().accounts().find(translatedId));
                var accountId = account.map(dev.lunynt.opengate.account.Account::playerId).orElse(translatedId);
                var cookieValid = account.isPresent()
                        && plugin.openGate().cookieSessions().verify(accountId, cookie).get();
                plugin.getServer().getScheduler().runTask(plugin, () -> finishJoin(
                        player,
                        session,
                        accountId,
                        account,
                        cookieValid,
                        plugin.floodgate().isPlayer(playerId)
                                ? IdentityType.FLOODGATE
                                : plugin.getServer().getOnlineMode() ? IdentityType.PREMIUM : IdentityType.OFFLINE));
            } catch (Exception exception) {
                plugin.getLogger().severe("Could not load account for " + player.getName() + ": " + exception.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (isCurrent(player, session)) player.kickPlayer(message(player, "profile-lookup-unavailable"));
                });
            }
        });
        scheduleTimeout(player, playerId);
    }

    private void finishJoin(
            org.bukkit.entity.Player player,
            dev.lunynt.opengate.auth.AuthenticationSession session,
            java.util.UUID accountId,
            java.util.Optional<dev.lunynt.opengate.account.Account> account,
            boolean cookieValid,
            IdentityType identityType) {
        if (!isCurrent(player, session)) return;
        if (identityType == IdentityType.OFFLINE
                && !plugin.openGate().config().offlineWhitelist().allows(player.getName())) {
            player.kickPlayer(message(player, "offline-not-whitelisted"));
            return;
        }
        var registered = account.isPresent();
        var requirements = plugin.openGate().config().authenticationRequirements()
                .forPermissions(player::hasPermission);
        session.resolve(new ResolvedIdentity(
                player.getName(),
                accountId,
                identityType,
                registered,
                account.map(value -> value.passwordHash() != null
                        && (identityType == IdentityType.OFFLINE || requirements.passwordRequired())).orElse(false),
                account.map(value -> value.totpSecret() != null).orElse(false)),
                requirements);

        if (cookieValid && session.state() != AuthenticationState.AWAITING_REGISTRATION
                && session.state() != AuthenticationState.AWAITING_TOTP_ENROLLMENT) {
            session.resumeWithCookie();
        }

        if (session.state() == AuthenticationState.AUTHENTICATED) {
            session.release();
            player.sendMessage(message(player, "automatic-login"));
        } else if (session.state() == AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(message(player, "register-prompt"));
            plugin.showAuthenticationDialog(player, true);
        } else if (session.state() == AuthenticationState.AWAITING_TOTP_ENROLLMENT) {
            player.sendMessage(message(player, "totp-enrollment-required"));
        } else {
            player.sendMessage(message(player, "login-prompt"));
            plugin.showAuthenticationDialog(player, false);
        }
    }

    private void scheduleTimeout(org.bukkit.entity.Player player, java.util.UUID playerId) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isBlocked(playerId)) {
                player.kickPlayer(message(player, "authentication-timeout"));
            }
        }, plugin.openGate().config().authenticationTimeout().toSeconds() * 20L);
    }

    private boolean isCurrent(
            org.bukkit.entity.Player player, dev.lunynt.opengate.auth.AuthenticationSession session) {
        return player.isOnline()
                && plugin.openGate().sessions().find(player.getUniqueId()).orElse(null) == session;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setQuitMessage(null);
        plugin.openGate().sessions().close(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId()) && changedBlock(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        remapAlias(event);
        if (!isBlocked(event.getPlayer().getUniqueId())) {
            return;
        }
        var command = event.getMessage().substring(1).split(" ", 2)[0];
        if (!plugin.openGate().config().commands().isAuthenticationLabel(command)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message(event.getPlayer(), "authenticate-first"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onServerCommand(org.bukkit.event.server.ServerCommandEvent event) {
        var parts = event.getCommand().split(" ", 2);
        var label = parts[0].toLowerCase(java.util.Locale.ROOT);
        var canonical = plugin.openGate().config().commands().canonical(label);
        if (!canonical.equals(label)) {
            event.setCommand(canonical + (parts.length == 2 ? " " + parts[1] : ""));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                && isBlocked(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                && isBlocked(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof org.bukkit.entity.Player player
                && isBlocked(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player
                && isBlocked(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOutgoingDamage(EntityDamageByEntityEvent event) {
        var attacker = event.getDamager() instanceof org.bukkit.entity.Player player
                ? player
                : event.getDamager() instanceof Projectile projectile
                        && projectile.getShooter() instanceof org.bukkit.entity.Player player
                                ? player
                                : null;
        if (attacker != null && isBlocked(attacker.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player
                && isBlocked(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeldItem(PlayerItemHeldEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onCommandsSent(PlayerCommandSendEvent event) {
        if (!isBlocked(event.getPlayer().getUniqueId())) return;
        event.getCommands().removeIf(command -> !plugin.openGate().config().commands()
                .isAuthenticationLabel(command));
    }

    private boolean isBlocked(java.util.UUID playerId) {
        return plugin.openGate()
                .sessions()
                .find(playerId)
                .map(session -> session.state() != AuthenticationState.RELEASED)
                .orElse(true);
    }

    private static boolean changedBlock(PlayerMoveEvent event) {
        var from = event.getFrom();
        var to = event.getTo();
        return to != null
                && (!from.getWorld().equals(to.getWorld())
                        || from.getBlockX() != to.getBlockX()
                        || from.getBlockY() != to.getBlockY()
                        || from.getBlockZ() != to.getBlockZ());
    }

    private void remapAlias(PlayerCommandPreprocessEvent event) {
        var commandLine = event.getMessage().substring(1);
        var parts = commandLine.split(" ", 2);
        var label = parts[0].toLowerCase(java.util.Locale.ROOT);
        var canonical = plugin.openGate().config().commands().canonical(label);
        if (!canonical.equals(label)) {
            event.setMessage("/" + canonical + (parts.length == 2 ? " " + parts[1] : ""));
        }
    }

    private String message(String key) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', plugin.openGate().messages().get(key));
    }

    private String message(org.bukkit.entity.Player player, String key) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&',
                plugin.openGate().messages().get(java.util.Locale.forLanguageTag(player.getLocale().replace('_', '-')), key));
    }
}
