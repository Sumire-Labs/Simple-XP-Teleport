package com.example.sxt.teleport;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import com.example.sxt.config.CommandKey;
import com.example.sxt.cost.CostCalculator;
import com.example.sxt.cost.CostMode;
import com.example.sxt.cost.XpUtil;
import com.example.sxt.data.dao.WaypointDao;
import com.example.sxt.data.model.Waypoint;
import com.example.sxt.message.MessageService;
import com.example.sxt.permission.WaypointLimitResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Manages pending waypoint share requests between players.
 * Each target can have at most one outstanding request (latest wins).
 *
 * <p>A repeating cleanup task automatically expires requests that
 * exceed the configured {@code waypoints.share.expire-seconds}.</p>
 */
public final class WaypointShareRequest {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService messageService;
    private final WaypointDao waypointDao;
    private final WaypointLimitResolver waypointLimitResolver;
    private final CostCalculator costCalculator;
    private final Executor mainThread;

    /** Pending share requests, keyed by target UUID. */
    private final Map<UUID, SharePending> pendingRequests = new ConcurrentHashMap<>();

    public WaypointShareRequest(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.waypointDao = plugin.getWaypointDao();
        this.waypointLimitResolver = new WaypointLimitResolver(plugin);
        this.costCalculator = new CostCalculator(plugin);
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    // ── Public API ───────────────────────────────────────────

    /**
     * Initiate a waypoint share request from {@code requester} to {@code target}.
     * Overwrites any existing pending request for the same target.
     *
     * @param requester the player sending the share request
     * @param target    the player receiving the share request
     * @param waypoint  the waypoint being shared
     */
    public void initiate(Player requester, Player target, Waypoint waypoint) {
        SharePending pending = new SharePending(
                requester.getUniqueId(), target.getUniqueId(),
                waypoint, System.currentTimeMillis());
        pendingRequests.put(target.getUniqueId(), pending);

        messageService.send(requester, "waypoint.share.sent",
                Map.of("player", requester.getName(),
                       "target", target.getName(),
                       "waypoint", waypoint.name()));
        messageService.send(target, "waypoint.share.received",
                Map.of("player", requester.getName(),
                       "target", target.getName(),
                       "waypoint", waypoint.name()));
    }

    /**
     * Accept the pending share request for {@code target} (the receiver).
     * Copies the shared waypoint into the target's collection.
     *
     * <p>If {@code waypoints.share.charge-on-accept} is enabled, the target
     * pays the XP cost for the waypoint as configured under
     * {@code commands.wayx.cost.*}. On cost failure the pending request is
     * left intact so the player can retry.</p>
     *
     * @param target the player who received and is now accepting the request
     */
    public void accept(Player target) {
        UUID targetUuid = target.getUniqueId();
        SharePending pending = pendingRequests.get(targetUuid);
        if (pending == null) {
            messageService.send(target, "waypoint.share.no-pending", Map.of());
            return;
        }

        // Check expiry
        long timeoutMs = plugin.getPluginConfig().waypointsShareExpireSeconds() * 1000L;
        if (System.currentTimeMillis() - pending.createdAtMs() > timeoutMs) {
            pendingRequests.remove(targetUuid);
            messageService.send(target, "waypoint.share.expired", Map.of());
            Player requester = Bukkit.getPlayer(pending.requesterUuid());
            if (requester != null && requester.isOnline()) {
                messageService.send(requester, "waypoint.share.expired", Map.of());
            }
            return;
        }

        Waypoint waypoint = pending.waypoint();

        // Check if target already has a waypoint with the same name
        waypointDao.findOne(targetUuid, waypoint.name()).thenAcceptAsync(opt -> {
            boolean isOverwrite = opt.isPresent();

            if (!isOverwrite) {
                // New waypoint — check limit
                waypointDao.countByOwner(targetUuid).thenAcceptAsync(count -> {
                    int max = waypointLimitResolver.resolve(target);
                    if (count >= max) {
                        messageService.send(target, "waypoint.limit-reached",
                                Map.of("count", String.valueOf(count),
                                       "max", String.valueOf(max)));
                        // Keep pending so the player can retry after freeing space
                        return;
                    }
                    // Limit ok — check cost and save
                    doAcceptSave(target, pending, waypoint, targetUuid);
                }, mainThread).exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to count waypoints for share accept", ex);
                    return null;
                });
            } else {
                // Overwrite existing — skip limit check, charge cost and save
                doAcceptSave(target, pending, waypoint, targetUuid);
            }
        }, mainThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to find waypoint for share accept", ex);
            return null;
        });
    }

    /**
     * Deny the pending share request for {@code target} (the receiver).
     * Notifies both the target and the requester (if online).
     *
     * @param target the player who received and is now denying the request
     */
    public void deny(Player target) {
        SharePending pending = pendingRequests.remove(target.getUniqueId());
        if (pending == null) {
            messageService.send(target, "waypoint.share.no-pending", Map.of());
            return;
        }

        String waypointName = pending.waypoint().name();

        messageService.send(target, "waypoint.share.denied",
                Map.of("waypoint", waypointName));

        Player requester = Bukkit.getPlayer(pending.requesterUuid());
        if (requester != null && requester.isOnline()) {
            messageService.send(requester, "waypoint.share.denied",
                    Map.of("player", requester.getName(),
                           "target", target.getName(),
                           "waypoint", waypointName));
        }
    }

    // ── Cleanup ──────────────────────────────────────────────

    /**
     * Start a repeating task (every 20 ticks) that expires timed-out requests.
     * Must be called from the main server thread during {@code onEnable}.
     */
    public void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupExpired,
                20L, 20L);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        long timeoutMs = plugin.getPluginConfig().waypointsShareExpireSeconds() * 1000L;
        Iterator<Map.Entry<UUID, SharePending>> it = pendingRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SharePending> entry = it.next();
            SharePending pending = entry.getValue();
            if (now - pending.createdAtMs() > timeoutMs) {
                it.remove();
                sendExpired(pending);
            }
        }
    }

    private void sendExpired(SharePending pending) {
        Player requester = Bukkit.getPlayer(pending.requesterUuid());
        Player target = Bukkit.getPlayer(pending.targetUuid());
        if (requester != null && requester.isOnline()) {
            messageService.send(requester, "waypoint.share.expired", Map.of());
        }
        if (target != null && target.isOnline()) {
            messageService.send(target, "waypoint.share.expired", Map.of());
        }
    }

    // ── Internal helpers ────────────────────────────────────

    /**
     * Shared logic for accept: optionally charge XP, then save the waypoint
     * under the target's ownership, remove the pending request, and notify
     * both players.
     */
    private void doAcceptSave(Player target, SharePending pending,
                              Waypoint waypoint, UUID targetUuid) {
        // If charge-on-accept is enabled, check and deduct cost
        if (plugin.getPluginConfig().isWaypointsShareChargeOnAccept()) {
            CommandConfig cfg = plugin.getPluginConfig().commandConfig(CommandKey.WAYX);
            if (cfg != null) {
                Location from = target.getLocation();
                Location to = waypointToLocation(waypoint);
                int cost = costCalculator.calculate(cfg, from, to);

                // Check cost bypass permission
                if (target.hasPermission("sxt.bypass.cost.wayx")
                        || target.hasPermission("sxt.bypass.cost.*")) {
                    cost = 0;
                }

                if (cost > 0) {
                    if (cfg.costMode() == CostMode.LEVEL) {
                        if (target.getLevel() < cost) {
                            messageService.send(target, "cost.not-enough-level",
                                    Map.of("cost", String.valueOf(cost)));
                            // Keep pending for retry
                            return;
                        }
                    } else {
                        if (XpUtil.getTotalExperience(target) < cost) {
                            messageService.send(target, "cost.not-enough-points",
                                    Map.of("cost", String.valueOf(cost)));
                            // Keep pending for retry
                            return;
                        }
                    }

                    // Deduct cost
                    boolean ok;
                    if (cfg.costMode() == CostMode.LEVEL) {
                        ok = XpUtil.takeLevels(target, cost);
                    } else {
                        ok = XpUtil.takePoints(target, cost);
                    }
                    if (!ok) {
                        // Should not reach here (already checked above), but guard
                        return;
                    }
                    String consumedKey = cfg.costMode() == CostMode.LEVEL
                            ? "cost.consumed-level" : "cost.consumed-points";
                    messageService.send(target, consumedKey,
                            Map.of("cost", String.valueOf(cost)));
                }
            }
        }

        // Save waypoint with target as new owner
        long now = System.currentTimeMillis();
        Waypoint newWaypoint = new Waypoint(
                0L,                     // id (auto-generated by DB)
                targetUuid,
                waypoint.name(),
                waypoint.world(),
                waypoint.x(), waypoint.y(), waypoint.z(),
                waypoint.yaw(), waypoint.pitch(),
                now,                    // created_at
                now                     // updated_at
        );

        waypointDao.save(newWaypoint).thenAcceptAsync(v -> {
            // Remove pending on success
            pendingRequests.remove(target.getUniqueId());

            // Notify target (receiver / new owner)
            var requester = Bukkit.getPlayer(pending.requesterUuid());
            String requesterName = requester != null
                    ? requester.getName()
                    : Bukkit.getOfflinePlayer(pending.requesterUuid()).getName();
            messageService.send(target, "waypoint.share.copied",
                    Map.of("waypoint", waypoint.name(),
                           "player", requesterName != null ? requesterName : "?"));

            // Notify requester if online
            if (requester != null && requester.isOnline()) {
                messageService.send(requester, "waypoint.share.accepted",
                        Map.of("player", requester.getName(),
                               "target", target.getName(),
                               "waypoint", waypoint.name()));
            }
        }, mainThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save waypoint for share accept", ex);
            return null;
        });
    }

    /** Build a {@link Location} from a waypoint record. */
    private static Location waypointToLocation(Waypoint waypoint) {
        World world = Bukkit.getWorld(waypoint.world());
        return new Location(world, waypoint.x(), waypoint.y(), waypoint.z(),
                waypoint.yaw(), waypoint.pitch());
    }

    // ── SharePending record ──────────────────────────────────

    /**
     * Represents an outstanding waypoint share request.
     *
     * @param requesterUuid the UUID of the player who sent the share
     * @param targetUuid    the UUID of the player who receives the share
     * @param waypoint      the waypoint being shared
     * @param createdAtMs   epoch-millis when the request was created
     */
    public record SharePending(UUID requesterUuid, UUID targetUuid,
                                Waypoint waypoint, long createdAtMs) {
    }
}
