package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.message.MessageService;
import com.example.sxt.teleport.TeleportRequest;
import com.example.sxt.teleport.TeleportRequest.Pending;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * /tpdenyx — deny the most recent pending TPA or TPAHERE request.
 */
public final class TpaDenyCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final TeleportRequest teleportRequest;

    public TpaDenyCommand(SimpleXpTeleportPlugin plugin) {
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

        Optional<Pending> pendingOpt = teleportRequest.removePending(player.getUniqueId());
        if (pendingOpt.isEmpty()) {
            msg.send(player, "tpa.no-pending", Map.of());
            return true;
        }

        msg.send(player, "tpa.denied", Map.of());
        return true;
    }
}
