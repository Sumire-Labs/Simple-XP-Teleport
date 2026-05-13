package com.example.sxt.listener;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.teleport.TeleportService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Cancels active warmups when the player takes damage, provided
 * {@code cancel-on-damage} is configured for that command.
 */
public final class EntityDamageListener implements Listener {

    private final TeleportService teleportService;

    public EntityDamageListener(SimpleXpTeleportPlugin plugin, TeleportService teleportService) {
        this.teleportService = teleportService;
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        // Delegate to TeleportService which checks active warmup + cancel-on-damage
        teleportService.onPlayerDamaged(player);
    }
}
