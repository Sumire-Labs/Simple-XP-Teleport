package com.example.sxt.permission;

import com.example.sxt.SimpleXpTeleportPlugin;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Resolves how many waypoints a player can set.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code sxt.waypoints.unlimited} → {@link Integer#MAX_VALUE}</li>
 *   <li>Highest {@code n} from any granted {@code sxt.waypoints.max.<n>}</li>
 *   <li>{@code config.waypoints.max-per-player}</li>
 * </ol></p>
 */
public final class WaypointLimitResolver {

    private final SimpleXpTeleportPlugin plugin;

    public WaypointLimitResolver(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolve the maximum number of waypoints for the given player.
     *
     * @param p the player (must not be null)
     * @return effective waypoint limit (≥ 0, or {@link Integer#MAX_VALUE} for unlimited)
     */
    public int resolve(Player p) {
        if (p.hasPermission("sxt.waypoints.unlimited")) {
            return Integer.MAX_VALUE;
        }

        int max = -1;
        for (PermissionAttachmentInfo permInfo : p.getEffectivePermissions()) {
            String perm = permInfo.getPermission();
            if (perm.startsWith("sxt.waypoints.max.") && permInfo.getValue()) {
                try {
                    int n = Integer.parseInt(perm.substring("sxt.waypoints.max.".length()));
                    if (n > max) {
                        max = n;
                    }
                } catch (NumberFormatException ignored) {
                    // non-numeric suffix — skip
                }
            }
        }

        if (max > 0) {
            return max;
        }
        return plugin.getPluginConfig().waypointsMaxPerPlayer();
    }
}
