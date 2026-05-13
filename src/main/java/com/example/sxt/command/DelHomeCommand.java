package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.data.dao.HomeDao;
import com.example.sxt.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * /delhomex [name] — deletes a saved home.
 *
 * <p>Name defaults to {@code "home"}. If the home does not exist
 * {@code home.not-found} is sent.</p>
 */
public final class DelHomeCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final HomeDao homeDao;
    private final Executor mainThread;

    public DelHomeCommand(SimpleXpTeleportPlugin plugin) {
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
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.player-only", Map.of());
            return true;
        }

        if (args.length > 1) {
            msg.send(sender, "general.usage", Map.of("usage", command.getUsage()));
            return true;
        }

        String homeName = args.length > 0 ? args[0] : "home";
        var uuid = player.getUniqueId();

        homeDao.findOne(uuid, homeName).thenAcceptAsync(opt -> {
            if (opt.isEmpty()) {
                msg.send(player, "home.not-found", Map.of("home", homeName));
                return;
            }
            homeDao.delete(uuid, homeName).thenAcceptAsync(v ->
                    msg.send(player, "home.deleted", Map.of("home", homeName)),
                    mainThread);
        }, mainThread);

        return true;
    }
}