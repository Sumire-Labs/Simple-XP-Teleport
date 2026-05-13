package com.example.sxt.teleport;

import com.example.sxt.SimpleXpTeleportPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tags both participants with the current epoch-millis whenever a player damages
 * another player ({@link EntityDamageByEntityEvent}). A player is considered
 * "in combat" until {@code pvpDuration} seconds have elapsed since their last tag.
 */
public final class CombatTagManager implements Listener {

    private final SimpleXpTeleportPlugin plugin;
    private final Map<UUID, Long> taggedAt = new ConcurrentHashMap<>();

    public CombatTagManager(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) {
            return;
        }
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        // Player-vs-player damage only — tag both participants
        long now = System.currentTimeMillis();
        taggedAt.put(damager.getUniqueId(), now);
        taggedAt.put(victim.getUniqueId(), now);
    }

    /**
     * Returns {@code true} when the player is still within the combat-tag window.
     * Equivalent to {@code now < taggedAt + pvpDuration * 1000}.
     */
    public boolean isInCombat(Player player) {
        Long tagged = taggedAt.get(player.getUniqueId());
        if (tagged == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        long pvpDuration = plugin.getPluginConfig().combatTagPvpDuration();
        return now < tagged + pvpDuration * 1000L;
    }

    /**
     * Returns remaining combat-tag seconds (ceil-rounded), or {@code 0}
     * when the player is not currently in combat.
     */
    public long remainingSeconds(Player player) {
        Long tagged = taggedAt.get(player.getUniqueId());
        if (tagged == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long pvpDuration = plugin.getPluginConfig().combatTagPvpDuration();
        long expiresAt = tagged + pvpDuration * 1000L;
        if (now >= expiresAt) {
            return 0;
        }
        return (long) Math.ceil((expiresAt - now) / 1000.0);
    }
}
