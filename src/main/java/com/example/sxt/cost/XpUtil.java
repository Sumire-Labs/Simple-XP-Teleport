package com.example.sxt.cost;

import org.bukkit.entity.Player;

/**
 * Utility methods for working with Minecraft experience (levels / total points).
 *
 * <p>Implements the original Minecraft XP formulas so that Paper-specific
 * quirks of {@code giveExp} / {@code setTotalExperience} are avoided.
 */
public final class XpUtil {

    private XpUtil() {
    }

    /**
     * Total experience points a player currently has (level + exp progress).
     */
    public static int getTotalExperience(Player p) {
        int level = p.getLevel();
        int total = totalXpForLevel(level);
        float expProgress = p.getExp();
        int expToLevel = p.getExpToLevel();
        total += (int) Math.floor(expProgress * expToLevel);
        return total;
    }

    /**
     * Set player's total experience from a points value.
     *
     * <p>Does <strong>not</strong> use {@code setLevel(0); setExp(0); giveExp(total)}.
     * Instead, the target level and exp progress are computed manually and applied via
     * {@code setLevel} / {@code setExp} to avoid Paper-specific behaviour differences.
     */
    public static void setTotalExperience(Player p, int total) {
        if (total < 0) {
            total = 0;
        }

        int level = levelForTotalXp(total);
        int levelBase = totalXpForLevel(level);
        int remainder = total - levelBase;
        int expToLevel = expNeededForLevel(level);

        float expProgress;
        if (expToLevel <= 0) {
            expProgress = 0.0f;
        } else {
            expProgress = (float) ((double) remainder / (double) expToLevel);
            // Correct any float-truncation that would lose points on the round-trip
            if (expProgress < 1.0f && (int) (expProgress * expToLevel) < remainder) {
                expProgress = Math.nextUp(expProgress);
            }
            // Clamp to [0, 1) as a safety net
            if (expProgress < 0.0f) expProgress = 0.0f;
            if (expProgress >= 1.0f) expProgress = 0.0f;
        }

        p.setLevel(level);
        p.setExp(expProgress);
    }

    /**
     * Subtract {@code points} total experience points.
     *
     * @return {@code true} on success, {@code false} if insufficient
     */
    public static boolean takePoints(Player p, int points) {
        int current = getTotalExperience(p);
        if (current < points) {
            return false;
        }
        setTotalExperience(p, current - points);
        return true;
    }

    /**
     * Subtract {@code levels} levels (preserving the exp progress within the new level).
     *
     * @return {@code true} on success, {@code false} if insufficient
     */
    public static boolean takeLevels(Player p, int levels) {
        int currentLevel = p.getLevel();
        if (currentLevel < levels) {
            return false;
        }
        p.setLevel(currentLevel - levels);
        return true;
    }

    // ─── XP formula helpers ──────────────────────────────────────

    /**
     * Total XP required to reach level {@code L} (exclusive of progress <em>within</em> level L).
     *
     * <p>Minecraft formulas:
     * <ul>
     *   <li>{@code L ≤ 16}: {@code L² + 6L}</li>
     *   <li>{@code 16 < L ≤ 31}: {@code 2.5L² − 40.5L + 360}</li>
     *   <li>{@code L > 31}: {@code 4.5L² − 162.5L + 2220}</li>
     * </ul>
     */
    static int totalXpForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int) Math.round(2.5 * level * level - 40.5 * level + 360.0);
        } else {
            return (int) Math.round(4.5 * level * level - 162.5 * level + 2220.0);
        }
    }

    /**
     * XP needed to go from level {@code L} to {@code L+1}.
     *
     * <p>Minecraft formulas:
     * <ul>
     *   <li>{@code L ≤ 15}: {@code 2L + 7}</li>
     *   <li>{@code 15 < L ≤ 30}: {@code 5L − 38}</li>
     *   <li>{@code L > 30}: {@code 9L − 158}</li>
     * </ul>
     */
    static int expNeededForLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        } else if (level <= 30) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }

    /**
     * Given total XP points, find the highest level {@code L} whose cumulative XP ≤ {@code total}.
     */
    static int levelForTotalXp(int total) {
        int level = 0;
        while (totalXpForLevel(level + 1) <= total) {
            level++;
        }
        return level;
    }
}
