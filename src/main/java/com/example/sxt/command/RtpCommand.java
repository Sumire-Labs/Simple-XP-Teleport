package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
import com.example.sxt.message.MessageService;
import com.example.sxt.teleport.RandomLocationFinder;
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
import java.util.Optional;

/**
 * /rtpx &lt;min&gt; &lt;max&gt; &lt;world&gt; — teleports the player to a random safe location
 * within the specified annular radius range.
 *
 * <p>The random location is found via {@link RandomLocationFinder} on the
 * <strong>main thread</strong> because the underlying
 * {@code World#getHighestBlockYAt} / {@code World#getBlockAt} API calls are
 * not thread-safe. Once a safe location is determined the teleport is
 * initiated through {@link TeleportService#requestTeleport(Player, Location,
 * CommandKey, int)} with distance override (also main-thread only per §4.1).</p>
 */
public final class RtpCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final RandomLocationFinder randomLocationFinder;
    private final TeleportService teleportService;

    public RtpCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.randomLocationFinder = plugin.getRandomLocationFinder();
        this.teleportService = plugin.getTeleportService();
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

        if (args.length != 3) {
            msg.send(sender, "general.usage", Map.of("usage", command.getUsage()));
            return true;
        }

        // ── Parse min / max ─────────────────────────────────
        int min;
        int max;
        try {
            min = Integer.parseInt(args[0]);
            max = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            msg.send(player, "rtp.invalid-range", Map.of());
            return true;
        }

        if (min < 0 || max < min) {
            msg.send(player, "rtp.invalid-range", Map.of());
            return true;
        }

        // ── Determine target world ──────────────────────────
        World world = Bukkit.getWorld(args[2]);
        if (world == null) {
            msg.send(player, "world.not-found", Map.of("world", args[2]));
            return true;
        }

        // ── Send searching message immediately ──────────────
        msg.send(player, "rtp.searching", Map.of());

        // ── Find random location (main thread — World API is not thread-safe) ──
        var config = plugin.getPluginConfig().commandConfig(CommandKey.RTPX);
        Optional<Location> opt = randomLocationFinder.find(world, config, min, max);

        if (opt.isEmpty()) {
            msg.send(player, "safety.no-safe-location", Map.of());
            return true;
        }

        // Distance override: cost is based on the specified max radius
        teleportService.requestTeleport(player, opt.get(), CommandKey.RTPX, max);
        return true;
    }
}
