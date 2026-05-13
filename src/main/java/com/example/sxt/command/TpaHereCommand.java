package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.message.MessageService;
import com.example.sxt.teleport.TeleportRequest;
import com.example.sxt.teleport.TeleportRequest.Pending;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * /tpahere <player> — send a TPAHERE request asking the target to teleport to you.
 */
public final class TpaHereCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final TeleportRequest teleportRequest;

    public TpaHereCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.teleportRequest = plugin.getTeleportRequest();
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

        if (args.length < 1) {
            return false;
        }

        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        if (target == null || !target.isOnline()) {
            msg.send(player, "general.player-not-found",
                    Map.of("target", targetName));
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            msg.send(player, "general.player-not-found",
                    Map.of("target", targetName));
            return true;
        }

        Pending pending = new Pending(player.getUniqueId(),
                target.getUniqueId(), Pending.Type.TPAHERE,
                System.currentTimeMillis());
        teleportRequest.putPending(target.getUniqueId(), pending);

        msg.send(player, "tpa.sent", Map.of("target", target.getName()));
        msg.send(target, "tpa.received-here",
                Map.of("player", player.getName()));
        return true;
    }
}
