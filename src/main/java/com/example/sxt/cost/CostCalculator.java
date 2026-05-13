package com.example.sxt.cost;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import org.bukkit.Location;

/**
 * Computes teleport costs according to the formulas defined in §4.2.
 */
public final class CostCalculator {

    public CostCalculator(SimpleXpTeleportPlugin plugin) {
    }

    /**
     * Return total cost as integer.
     * For {@link CostMode#LEVEL} mode the value represents levels;
     * for {@link CostMode#POINTS} mode it represents total xp points.
     */
    public int calculate(CommandConfig cfg, Location from, Location to) {
        if (cfg.costType() == CostType.FIXED) {
            int cost = cfg.amount();
            if (isCrossWorld(from, to)) {
                cost += cfg.crossWorldExtra();
            }
            return Math.max(0, cost);
        }

        // CostType.DISTANCE — §4.2 距離コスト計算式
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        double raw = cfg.base() + cfg.perBlock() * distance;

        if (isCrossWorld(from, to)) {
            raw += cfg.crossWorldExtra();
        }

        int cost = (int) Math.round(raw);
        return Math.max(cfg.min(), Math.min(cfg.max(), cost));
    }

    /**
     * Like {@link #calculate(CommandConfig, Location, Location)} but uses
     * {@code distanceOverride} instead of the actual Euclidean distance for
     * {@link CostType#DISTANCE} cost calculations.
     * For {@link CostType#FIXED} the override is ignored.
     *
     * <p>Used by /rtpx where the cost is based on the maximum search radius
     * rather than the actual landing spot distance.</p>
     */
    public int calculate(CommandConfig cfg, Location from, Location to, int distanceOverride) {
        if (cfg.costType() == CostType.FIXED) {
            return calculate(cfg, from, to);
        }

        // CostType.DISTANCE — use override distance instead of actual
        double raw = cfg.base() + cfg.perBlock() * distanceOverride;

        if (isCrossWorld(from, to)) {
            raw += cfg.crossWorldExtra();
        }

        int cost = (int) Math.round(raw);
        return Math.max(cfg.min(), Math.min(cfg.max(), cost));
    }

    private static boolean isCrossWorld(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null) {
            return false;
        }
        return !from.getWorld().getName().equals(to.getWorld().getName());
    }
}
