package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
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

/**
 * /tpposx &lt;x&gt; &lt;y&gt; &lt;z&gt; [world] — teleports the player to
 * explicit coordinates.
 *
 * <p>Numeric validation and Y-range checks are performed synchronously
 * before the teleport pipeline via
 * {@link TeleportService#requestTeleport}.</p>
 */
public final class TpPosCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final TeleportService teleportService;

    public TpPosCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
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

        if (args.length < 3) {
            return false; // let Bukkit show usage from plugin.yml
        }

        // ── Parse coordinates ───────────────────────────────
        double x, y, z;
        try {
            x = Double.parseDouble(args[0]);
            y = Double.parseDouble(args[1]);
            z = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            msg.send(player, "tppos.invalid-coordinates", Map.of());
            return true;
        }

        // ── Determine target world ──────────────────────────
        World world;
        if (args.length > 3) {
            world = Bukkit.getWorld(args[3]);
            if (world == null) {
                msg.send(player, "world.not-found", Map.of("world", args[3]));
                return true;
            }
        } else {
            world = player.getWorld();
        }

        // ── Validate Y range ────────────────────────────────
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
            msg.send(player, "tppos.y-out-of-range", Map.of(
                    "min", String.valueOf(world.getMinHeight()),
                    "max", String.valueOf(world.getMaxHeight() - 1)));
            return true;
        }

        // ── Initiate teleport ───────────────────────────────
        Location dest = new Location(world, x, y, z);
        teleportService.requestTeleport(player, dest, CommandKey.TPPOSX);

        return true;
    }
}
