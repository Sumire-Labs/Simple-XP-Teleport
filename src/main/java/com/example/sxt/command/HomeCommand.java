package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
import com.example.sxt.data.dao.HomeDao;
import com.example.sxt.data.model.Home;
import com.example.sxt.message.MessageService;
import com.example.sxt.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * /homex [name] — teleports the player to a saved home.
 *
 * <p>Name defaults to {@code "home"}. The home is loaded via
 * {@link HomeDao#findOne} on an async thread; the teleport is then
 * initiated on the main thread through {@link TeleportService#requestTeleport}.</p>
 */
public final class HomeCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final HomeDao homeDao;
    private final TeleportService teleportService;
    private final Executor mainThread;

    public HomeCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.homeDao = plugin.getHomeDao();
        this.teleportService = plugin.getTeleportService();
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

        if (args.length > 1) {
            msg.send(sender, "general.usage", Map.of("usage", command.getUsage()));
            return true;
        }

        String homeName = args.length > 0 ? args[0] : "home";

        homeDao.findOne(player.getUniqueId(), homeName).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                msg.send(player, "home.not-found", Map.of("home", homeName));
                return;
            }

            Home home = opt.get();
            World world = Bukkit.getWorld(home.world());
            if (world == null) {
                msg.send(player, "home.world-not-found",
                        Map.of("world", home.world()));
                return;
            }

            Location dest = new Location(world,
                    home.x(), home.y(), home.z(),
                    home.yaw(), home.pitch());

            teleportService.requestTeleport(player, dest, CommandKey.HOMEX,
                    Map.of("home", homeName));
        }, mainThread);

        return true;
    }
}