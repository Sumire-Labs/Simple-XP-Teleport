package com.example.sxt.teleport;

import com.example.sxt.SimpleXpTeleportPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * Determines whether a given {@link Location} is safe for a player to land on.
 * <p>
 * A location is considered safe when:
 * <ul>
 *   <li>Its Y coordinate is within {@code [world.minHeight .. world.maxHeight-2]}.</li>
 *   <li>The block directly beneath the feet ({@code y-1}) is solid.</li>
 *   <li>The blocks at feet level ({@code y}) and head level ({@code y+1}) are both
 *       non-solid (no collision) and do not contain liquids, fire, magma, or cactus.</li>
 * </ul>
 *
 * <p>{@link #findSafe(Location, int)} performs a horizontal spiral search around a
 * centre, using {@code getHighestBlockYAt + 1} as the candidate Y for each (x,z)
 * position until a safe spot is found or the maximum attempt count ({@code radius²})
 * is reached.
 */
public final class SafetyChecker {

    private static final Set<Material> UNSAFE_MATERIALS = EnumSet.of(
            Material.LAVA,
            Material.WATER,
            Material.FIRE,
            Material.SOUL_FIRE,
            Material.MAGMA_BLOCK,
            Material.CACTUS
    );

    private final SimpleXpTeleportPlugin plugin;

    public SafetyChecker(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Single-location check ───────────────────────────────

    /**
     * Returns {@code true} when a player can safely stand at the given location.
     */
    public boolean isSafe(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Vertical range: must leave room for head at y+1
        if (y < world.getMinHeight() || y > world.getMaxHeight() - 2) {
            return false;
        }

        // Block under feet must be solid
        Block ground = world.getBlockAt(x, y - 1, z);
        if (!ground.getType().isSolid()) {
            return false;
        }

        // Feet position: must be non-solid (fit in) and not dangerous
        Block feet = world.getBlockAt(x, y, z);
        if (feet.getType().isSolid()) {
            return false;
        }
        if (isUnsafe(feet.getType())) {
            return false;
        }

        // Head position: same conditions
        Block head = world.getBlockAt(x, y + 1, z);
        if (head.getType().isSolid()) {
            return false;
        }
        if (isUnsafe(head.getType())) {
            return false;
        }

        return true;
    }

    // ── Area search ─────────────────────────────────────────

    /**
     * Searches horizontally in a spiral pattern around {@code center} for a safe
     * landing spot. For each (x,z) position the candidate Y is taken from
     * {@code world.getHighestBlockYAt(x,z) + 1}.
     *
     * @param center the centre of the search area
     * @param radius maximum horizontal offset to explore; at most {@code radius²}
     *               positions are tested
     * @return the first safe location found, or {@link Optional#empty()} when no
     *         position within the radius is safe
     */
    public Optional<Location> findSafe(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        int cx = center.getBlockX();
        int cz = center.getBlockZ();
        int maxAttempts = radius * radius;
        int attempts = 0;

        // Spiral state
        int dx = 0;
        int dz = 0;

        while (attempts < maxAttempts) {
            int x = cx + dx;
            int z = cz + dz;

            int y = world.getHighestBlockYAt(x, z) + 1;

            // Only try positions whose candidate Y is within the world height range
            if (y >= world.getMinHeight() && y <= world.getMaxHeight() - 2) {
                Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                if (isSafe(candidate)) {
                    return Optional.of(candidate);
                }
            }

            attempts++;

            // Advance to the next spiral position
            if (dx == 0 && dz == 0) {
                dx = 1;          // move one block east to start layer 1
            } else {
                int layer = Math.max(Math.abs(dx), Math.abs(dz));

                if (dx == layer && dz > -layer && dz < layer) {
                    dz++;        // north along right edge
                } else if (dz == layer && dx > -layer) {
                    dx--;        // west along top edge
                } else if (dx == -layer && dz > -layer) {
                    dz--;        // south along left edge
                } else if (dz == -layer && dx < layer) {
                    dx++;        // east along bottom edge
                } else {
                    dx++;        // step into the next layer
                }
            }
        }

        return Optional.empty();
    }

    // ── Helpers ─────────────────────────────────────────────

    private static boolean isUnsafe(Material material) {
        return UNSAFE_MATERIALS.contains(material);
    }
}
