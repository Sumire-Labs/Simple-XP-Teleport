package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
import com.example.sxt.data.dao.BackLocationDao;
import com.example.sxt.data.model.BackLocation;
import com.example.sxt.message.MessageService;
import com.example.sxt.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * /backx — teleports the player to their previous recorded location.
 *
 * <p>The back location is loaded via {@link BackLocationDao#find} on an
 * async thread (never {@code join/get} on the main thread).  Once the
 * result is available execution returns to the main thread via
 * {@code thenAcceptAsync(..., mainThread)} so that Bukkit APIs
 * ({@code getWorld}, {@link MessageService#send},
 * {@link TeleportService#requestTeleport}) are called safely.</p>
 *
 * <p>{@link TeleportService} itself does <em>not</em> save a new back
 * location when the command key is {@link CommandKey#BACKX} (see Step 6
 * pipeline item 10).  This avoids overwriting the previous position.</p>
 */
public final class BackCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final BackLocationDao backLocationDao;
    private final TeleportService teleportService;
    private final Executor mainThread;

    public BackCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.backLocationDao = plugin.getBackLocationDao();
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

        // ── Async DAO lookup ────────────────────────────────
        backLocationDao.find(player.getUniqueId())
                .thenAcceptAsync(opt -> {
                    if (opt.isEmpty()) {
                        msg.send(player, "back.no-previous", Map.of());
                        return;
                    }

                    BackLocation back = opt.get();
                    World world = Bukkit.getWorld(back.world());
                    if (world == null) {
                        msg.send(player, "world.not-found",
                                Map.of("world", back.world()));
                        return;
                    }

                    Location dest = new Location(world,
                            back.x(), back.y(), back.z(),
                            back.yaw(), back.pitch());

                    teleportService.requestTeleport(player, dest, CommandKey.BACKX);
                }, mainThread)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to retrieve back location", ex);
                    return null;
                });

        return true;
    }
}
