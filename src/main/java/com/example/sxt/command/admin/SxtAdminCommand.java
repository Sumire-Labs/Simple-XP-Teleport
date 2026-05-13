package com.example.sxt.command.admin;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.data.dao.HomeDao;
import com.example.sxt.data.model.BackLocation;
import com.example.sxt.data.model.BackLocation.BackReason;
import com.example.sxt.data.model.Home;
import com.example.sxt.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * /sxtadmin reload | debug | home &lt;player&gt; list | home &lt;player&gt; delete &lt;name&gt; | home &lt;player&gt; tp &lt;name&gt;
 */
public final class SxtAdminCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final HomeDao homeDao;
    private final Executor mainThread;

    public SxtAdminCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.homeDao = plugin.getHomeDao();
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0) {
            return false; // show usage
        }

        String sub = args[0].toLowerCase();

        if ("home".equals(sub)) {
            return handleHome(sender, args);
        }

        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "debug"  -> handleDebug(sender);
            default       -> false;
        };
    }

    private boolean handleReload(CommandSender sender) {
        plugin.getPluginConfig().reload();
        plugin.getLangLoader().reload(plugin.getPluginConfig().language());
        msg.send(sender, "general.reload-success", null);
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        plugin.getPluginConfig().toggleDebug();
        String key = plugin.getPluginConfig().isDebug() ? "general.debug-on" : "general.debug-off";
        msg.send(sender, key, null);
        return true;
    }

    // ── home subcommands ─────────────────────────────────────

    private boolean handleHome(CommandSender sender, String[] args) {
        // /sxtadmin home <player> <list|delete|tp> [name]
        if (args.length < 3) {
            return false;
        }

        if (!sender.hasPermission("sxt.manage.home.others")) {
            msg.send(sender, "general.no-permission", Map.of());
            return true;
        }

        String targetName = args[1];
        String sub = args[2].toLowerCase();

        return switch (sub) {
            case "list" -> {
                if (args.length != 3) yield false;
                yield handleHomeList(sender, targetName);
            }
            case "delete" -> {
                if (args.length != 4) yield false;
                yield handleHomeDelete(sender, targetName, args[3]);
            }
            case "tp" -> {
                if (args.length != 4) yield false;
                yield handleHomeTp(sender, targetName, args[3]);
            }
            default -> false;
        };
    }

    private boolean handleHomeList(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            msg.send(sender, "general.player-not-found", Map.of("target", targetName));
            return true;
        }

        homeDao.listByPlayer(target.getUniqueId()).thenAcceptAsync(homes -> {
            if (homes.isEmpty()) {
                msg.send(sender, "admin.home-list-empty",
                        Map.of("player", target.getName()));
            } else {
                msg.send(sender, "admin.home-list-header",
                        Map.of("player", target.getName(),
                                "count", String.valueOf(homes.size())));
                for (Home home : homes) {
                    msg.send(sender, "admin.home-list-entry", Map.of(
                            "home", home.name(),
                            "world", home.world(),
                            "x", String.valueOf((int) home.x()),
                            "y", String.valueOf((int) home.y()),
                            "z", String.valueOf((int) home.z())
                    ));
                }
            }
        }, mainThread).exceptionally(ex -> {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to list homes for " + targetName, ex);
            return null;
        });

        return true;
    }

    private boolean handleHomeDelete(CommandSender sender,
                                     String targetName, String homeName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            msg.send(sender, "general.player-not-found", Map.of("target", targetName));
            return true;
        }

        homeDao.findOne(target.getUniqueId(), homeName)
                .thenCompose(opt -> {
                    if (opt.isEmpty()) {
                        return CompletableFuture.completedFuture(Optional.<Home>empty());
                    }
                    return homeDao.delete(target.getUniqueId(), homeName)
                            .thenApply(v -> opt);
                })
                .thenAcceptAsync(opt -> {
                    if (opt.isEmpty()) {
                        msg.send(sender, "admin.home-not-found",
                                Map.of("player", target.getName(), "home", homeName));
                    } else {
                        msg.send(sender, "admin.home-deleted",
                                Map.of("player", target.getName(), "home", homeName));
                    }
                }, mainThread)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to delete home '" + homeName + "' for " + targetName, ex);
                    return null;
                });

        return true;
    }

    private boolean handleHomeTp(CommandSender sender,
                                 String targetName, String homeName) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.player-only", Map.of());
            return true;
        }

        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            msg.send(player, "general.player-not-found", Map.of("target", targetName));
            return true;
        }

        homeDao.findOne(target.getUniqueId(), homeName)
                .thenAcceptAsync(opt -> {
                    if (opt.isEmpty()) {
                        msg.send(player, "admin.home-not-found",
                                Map.of("player", target.getName(), "home", homeName));
                        return;
                    }

                    Home home = opt.get();
                    World world = plugin.getServer().getWorld(home.world());
                    if (world == null) {
                        msg.send(player, "world.not-found",
                                Map.of("world", home.world()));
                        return;
                    }

                    Location dest = new Location(world,
                            home.x(), home.y(), home.z(),
                            home.yaw(), home.pitch());

                    // Save back location for executor (non-blocking async DAO)
                    Location from = player.getLocation();
                    BackLocation back = new BackLocation(
                            player.getUniqueId(),
                            from.getWorld().getName(),
                            from.getX(), from.getY(), from.getZ(),
                            from.getYaw(), from.getPitch(),
                            System.currentTimeMillis(),
                            BackReason.TELEPORT
                    );
                    plugin.getBackLocationDao().upsert(back)
                            .exceptionally(ex -> {
                                plugin.getLogger().log(Level.WARNING,
                                        "Failed to save back location for admin tp", ex);
                                return null;
                            });

                    // Immediate teleport — no cost/CD pipeline
                    player.teleportAsync(dest,
                            PlayerTeleportEvent.TeleportCause.COMMAND)
                            .thenAccept(success -> {
                                if (Boolean.TRUE.equals(success)) {
                                    plugin.getServer().getScheduler()
                                            .runTask(plugin, () -> msg.send(player,
                                                    "admin.home-teleported",
                                                    Map.of("player", target.getName(),
                                                            "home", homeName)));
                                }
                            });
                }, mainThread)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to teleport to home '" + homeName
                                    + "' of " + targetName, ex);
                    return null;
                });

        return true;
    }
}
