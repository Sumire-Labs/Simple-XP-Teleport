package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
import com.example.sxt.data.dao.WarpDao;
import com.example.sxt.data.model.Warp;
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
 * /warpx <name> — teleports the player to a public warp.
 *
 * <p>The warp is loaded via {@link WarpDao#findOne} on an async thread;
 * the teleport is then initiated on the main thread through
 * {@link TeleportService#requestTeleport}.</p>
 */
public final class WarpCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final WarpDao warpDao;
    private final TeleportService teleportService;
    private final Executor mainThread;

    public WarpCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.warpDao = plugin.getWarpDao();
        this.teleportService = plugin.getTeleportService();
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        // No arguments → open GUI for players
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                msg.send(sender, "general.player-only", Map.of());
                return true;
            }
            plugin.getWarpGuiService().openPublicGui(player);
            return true;
        }

        // Too many arguments
        if (args.length > 1) {
            msg.send(sender, "general.usage", Map.of("usage", command.getUsage()));
            return true;
        }

        // Exactly 1 argument → existing warp teleport
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.player-only", Map.of());
            return true;
        }

        String warpName = args[0];

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

            teleportService.requestTeleport(player, dest, CommandKey.WARPX,
                    Map.of("warp", warpName));
        }, mainThread);

        return true;
    }
}