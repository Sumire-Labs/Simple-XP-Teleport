package com.example.sxt.gui;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
import com.example.sxt.data.dao.WarpDao;
import com.example.sxt.data.model.BackLocation;
import com.example.sxt.data.model.BackLocation.BackReason;
import com.example.sxt.data.model.Warp;
import com.example.sxt.message.MessageService;
import com.example.sxt.teleport.TeleportService;
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
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Handles clicks inside warp GUI inventories and chat input for warp creation.
 *
 * <p>Always cancels the event first to prevent item removal.
 * Identifies inventory type via {@code instanceof} on the holder.</p>
 */
public final class WarpGuiListener implements Listener {

    private static final long PENDING_TIMEOUT_TICKS = 1200L; // 60 seconds

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final WarpDao warpDao;
    private final TeleportService teleportService;
    private final Executor mainThread;
    private final WarpGuiService warpGuiService;

    /** Players currently waiting to type a new warp name via chat. */
    private final Map<UUID, PendingWarpCreate> pendingWarpCreates = new ConcurrentHashMap<>();

    private record PendingWarpCreate(int returnPage, long createdAt) {}

    public WarpGuiListener(SimpleXpTeleportPlugin plugin, WarpGuiService warpGuiService) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.warpDao = plugin.getWarpDao();
        this.teleportService = plugin.getTeleportService();
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
        this.warpGuiService = warpGuiService;
    }

    // ── Inventory clicks ──────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        InventoryHolder rawHolder = inv.getHolder();

        if (rawHolder instanceof WarpAdminActionGuiHolder actionHolder) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= inv.getSize()) return;
            handleAdminActionClick(player, actionHolder, slot);
            return;
        }

        if (rawHolder instanceof WarpGuiHolder holder) {
            event.setCancelled(true); // prevent item removal

            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            if (slot < 0) return;

            // Top inventory clicks only
            if (slot >= inv.getSize()) return;

            // ── Public GUI ────────────────────────────────────
            if (!(holder instanceof WarpAdminGuiHolder)) {
                handlePublicClick(player, holder, slot);
                return;
            }

            // ── Admin GUI ─────────────────────────────────────
            handleAdminClick(player, (WarpAdminGuiHolder) holder, slot);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder rawHolder = event.getInventory().getHolder();
        if (rawHolder instanceof WarpGuiHolder
                || rawHolder instanceof WarpAdminActionGuiHolder) {
            event.setCancelled(true);
        }
    }

    // ── Chat input for new warp creation ──────────────────────

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        PendingWarpCreate pending = pendingWarpCreates.remove(uuid);
        if (pending == null) return;

        event.setCancelled(true);

        String plainMessage = PlainTextComponentSerializer.plainText()
                .serialize(event.message());
        String name = plainMessage.trim();

        if (name.equalsIgnoreCase("cancel")) {
            msg.send(player, "warp.gui.create-cancelled", Map.of());
            return;
        }

        // Validate name: not blank, no spaces, max 64 chars
        if (name.isEmpty() || name.contains(" ") || name.length() > 64) {
            msg.send(player, "warp.gui.create-invalid-name", Map.of());
            // Re-register so player can try again (existing timeout still ticking)
            pendingWarpCreates.put(uuid,
                    new PendingWarpCreate(pending.returnPage(), pending.createdAt()));
            return;
        }

        // Create warp on main thread (location must be accessed from main thread)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Location loc = player.getLocation();
            long now = System.currentTimeMillis();

            Warp warp = new Warp(
                    0L,                     // id (auto-generated by DB)
                    name,
                    loc.getWorld().getName(),
                    loc.getX(), loc.getY(), loc.getZ(),
                    loc.getYaw(), loc.getPitch(),
                    player.getUniqueId(),   // created_by
                    now,
                    now
            );

            warpDao.save(warp).thenAcceptAsync(v -> {
                msg.send(player, "warp.gui.create-success",
                        Map.of("warp", name));
                warpGuiService.openAdminGui(player, pending.returnPage());
            }, mainThread).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to save warp '" + name + "' from admin GUI", ex);
                return null;
            });
        });
    }

    // ── Public GUI handlers ───────────────────────────────────

    private void handlePublicClick(Player player, WarpGuiHolder holder, int slot) {
        if (holder.isPrevSlot(slot)) {
            warpGuiService.openPublicGui(player, holder.page() - 1);
            return;
        }
        if (holder.isNextSlot(slot)) {
            warpGuiService.openPublicGui(player, holder.page() + 1);
            return;
        }

        Warp warp = holder.getWarpAtSlot(slot);
        if (warp == null) return;

        player.closeInventory();

        String warpName = warp.name();
        warpDao.findOne(warpName).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                msg.send(player, "warp.not-found", Map.of("warp", warpName));
                return;
            }

            Warp fresh = opt.get();
            World world = Bukkit.getWorld(fresh.world());
            if (world == null) {
                msg.send(player, "warp.world-not-found",
                        Map.of("world", fresh.world()));
                return;
            }

            Location dest = new Location(world,
                    fresh.x(), fresh.y(), fresh.z(),
                    fresh.yaw(), fresh.pitch());

            teleportService.requestTeleport(player, dest, CommandKey.WARPX,
                    Map.of("warp", warpName));
        }, mainThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to find warp '" + warpName + "' for GUI teleport", ex);
            return null;
        });
    }

    // ── Admin GUI handlers ────────────────────────────────────

    private void handleAdminClick(Player player,
                                  WarpAdminGuiHolder holder, int slot) {
        if (holder.isPrevSlot(slot)) {
            warpGuiService.openAdminGui(player, holder.page() - 1);
            return;
        }
        if (holder.isNextSlot(slot)) {
            warpGuiService.openAdminGui(player, holder.page() + 1);
            return;
        }

        if (holder.isAddSlot(slot)) {
            player.closeInventory();
            UUID uuid = player.getUniqueId();
            // Remove any previous pending entry for this player
            pendingWarpCreates.remove(uuid);
            pendingWarpCreates.put(uuid,
                    new PendingWarpCreate(holder.page(), System.currentTimeMillis()));
            msg.send(player, "warp.gui.create-prompt", Map.of());

            // Schedule timeout
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                PendingWarpCreate p = pendingWarpCreates.remove(uuid);
                if (p != null) {
                    msg.send(player, "warp.gui.pending-timeout", Map.of());
                }
            }, PENDING_TIMEOUT_TICKS);
            return;
        }

        Warp warp = holder.getWarpAtSlot(slot);
        if (warp == null) return;

        // Open action GUI for this warp
        warpGuiService.openAdminActionGui(player, warp.name(), holder.page());
    }

    // ── Admin Action GUI handlers ─────────────────────────────

    private void handleAdminActionClick(Player player,
                                         WarpAdminActionGuiHolder holder,
                                         int slot) {
        if (slot == WarpAdminActionGuiHolder.TELEPORT_SLOT) {
            handleActionTeleport(player, holder);
            return;
        }
        if (slot == WarpAdminActionGuiHolder.OVERWRITE_SLOT) {
            handleActionOverwrite(player, holder);
            return;
        }
        if (slot == WarpAdminActionGuiHolder.DELETE_SLOT) {
            handleActionDelete(player, holder);
            return;
        }
        if (slot == WarpAdminActionGuiHolder.BACK_SLOT) {
            warpGuiService.openAdminGui(player, holder.returnPage());
            return;
        }
    }

    private void handleActionTeleport(Player player,
                                       WarpAdminActionGuiHolder holder) {
        player.closeInventory();
        String warpName = holder.warpName();

        warpDao.findOne(warpName).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                msg.send(player, "warp.not-found", Map.of("warp", warpName));
                return;
            }

            Warp warp = opt.get();
            World world = Bukkit.getWorld(warp.world());
            if (world == null) {
                msg.send(player, "warp.world-not-found",
                        Map.of("world", warp.world()));
                return;
            }

            Location dest = new Location(world,
                    warp.x(), warp.y(), warp.z(),
                    warp.yaw(), warp.pitch());

            // Save back location (non-blocking async)
            Location from = player.getLocation();
            BackLocation back = new BackLocation(
                    player.getUniqueId(),
                    from.getWorld().getName(),
                    from.getX(), from.getY(), from.getZ(),
                    from.getYaw(), from.getPitch(),
                    System.currentTimeMillis(),
                    BackReason.TELEPORT
            );
            plugin.getBackLocationDao().upsert(back).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to save back location for admin warp teleport", ex);
                return null;
            });

            // Direct teleport — no cost/CD/warmup
            player.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.COMMAND)
                    .thenAccept(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            plugin.getServer().getScheduler().runTask(plugin,
                                    () -> msg.send(player,
                                            "warp.gui.teleported-admin",
                                            Map.of("warp", warpName)));
                        }
                    });
        }, mainThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to find warp '" + warpName + "' for admin teleport", ex);
            return null;
        });
    }

    private void handleActionOverwrite(Player player,
                                        WarpAdminActionGuiHolder holder) {
        player.closeInventory();
        String warpName = holder.warpName();
        int returnPage = holder.returnPage();

        Location loc = player.getLocation();
        long now = System.currentTimeMillis();

        Warp warp = new Warp(
                0L,                     // id (auto-generated by DB)
                warpName,
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                player.getUniqueId(),   // created_by
                now,
                now
        );

        warpDao.save(warp).thenAcceptAsync(v -> {
            msg.send(player, "warp.gui.overwrite-success",
                    Map.of("warp", warpName));
            warpGuiService.openAdminGui(player, returnPage);
        }, mainThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to overwrite warp '" + warpName + "'", ex);
            return null;
        });
    }

    private void handleActionDelete(Player player,
                                     WarpAdminActionGuiHolder holder) {
        player.closeInventory();
        String warpName = holder.warpName();
        int returnPage = holder.returnPage();

        warpDao.delete(warpName).thenAcceptAsync(v -> {
            msg.send(player, "warp.gui.delete-success",
                    Map.of("warp", warpName));
            warpGuiService.openAdminGui(player, returnPage);
        }, mainThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to delete warp '" + warpName + "'", ex);
            return null;
        });
    }
}
