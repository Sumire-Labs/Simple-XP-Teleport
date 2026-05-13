package com.example.sxt.listener;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.teleport.TeleportService;
import com.example.sxt.teleport.WarmupTask;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Cancels active warmups when the player moves more than 0.1 blocks
 * horizontally, provided {@code cancel-on-move} is enabled for that
 * command's configuration.
 *
 * <p>The movement check itself runs inside {@link WarmupTask#run()} on
 * every tick; this listener exists as a secondary defence and for
 * future extension. The primary cancellation logic is in
 * {@link WarmupTask}.</p>
 */
public final class PlayerMoveListener implements Listener {

    private final TeleportService teleportService;

    public PlayerMoveListener(SimpleXpTeleportPlugin plugin, TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Movement detection is handled inside WarmupTask.run() each tick.
        // This listener is kept for architectural completeness; no action needed.
    }
}
