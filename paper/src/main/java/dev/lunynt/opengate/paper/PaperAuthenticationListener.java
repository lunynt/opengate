package dev.lunynt.opengate.paper;

import dev.lunynt.opengate.auth.AuthenticationState;
import dev.lunynt.opengate.auth.IdentityType;
import dev.lunynt.opengate.auth.ResolvedIdentity;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PaperAuthenticationListener implements Listener {
    private static final Set<String> ALLOWED_COMMANDS = Set.of("login", "l", "register", "reg", "totp", "2fa");

    private final OpenGatePaperPlugin plugin;

    PaperAuthenticationListener(OpenGatePaperPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var playerId = player.getUniqueId();
        var address = player.getAddress() == null ? "unknown" : player.getAddress().getAddress().getHostAddress();
        plugin.openGate().sessions().close(playerId);
        var session = plugin.openGate().sessions().open(playerId);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var account = plugin.openGate().accounts().find(playerId);
                var trusted = account.filter(value -> plugin.openGate()
                                .accounts()
                                .hasTrustedSession(
                                        value, address, plugin.openGate().config().trustedSessionLifetime()))
                        .isPresent();
                plugin.getServer().getScheduler().runTask(plugin, () -> finishJoin(
                        player,
                        session,
                        account,
                        trusted,
                        plugin.getServer().getOnlineMode() ? IdentityType.PREMIUM : IdentityType.OFFLINE));
            } catch (RuntimeException exception) {
                plugin.getLogger().severe("Could not load account for " + player.getName() + ": " + exception.getMessage());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (isCurrent(player, session)) player.kick(message("profile-lookup-unavailable"));
                });
            }
        });
        scheduleTimeout(player, playerId);
    }

    private void finishJoin(
            org.bukkit.entity.Player player,
            dev.lunynt.opengate.auth.AuthenticationSession session,
            java.util.Optional<dev.lunynt.opengate.account.Account> account,
            boolean trusted,
            IdentityType identityType) {
        if (!isCurrent(player, session)) return;
        var playerId = player.getUniqueId();
        var registered = account.isPresent();
        session.resolve(new ResolvedIdentity(
                player.getName(),
                playerId,
                identityType,
                registered,
                account.map(value -> value.passwordHash() != null).orElse(false),
                account.map(value -> value.totpSecret() != null).orElse(false),
                trusted));

        if (session.state() == AuthenticationState.AUTHENTICATED) {
            session.release();
            player.sendMessage(message("automatic-login"));
        } else if (session.state() == AuthenticationState.AWAITING_REGISTRATION) {
            player.sendMessage(message("register-prompt"));
        } else {
            player.sendMessage(message("login-prompt"));
        }
    }

    private void scheduleTimeout(org.bukkit.entity.Player player, java.util.UUID playerId) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isBlocked(playerId)) {
                player.kick(message("authentication-timeout"));
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
        plugin.openGate().sessions().close(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId()) && event.hasChangedBlock()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isBlocked(event.getPlayer().getUniqueId())) {
            return;
        }
        var command = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(java.util.Locale.ROOT);
        if (!ALLOWED_COMMANDS.contains(command)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message("authenticate-first"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isBlocked(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
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

    private boolean isBlocked(java.util.UUID playerId) {
        return plugin.openGate()
                .sessions()
                .find(playerId)
                .map(session -> session.state() != AuthenticationState.RELEASED)
                .orElse(true);
    }

    private Component message(String key) {
        return Component.text(plugin.openGate().messages().get(key));
    }
}
