package com.example.sxt.listener;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.data.model.BackLocation;
import com.example.sxt.data.model.BackLocation.BackReason;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

import java.util.Set;
import java.util.logging.Level;

/**
 * Listens for teleport events to optionally record back-locations
 * for non-plugin teleports (ender pearls, chorus fruit, etc.).
 *
 * <p>Controlled by {@code back.record-non-plugin-teleports} in
 * {@code config.yml} (default {@code false}). When enabled, back
 * locations are saved for teleports with causes
 * {@link TeleportCause#ENDER_PEARL}, {@link TeleportCause#CHORUS_FRUIT},
 * and {@link TeleportCause#PLUGIN} (i.e. teleports not triggered by
 * this plugin's pipeline).</p>
 */
public final class PlayerTeleportListener implements Listener {

    private static final Set<TeleportCause> RECORDABLE_CAUSES = Set.of(
            TeleportCause.ENDER_PEARL,
            TeleportCause.CONSUMABLE_EFFECT, // chorus fruit (& other consumables) since 1.21.3
            TeleportCause.PLUGIN
    );

    private final SimpleXpTeleportPlugin plugin;

    public PlayerTeleportListener(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.getPluginConfig().isBackRecordNonPluginTeleports()) {
            return;
        }

        if (!RECORDABLE_CAUSES.contains(event.getCause())) {
            return;
        }

        Player player = event.getPlayer();
        Location from = event.getFrom();

        BackLocation back = new BackLocation(
                player.getUniqueId(),
                from.getWorld().getName(),
                from.getX(), from.getY(), from.getZ(),
                from.getYaw(), from.getPitch(),
                System.currentTimeMillis(),
                BackReason.TELEPORT
        );

        // Non-blocking async DAO call — do NOT join/get on main thread
        plugin.getBackLocationDao().upsert(back)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to save back location from non-plugin teleport", ex);
                    return null;
                });
    }
}
