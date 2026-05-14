package com.example.sxt.gui;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
import com.example.sxt.data.dao.WaypointDao;
import com.example.sxt.data.model.Waypoint;
import com.example.sxt.message.MessageService;
import com.example.sxt.permission.WaypointLimitResolver;
import com.example.sxt.teleport.TeleportService;
import com.example.sxt.teleport.WaypointShareRequest;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Handles clicks inside waypoint GUI inventories and chat input for waypoint creation.
 *
 * <p>Always cancels the event first to prevent item removal.
 * Identifies inventory type via {@code instanceof} on the holder.</p>
 */
public final class WaypointGuiListener implements Listener {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{1,32}$");
    private static final long PENDING_TIMEOUT_TICKS = 1200L; // 60 seconds

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final WaypointDao waypointDao;
    private final TeleportService teleportService;
    private final WaypointShareRequest shareRequest;
    private final WaypointLimitResolver waypointLimitResolver;
    private final Executor mainThread;
    private final WaypointGuiService waypointGuiService;

    /** Players currently waiting to type a new waypoint name via chat. */
    private final Map<UUID, PendingWaypointCreate> pendingWaypointCreates = new ConcurrentHashMap<>();

    private record PendingWaypointCreate(int returnPage, boolean isManage, long createdAt) {}

    public WaypointGuiListener(SimpleXpTeleportPlugin plugin, WaypointGuiService waypointGuiService) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.waypointDao = plugin.getWaypointDao();
        this.teleportService = plugin.getTeleportService();
        this.shareRequest = plugin.getWaypointShareRequest();
        this.waypointLimitResolver = new WaypointLimitResolver(plugin);
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
        this.waypointGuiService = waypointGuiService;
    }

    // ── Inventory clicks ──────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder rawHolder = inv.getHolder();

        if (rawHolder instanceof WaypointActionGuiHolder actionHolder) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= inv.getSize()) return;
            handleActionClick(player, actionHolder, slot);
            return;
        }

        if (rawHolder instanceof WaypointShareTargetGuiHolder shareHolder) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= inv.getSize()) return;
            handleShareTargetClick(player, shareHolder, slot);
            return;
        }

        if (rawHolder instanceof WaypointManageGuiHolder manageHolder) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= inv.getSize()) return;
            handleManageClick(player, manageHolder, slot);
            return;
        }

        if (rawHolder instanceof WaypointGuiHolder holder) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= inv.getSize()) return;
            handlePublicClick(player, holder, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder rawHolder = event.getInventory().getHolder();
        if (rawHolder instanceof WaypointGuiHolder
                || rawHolder instanceof WaypointActionGuiHolder
                || rawHolder instanceof WaypointShareTargetGuiHolder) {
            event.setCancelled(true);
        }
    }

    // ── Chat input for new waypoint creation ──────────────────

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        PendingWaypointCreate pending = pendingWaypointCreates.remove(uuid);
        if (pending == null) return;

        event.setCancelled(true);

        String plainMessage = PlainTextComponentSerializer.plainText()
                .serialize(event.message());
        String name = plainMessage.trim();

        if (name.equalsIgnoreCase("cancel")) {
            msg.send(player, "waypoint.gui.add-cancelled", Map.of());
            return;
        }

        // Validate name
        if (!NAME_PATTERN.matcher(name).matches()) {
            msg.send(player, "waypoint.invalid-name", Map.of());
            // Re-register so player can try again
            pendingWaypointCreates.put(uuid,
                    new PendingWaypointCreate(pending.returnPage(),
                            pending.isManage(), pending.createdAt()));
            return;
        }

        // Create waypoint on main thread (location must be accessed from main thread)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            var playerUuid = player.getUniqueId();
            Location loc = player.getLocation();

            waypointDao.findOne(playerUuid, name).thenAcceptAsync(opt -> {
                if (opt.isPresent()) {
                    // Overwriting existing — skip limit check, save directly
                    Waypoint waypoint = buildWaypoint(player, name);
                    waypointDao.save(waypoint).thenAcceptAsync(v -> {
                        msg.send(player, "waypoint.added",
                                Map.of("waypoint", name));
                        reopenAfterAdd(player, pending);
                    }, mainThread);
                    return;
                }

                // New waypoint — verify limit
                waypointDao.countByOwner(playerUuid).thenAcceptAsync(count -> {
                    int max = waypointLimitResolver.resolve(player);
                    if (count >= max) {
                        msg.send(player, "waypoint.limit-reached",
                                Map.of("count", String.valueOf(count),
                                        "max", String.valueOf(max)));
                        return;
                    }
                    Waypoint waypoint = buildWaypoint(player, name);
                    waypointDao.save(waypoint).thenAcceptAsync(v -> {
                        msg.send(player, "waypoint.added",
                                Map.of("waypoint", name));
                        reopenAfterAdd(player, pending);
                    }, mainThread);
                }, mainThread).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to count waypoints for GUI add", ex);
                    return null;
                });
            }, mainThread).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to find waypoint for GUI add", ex);
                return null;
            });
        });
    }

    private void reopenAfterAdd(Player player, PendingWaypointCreate pending) {
        if (pending.isManage()) {
            waypointGuiService.openManageGui(player, pending.returnPage());
        } else {
            waypointGuiService.openPublicGui(player, pending.returnPage());
        }
    }

    // ── Public GUI handlers ───────────────────────────────────

    private void handlePublicClick(Player player, WaypointGuiHolder holder, int slot) {
        if (holder.isPrevSlot(slot)) {
            waypointGuiService.openPublicGui(player, holder.page() - 1);
            return;
        }
        if (holder.isNextSlot(slot)) {
            waypointGuiService.openPublicGui(player, holder.page() + 1);
            return;
        }
        if (holder.isAddSlot(slot)) {
            player.closeInventory();
            startPendingAdd(player, holder.page(), false);
            return;
        }
        if (holder.isManageSlot(slot)) {
            waypointGuiService.openManageGui(player, 0);
            return;
        }

        Waypoint waypoint = holder.getWaypointAtSlot(slot);
        if (waypoint == null) return;

        player.closeInventory();
        doTeleport(player, waypoint);
    }

    // ── Manage GUI handlers ───────────────────────────────────

    private void handleManageClick(Player player, WaypointManageGuiHolder holder, int slot) {
        if (holder.isPrevSlot(slot)) {
            waypointGuiService.openManageGui(player, holder.page() - 1);
            return;
        }
        if (holder.isNextSlot(slot)) {
            waypointGuiService.openManageGui(player, holder.page() + 1);
            return;
        }
        if (holder.isAddSlot(slot)) {
            player.closeInventory();
            startPendingAdd(player, holder.page(), true);
            return;
        }

        Waypoint waypoint = holder.getWaypointAtSlot(slot);
        if (waypoint == null) return;

        // Open action GUI for this waypoint
        waypointGuiService.openActionGui(player, waypoint.name(), holder.page());
    }

    // ── Action GUI handlers ───────────────────────────────────

    private void handleActionClick(Player player,
                                    WaypointActionGuiHolder holder,
                                    int slot) {
        if (slot == WaypointActionGuiHolder.TELEPORT_SLOT) {
            handleActionTeleport(player, holder);
            return;
        }
        if (slot == WaypointActionGuiHolder.DELETE_SLOT) {
            handleActionDelete(player, holder);
            return;
        }
        if (slot == WaypointActionGuiHolder.SHARE_SLOT) {
            waypointGuiService.openShareTargetGui(player,
                    holder.waypointName(), holder.returnPage(), 0);
            return;
        }
        if (slot == WaypointActionGuiHolder.BACK_SLOT) {
            waypointGuiService.openManageGui(player, holder.returnPage());
            return;
        }
    }

    private void handleActionTeleport(Player player,
                                       WaypointActionGuiHolder holder) {
        player.closeInventory();
        String waypointName = holder.waypointName();

        waypointDao.findOne(player.getUniqueId(), waypointName)
                .thenAcceptAsync(opt -> {
                    if (opt.isEmpty()) {
                        msg.send(player, "waypoint.not-found",
                                Map.of("waypoint", waypointName));
                        return;
                    }
                    doTeleport(player, opt.get());
                }, mainThread).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to find waypoint '" + waypointName
                                    + "' for action teleport", ex);
                    return null;
                });
    }

    private void handleActionDelete(Player player,
                                     WaypointActionGuiHolder holder) {
        player.closeInventory();
        String waypointName = holder.waypointName();
        int returnPage = holder.returnPage();

        waypointDao.delete(player.getUniqueId(), waypointName)
                .thenAcceptAsync(v -> {
                    msg.send(player, "waypoint.deleted",
                            Map.of("waypoint", waypointName));
                    waypointGuiService.openManageGui(player, returnPage);
                }, mainThread).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to delete waypoint '" + waypointName + "'", ex);
                    return null;
                });
    }

    // ── Share Target GUI handlers ─────────────────────────────

    private void handleShareTargetClick(Player player,
                                         WaypointShareTargetGuiHolder holder,
                                         int slot) {
        if (holder.isPrevSlot(slot)) {
            waypointGuiService.openShareTargetGui(player,
                    holder.waypointName(), holder.returnPage(), holder.page() - 1);
            return;
        }
        if (holder.isNextSlot(slot)) {
            waypointGuiService.openShareTargetGui(player,
                    holder.waypointName(), holder.returnPage(), holder.page() + 1);
            return;
        }
        if (holder.isBackSlot(slot)) {
            waypointGuiService.openActionGui(player,
                    holder.waypointName(), holder.returnPage());
            return;
        }

        Player target = holder.getPlayerAtSlot(slot);
        if (target == null) return;

        player.closeInventory();

        // Validate the waypoint still exists
        waypointDao.findOne(player.getUniqueId(), holder.waypointName())
                .thenAcceptAsync(opt -> {
                    if (opt.isEmpty()) {
                        msg.send(player, "waypoint.not-found",
                                Map.of("waypoint", holder.waypointName()));
                        return;
                    }
                    // Double-check target is still online
                    if (!target.isOnline()) {
                        msg.send(player, "general.player-not-found",
                                Map.of("target", target.getName()));
                        return;
                    }
                    shareRequest.initiate(player, target, opt.get());
                }, mainThread).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to find waypoint for share target", ex);
                    return null;
                });
    }

    // ── Shared teleport logic ─────────────────────────────────

    private void doTeleport(Player player, Waypoint waypoint) {
        String waypointName = waypoint.name();

        waypointDao.findOne(player.getUniqueId(), waypointName)
                .thenAcceptAsync(opt -> {
                    if (opt.isEmpty()) {
                        msg.send(player, "waypoint.not-found",
                                Map.of("waypoint", waypointName));
                        return;
                    }

                    Waypoint fresh = opt.get();
                    World world = Bukkit.getWorld(fresh.world());
                    if (world == null) {
                        msg.send(player, "waypoint.world-not-found",
                                Map.of("world", fresh.world()));
                        return;
                    }

                    Location dest = new Location(world,
                            fresh.x(), fresh.y(), fresh.z(),
                            fresh.yaw(), fresh.pitch());

                    teleportService.requestTeleport(player, dest, CommandKey.WAYX,
                            Map.of("waypoint", waypointName));
                }, mainThread).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to find waypoint '" + waypointName
                                    + "' for GUI teleport", ex);
                    return null;
                });
    }

    // ── Pending add helpers ───────────────────────────────────

    private void startPendingAdd(Player player, int returnPage, boolean isManage) {
        UUID uuid = player.getUniqueId();
        pendingWaypointCreates.remove(uuid);
        pendingWaypointCreates.put(uuid, new PendingWaypointCreate(
                returnPage, isManage, System.currentTimeMillis()));
        msg.send(player, "waypoint.gui.add-prompt", Map.of());

        // Schedule timeout
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            PendingWaypointCreate p = pendingWaypointCreates.remove(uuid);
            if (p != null) {
                msg.send(player, "waypoint.gui.add-timeout", Map.of());
            }
        }, PENDING_TIMEOUT_TICKS);
    }

    // ── Static helpers ────────────────────────────────────────

    /** Build a {@link Waypoint} from the player's current location. */
    private static Waypoint buildWaypoint(Player player, String name) {
        var loc = player.getLocation();
        long now = System.currentTimeMillis();
        return new Waypoint(
                0L,                     // id (auto-generated by DB)
                player.getUniqueId(),
                name,
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                now,                    // created_at
                now                     // updated_at
        );
    }
}
