package com.example.sxt.permission;

import com.example.sxt.SimpleXpTeleportPlugin;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

/**
 * Resolves how many homes a player can set.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>{@code sxt.homes.unlimited} → {@link Integer#MAX_VALUE}</li>
 *   <li>Highest {@code n} from any granted {@code sxt.homes.max.<n>}</li>
 *   <li>{@code config.home.default-max-count}</li>
 * </ol></p>
 */
public final class HomeLimitResolver {

    private final SimpleXpTeleportPlugin plugin;

    public HomeLimitResolver(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolve the maximum number of homes for the given player.
     *
     * @param p the player (must not be null)
     * @return effective home limit (≥ 0, or {@link Integer#MAX_VALUE} for unlimited)
     */
    public int resolve(Player p) {
        if (p.hasPermission("sxt.homes.unlimited")) {
            return Integer.MAX_VALUE;
        }

        int max = -1;
        for (PermissionAttachmentInfo permInfo : p.getEffectivePermissions()) {
            String perm = permInfo.getPermission();
            if (perm.startsWith("sxt.homes.max.") && permInfo.getValue()) {
                try {
                    int n = Integer.parseInt(perm.substring("sxt.homes.max.".length()));
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
        return plugin.getPluginConfig().defaultMaxHomes();
    }
}
