package com.example.sxt.teleport;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandKey;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the last successful teleport epoch-millis per player and command key.
 * Used to enforce per-command cooldowns configured in {@code config.yml}.
 */
public final class CooldownManager {

    private final SimpleXpTeleportPlugin plugin;
    private final Map<UUID, Map<CommandKey, Long>> cooldowns = new ConcurrentHashMap<>();

    public CooldownManager(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Record that {@code player} has just used {@code key} at {@code epochMs}.
     */
    public void setLastUsed(Player player, CommandKey key, long epochMs) {
        cooldowns.computeIfAbsent(player.getUniqueId(), u -> new ConcurrentHashMap<>())
                .put(key, epochMs);
    }

    /**
     * Returns the cooldown seconds remaining for the given player+command, or {@code 0}
     * when no cooldown is configured, the cooldown has expired, or the player holds
     * a bypass permission ({@code sxt.bypass.cooldown.<cmd>} or {@code sxt.bypass.cooldown.*}).
     *
     * @param configSec cooldown duration in seconds as configured
     */
    public long remainingSeconds(Player player, CommandKey key, int configSec) {
        if (configSec <= 0) {
            return 0;
        }
        if (hasCooldownBypass(player, key)) {
            return 0;
        }

        Map<CommandKey, Long> playerMap = cooldowns.get(player.getUniqueId());
        if (playerMap == null) {
            return 0;
        }

        Long lastUsed = playerMap.get(key);
        if (lastUsed == null) {
            return 0;
        }

        long now = System.currentTimeMillis();
        long elapsed = now - lastUsed;
        long cooldownMs = configSec * 1000L;
        long remainingMs = cooldownMs - elapsed;
        if (remainingMs <= 0) {
            return 0;
        }

        return (long) Math.ceil(remainingMs / 1000.0);
    }

    private boolean hasCooldownBypass(Player player, CommandKey key) {
        if (player.hasPermission("sxt.bypass.cooldown.*")) {
            return true;
        }
        String cmdLower = key.name().toLowerCase();
        return player.hasPermission("sxt.bypass.cooldown." + cmdLower);
    }
}
