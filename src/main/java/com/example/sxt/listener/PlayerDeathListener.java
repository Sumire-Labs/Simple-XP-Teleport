package com.example.sxt.listener;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.data.dao.BackLocationDao;
import com.example.sxt.data.model.BackLocation;
import com.example.sxt.data.model.BackLocation.BackReason;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Saves the player's death location to the {@code back_locations} table
 * so that {@code /backx} can return them to the place they died.
 */
public final class PlayerDeathListener implements Listener {

    private final SimpleXpTeleportPlugin plugin;
    private final BackLocationDao backLocationDao;

    public PlayerDeathListener(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.backLocationDao = plugin.getBackLocationDao();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Location loc = player.getLocation();

        BackLocation back = new BackLocation(
                player.getUniqueId(),
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                System.currentTimeMillis(),
                BackReason.DEATH
        );

        // Non-blocking async DAO call — DO NOT join/get on the main thread
        backLocationDao.upsert(back);
    }
}
