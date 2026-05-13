package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import com.example.sxt.config.CommandKey;
import com.example.sxt.message.MessageService;
import com.example.sxt.teleport.TeleportRequest;
import com.example.sxt.teleport.TeleportRequest.Pending;
import com.example.sxt.teleport.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * /tpacceptx — accept the most recent pending TPA or TPAHERE request.
 *
 * <p>Cost and cooldown are always charged to the requester.
 * For TPA the requester teleports to the target; for TPAHERE
 * the target teleports to the requester.</p>
 */
public final class TpaAcceptCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final TeleportRequest teleportRequest;
    private final TeleportService teleportService;

    public TpaAcceptCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.teleportRequest = plugin.getTeleportRequest();
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

        if (args.length > 0) {
            msg.send(sender, "general.usage", Map.of("usage", command.getUsage()));
            return true;
        }

        Optional<Pending> pendingOpt = teleportRequest.getPending(player.getUniqueId());
        if (pendingOpt.isEmpty()) {
            msg.send(player, "tpa.no-pending", Map.of());
            return true;
        }
        Pending pending = pendingOpt.get();

        // Determine the config key for timeout / command config
        CommandKey key = pending.type() == Pending.Type.TPA
                ? CommandKey.TPAX : CommandKey.TPAHERE;
        CommandConfig cfg = plugin.getPluginConfig().commandConfig(key);

        // Check timeout
        if (cfg != null) {
            long timeoutMs = cfg.requestTimeoutSeconds() * 1000L;
            if (System.currentTimeMillis() - pending.createdAtMs() > timeoutMs) {
                teleportRequest.removePending(player.getUniqueId());
                msg.send(player, "tpa.expired", Map.of());
                Player requester = Bukkit.getPlayer(pending.requesterUuid());
                if (requester != null && requester.isOnline()) {
                    msg.send(requester, "tpa.expired", Map.of());
                }
                return true;
            }
        }

        Player requester = Bukkit.getPlayer(pending.requesterUuid());
        if (requester == null || !requester.isOnline()) {
            teleportRequest.removePending(player.getUniqueId());
            OfflinePlayer offline = Bukkit.getOfflinePlayer(pending.requesterUuid());
            String name = offline.getName();
            msg.send(player, "general.player-not-found",
                    Map.of("target", name != null ? name : "?"));
            return true;
        }

        // Remove pending before initiating teleport to prevent double-accept
        teleportRequest.removePending(player.getUniqueId());

        if (pending.type() == Pending.Type.TPA) {
            // Requester teleports to target (player). Requester pays.
            // <player> placeholder = target's name
            teleportService.requestTeleport(requester,
                    player.getLocation(), CommandKey.TPAX,
                    Map.of("player", player.getName()));
        } else {
            // TPAHERE: target (player) teleports to requester.
            // Requester still pays cost and cooldown.
            // <player> placeholder = requester's name (the player the mover is going to)
            teleportService.requestTeleportWithPayer(player, requester,
                    requester.getLocation(), CommandKey.TPAHERE,
                    Map.of("player", requester.getName()));
        }

        msg.send(player, "tpa.accepted", Map.of());
        return true;
    }
}
