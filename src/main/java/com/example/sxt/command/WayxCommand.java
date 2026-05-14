package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
import com.example.sxt.data.dao.WaypointDao;
import com.example.sxt.data.model.Waypoint;
import com.example.sxt.gui.WaypointGuiService;
import com.example.sxt.message.MessageService;
import com.example.sxt.permission.WaypointLimitResolver;
import com.example.sxt.teleport.WaypointShareRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * /wayx — manage personal waypoints.
 *
 * <p>Subcommands:
 * <ul>
 *   <li><b>(no args)</b> — opens the waypoint selection GUI (future).</li>
 *   <li><b>add &lt;name&gt;</b> — registers the current location as a waypoint.</li>
 *   <li><b>remove &lt;name&gt;</b> — deletes a saved waypoint.</li>
 *   <li><b>list</b> — opens the waypoint management GUI (future).</li>
 *   <li><b>share &lt;name&gt; &lt;player&gt;</b> — sends a waypoint share request.</li>
 *   <li><b>accept</b> — accepts a pending waypoint share request.</li>
 *   <li><b>deny</b> — denies a pending waypoint share request.</li>
 * </ul>
 *
 * <p>Name pattern: {@code ^[a-zA-Z0-9_\-]{1,32}$}.</p>
 */
public final class WayxCommand implements CommandExecutor, TabCompleter {

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_\\-]{1,32}$");
    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "list", "share", "accept", "deny");

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final WaypointDao waypointDao;
    private final WaypointLimitResolver waypointLimitResolver;
    private final WaypointShareRequest shareRequest;
    private final WaypointGuiService waypointGuiService;
    private final Executor mainThread;

    public WayxCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.waypointDao = plugin.getWaypointDao();
        this.waypointLimitResolver = new WaypointLimitResolver(plugin);
        this.shareRequest = plugin.getWaypointShareRequest();
        this.waypointGuiService = plugin.getWaypointGuiService();
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.player-only", Map.of());
            return true;
        }

        if (!player.hasPermission("sxt.use.wayx")) {
            msg.send(player, "general.no-permission", Map.of());
            return true;
        }

        if (args.length == 0) {
            return handleOpenListGui(player, command);
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "add"    -> handleAdd(player, command, args);
            case "remove" -> handleRemove(player, command, args);
            case "list"   -> handleOpenManageGui(player, command);
            case "share"  -> handleShare(player, command, args);
            case "accept" -> handleAccept(player);
            case "deny"   -> handleDeny(player);
            default       -> {
                msg.send(player, "general.usage", Map.of("usage", command.getUsage()));
                yield true;
            }
        };
    }

    // ── /wayx (no args) ───────────────────────────────────────

    /**
     * Opens the waypoint selection GUI.
     */
    private boolean handleOpenListGui(Player player, Command command) {
        waypointGuiService.openPublicGui(player, 0);
        return true;
    }

    // ── /wayx add <name> ──────────────────────────────────────

    /**
     * Registers the player's current location as a waypoint with the given name.
     *
     * <p>If a waypoint with the same name already exists it is overwritten
     * (overwrites do not count against the limit). New waypoints are checked
     * against {@link WaypointLimitResolver}.</p>
     */
    private boolean handleAdd(Player player, Command command, String[] args) {
        if (args.length != 2) {
            msg.send(player, "general.usage",
                    Map.of("usage", "/wayx add <name>"));
            return true;
        }

        String waypointName = args[1];

        if (!NAME_PATTERN.matcher(waypointName).matches()) {
            msg.send(player, "waypoint.invalid-name", Map.of());
            return true;
        }

        var uuid = player.getUniqueId();

        waypointDao.findOne(uuid, waypointName).thenAcceptAsync(opt -> {
            if (opt.isPresent()) {
                // Overwriting an existing waypoint — skip limit check
                Waypoint waypoint = buildWaypoint(player, waypointName);
                waypointDao.save(waypoint).thenAcceptAsync(v ->
                        msg.send(player, "waypoint.added",
                                Map.of("waypoint", waypointName)),
                        mainThread);
                return;
            }

            // New waypoint — verify limit
            waypointDao.countByOwner(uuid).thenAcceptAsync(count -> {
                int max = waypointLimitResolver.resolve(player);
                if (count >= max) {
                    msg.send(player, "waypoint.limit-reached",
                            Map.of("count", String.valueOf(count),
                                   "max", String.valueOf(max)));
                    return;
                }
                Waypoint waypoint = buildWaypoint(player, waypointName);
                waypointDao.save(waypoint).thenAcceptAsync(v ->
                        msg.send(player, "waypoint.added",
                                Map.of("waypoint", waypointName)),
                        mainThread);
            }, mainThread).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to count waypoints for add", ex);
                return null;
            });

        }, mainThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to find waypoint for add", ex);
            return null;
        });

        return true;
    }

    // ── /wayx remove <name> ───────────────────────────────────

    /**
     * Deletes the named waypoint belonging to the player.
     */
    private boolean handleRemove(Player player, Command command, String[] args) {
        if (args.length != 2) {
            msg.send(player, "general.usage",
                    Map.of("usage", "/wayx remove <name>"));
            return true;
        }

        String waypointName = args[1];
        var uuid = player.getUniqueId();

        waypointDao.findOne(uuid, waypointName)
                .thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return waypointDao.delete(uuid, waypointName)
                            .thenApply(v -> true);
                })
                .thenAcceptAsync(found -> {
                    if (Boolean.FALSE.equals(found)) {
                        msg.send(player, "waypoint.not-found",
                                Map.of("waypoint", waypointName));
                    } else {
                        msg.send(player, "waypoint.deleted",
                                Map.of("waypoint", waypointName));
                    }
                }, mainThread)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to delete waypoint '" + waypointName + "'", ex);
                    return null;
                });

        return true;
    }

    // ── /wayx list ────────────────────────────────────────────

    /**
     * Opens the waypoint management GUI.
     */
    private boolean handleOpenManageGui(Player player, Command command) {
        waypointGuiService.openManageGui(player, 0);
        return true;
    }

    // ── /wayx share <name> <player> ───────────────────────────

    /**
     * Initiates a waypoint share request to another player.
     * Validates args, permissions, config, target existence, and waypoint
     * ownership, then delegates to {@link WaypointShareRequest#initiate}.
     */
    private boolean handleShare(Player player, Command command, String[] args) {
        if (args.length < 3) {
            msg.send(player, "general.usage",
                    Map.of("usage", "/wayx share <name> <player>"));
            return true;
        }

        if (!player.hasPermission("sxt.use.wayx.share")) {
            msg.send(player, "general.no-permission", Map.of());
            return true;
        }

        if (!plugin.getPluginConfig().isWaypointsShareEnabled()) {
            msg.send(player, "waypoint.share.disabled", Map.of());
            return true;
        }

        String waypointName = args[1];
        String targetName = args[2];

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            msg.send(player, "general.player-not-found",
                    Map.of("target", targetName));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            msg.send(player, "waypoint.share.self-target", Map.of());
            return true;
        }

        // Validate the waypoint exists
        waypointDao.findOne(player.getUniqueId(), waypointName)
                .thenAcceptAsync(opt -> {
                    if (opt.isEmpty()) {
                        msg.send(player, "waypoint.not-found",
                                Map.of("waypoint", waypointName));
                        return;
                    }
                    shareRequest.initiate(player, target, opt.get());
                }, mainThread)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to find waypoint for share", ex);
                    return null;
                });

        return true;
    }

    // ── /wayx accept ──────────────────────────────────────────

    /**
     * Accepts the pending waypoint share request sent to this player.
     * Copies the shared waypoint into the player's own collection.
     */
    private boolean handleAccept(Player player) {
        shareRequest.accept(player);
        return true;
    }

    // ── /wayx deny ────────────────────────────────────────────

    /**
     * Denies the pending waypoint share request sent to this player.
     */
    private boolean handleDeny(Player player) {
        shareRequest.deny(player);
        return true;
    }

    // ── Tab completion ────────────────────────────────────────

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String label,
                                      @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial)) {
                    matches.add(sub);
                }
            }
            return matches;
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if ("remove".equals(sub) || "share".equals(sub)) {
                // Suggest player's own waypoint names
                String partial = args[1].toLowerCase();
                List<String> matches = new ArrayList<>();
                // Synchronous tab completion — iterate cached or use a
                // best-effort approach. For now return empty to avoid
                // blocking; full async completion can be added later.
                return matches;
            }
        }

        if (args.length == 3 && "share".equalsIgnoreCase(args[0])) {
            // Suggest online player names
            String partial = args[2].toLowerCase();
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(partial)
                        && !online.getUniqueId().equals(player.getUniqueId())) {
                    names.add(online.getName());
                }
            }
            return names;
        }

        return List.of();
    }

    // ── Helpers ───────────────────────────────────────────────

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
