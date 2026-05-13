package com.example.sxt.teleport;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates random teleport destinations within a configurable annular ring
 * around the world spawn, delegating safety refinement to {@link SafetyChecker}.
 */
public final class RandomLocationFinder {

    private final SimpleXpTeleportPlugin plugin;
    private final SafetyChecker safetyChecker;

    public RandomLocationFinder(SimpleXpTeleportPlugin plugin, SafetyChecker safetyChecker) {
        this.plugin = plugin;
        this.safetyChecker = safetyChecker;
    }

    /**
     * Attempts to find a safe random location in {@code world} according to
     * the {@link CommandConfig} settings ({@code minRadius}, {@code maxRadius},
     * {@code maxAttempts}, {@code safeSearchRadius}).
     *
     * <p>The centre of the search area is {@code world.getSpawnLocation()}.
     * For each attempt a random angle ({@code 0..2π}) and a random radius
     * ({@code minRadius..maxRadius}) are chosen, and safety is verified via
     * {@link SafetyChecker#findSafe(Location, int)}.
     *
     * @return the first safe location found, or {@link Optional#empty()} when
     *         all attempts are exhausted
     */
    public Optional<Location> find(World world, CommandConfig config) {
        return find(world, config, config.minRadius(), config.maxRadius());
    }

    /**
     * Like {@link #find(World, CommandConfig)} but overrides the radius range
     * with explicit {@code minRadius} / {@code maxRadius} values.
     *
     * <p>Used by /rtpx when the player specifies a custom search range at
     * command invocation. Config values for {@code safeSearchRadius} and
     * {@code maxAttempts} are still honoured.</p>
     *
     * @param world    the target world
     * @param config   the command configuration
     * @param minRadius minimum radius (inclusive) from spawn
     * @param maxRadius maximum radius (inclusive) from spawn
     * @return the first safe location found, or {@link Optional#empty()} when
     *         all attempts are exhausted
     */
    public Optional<Location> find(World world, CommandConfig config,
                                   int minRadius, int maxRadius) {
        Location center = world.getSpawnLocation();
        int maxAttempts = config.maxAttempts();
        int safeSearchRadius = config.safeSearchRadius();

        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < maxAttempts; i++) {
            // Pick a random radius within [minRadius, maxRadius] and a random angle
            int radius = random.nextInt(minRadius, maxRadius + 1);
            double angle = random.nextDouble() * 2.0 * Math.PI;

            int dx = (int) Math.round(radius * Math.cos(angle));
            int dz = (int) Math.round(radius * Math.sin(angle));

            int x = center.getBlockX() + dx;
            int z = center.getBlockZ() + dz;

            // The initial Y is arbitrary — findSafe recomputes every candidate Y
            // from getHighestBlockYAt.  Use 0 as a dummy so the Location is valid.
            Location candidate = new Location(world, x + 0.5, 0, z + 0.5);

            Optional<Location> safe = safetyChecker.findSafe(candidate, safeSearchRadius);
            if (safe.isPresent()) {
                return safe;
            }
        }

        return Optional.empty();
    }
}
