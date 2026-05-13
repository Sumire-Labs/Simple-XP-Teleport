package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.data.dao.WarpDao;
import com.example.sxt.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * /delwarpx <name> — deletes a public warp.
 *
 * <p>Requires permission {@code sxt.manage.warp}. If the warp does not
 * exist {@code warp.not-found} is sent.</p>
 */
public final class DelWarpCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final WarpDao warpDao;
    private final Executor mainThread;

    public DelWarpCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.warpDao = plugin.getWarpDao();
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

        if (!player.hasPermission("sxt.manage.warp")) {
            msg.send(player, "general.no-permission", Map.of());
            return true;
        }

        if (args.length != 1) {
            msg.send(sender, "general.usage", Map.of("usage", command.getUsage()));
            return true;
        }

        String warpName = args[0];

        warpDao.findOne(warpName).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                msg.send(player, "warp.not-found", Map.of("warp", warpName));
                return;
            }
            warpDao.delete(warpName).thenAcceptAsync(v ->
                    msg.send(player, "warp.deleted", Map.of("warp", warpName)),
                    mainThread);
        }, mainThread);

        return true;
    }
}