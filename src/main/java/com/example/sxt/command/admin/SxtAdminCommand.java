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
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * /sxtadmin reload | debug | warp | home &lt;player&gt; list | home &lt;player&gt; delete &lt;name&gt; | home &lt;player&gt; tp &lt;name&gt;
 */
public final class SxtAdminCommand implements CommandExecutor, TabCompleter {

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
            msg.send(sender, "general.usage", Map.of("usage", command.getUsage()));
            return true;
        }

        String sub = args[0].toLowerCase();

        if ("home".equals(sub)) {
            return handleHome(sender, command, args);
        }

        if ("warp".equals(sub))   return handleWarp(sender);
        if ("reload".equals(sub)) return handleReload(sender);
        if ("debug".equals(sub))  return handleDebug(sender);

        // Typo suggestion for unknown subcommand
        String suggestion = closestSubcommand(sub);
        if (suggestion != null) {
            msg.send(sender, "admin.unknown-subcommand",
                    Map.of("sub", args[0], "suggest", suggestion));
        } else {
            msg.send(sender, "general.usage", Map.of("usage", command.getUsage()));
        }
        return true;
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

    private boolean handleWarp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.player-only", Map.of());
            return true;
        }
        if (!player.hasPermission("sxt.admin.warp.gui")) {
            msg.send(player, "general.no-permission", Map.of());
            return true;
        }
        plugin.getWarpGuiService().openAdminGui(player);
        return true;
    }

    // ── home subcommands ─────────────────────────────────────

    private boolean handleHome(CommandSender sender, Command command, String[] args) {
        // /sxtadmin home <player> <list|delete|tp> [name]
        if (args.length < 3) {
            msg.send(sender, "general.usage",
                    Map.of("usage", "/sxtadmin home <player> <list|delete|tp> [name]"));
            return true;
        }

        if (!sender.hasPermission("sxt.manage.home.others")) {
            msg.send(sender, "general.no-permission", Map.of());
            return true;
        }

        String targetName = args[1];
        String sub = args[2].toLowerCase();

        switch (sub) {
            case "list":
                if (args.length != 3) {
                    msg.send(sender, "general.usage",
                            Map.of("usage", "/sxtadmin home <player> list"));
                    return true;
                }
                return handleHomeList(sender, targetName);
            case "delete":
                if (args.length != 4) {
                    msg.send(sender, "general.usage",
                            Map.of("usage", "/sxtadmin home <player> delete <name>"));
                    return true;
                }
                return handleHomeDelete(sender, targetName, args[3]);
            case "tp":
                if (args.length != 4) {
                    msg.send(sender, "general.usage",
                            Map.of("usage", "/sxtadmin home <player> tp <name>"));
                    return true;
                }
                return handleHomeTp(sender, targetName, args[3]);
            default:
                msg.send(sender, "general.usage",
                        Map.of("usage", "/sxtadmin home <player> <list|delete|tp> [name]"));
                return true;
        }
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

    // ── Tab completion ────────────────────────────────────────

    private static final List<String> SUBCOMMANDS = List.of("reload", "debug", "warp", "home");
    private static final List<String> HOME_SUBCOMMANDS = List.of("list", "delete", "tp");

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String label,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(partial)) {
                    matches.add(sub);
                }
            }
            // Typo tolerance: if no prefix match, suggest based on edit distance ≤ 2
            if (matches.isEmpty() && !partial.isEmpty()) {
                String closest = closestSubcommand(partial);
                if (closest != null) {
                    matches.add(closest);
                }
            }
            return matches;
        }

        if (args.length >= 2 && "home".equalsIgnoreCase(args[0])) {
            if (args.length == 3) {
                String partial = args[2].toLowerCase();
                List<String> matches = new ArrayList<>();
                for (String sub : HOME_SUBCOMMANDS) {
                    if (sub.startsWith(partial)) {
                        matches.add(sub);
                    }
                }
                return matches;
            }
            // args.length == 2: suggest online player names
            if (args.length == 2 && sender.hasPermission("sxt.manage.home.others")) {
                String partial = args[1].toLowerCase();
                List<String> names = new ArrayList<>();
                for (org.bukkit.entity.Player online : org.bukkit.Bukkit.getOnlinePlayers()) {
                    if (online.getName().toLowerCase().startsWith(partial)) {
                        names.add(online.getName());
                    }
                }
                return names;
            }
        }

        return List.of();
    }

    // ── Typo suggestion (Levenshtein distance) ────────────────

    /** Return the closest subcommand for {@code input} if Levenshtein distance ≤ 2, else null. */
    @Nullable
    private static String closestSubcommand(String input) {
        String lower = input.toLowerCase();
        String best = null;
        int bestDist = Integer.MAX_VALUE;
        for (String candidate : SUBCOMMANDS) {
            int dist = levenshtein(lower, candidate);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return bestDist <= 2 ? best : null;
    }

    /** Compute Levenshtein edit distance between two strings. */
    private static int levenshtein(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[][] d = new int[n + 1][m + 1];
        for (int i = 0; i <= n; i++) d[i][0] = i;
        for (int j = 0; j <= m; j++) d[0][j] = j;
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(
                        Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1),
                        d[i - 1][j - 1] + cost
                );
            }
        }
        return d[n][m];
    }
}
