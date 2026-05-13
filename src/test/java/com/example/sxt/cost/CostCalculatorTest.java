package com.example.sxt.cost;

import com.example.sxt.config.CommandConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * §6.1 U-1 through U-4: Verify CostCalculator against the formulas in §4.2.
 */
class CostCalculatorTest {

    private CostCalculator calculator;
    private World worldOverworld;
    private World worldNether;

    @BeforeEach
    void setUp() {
        calculator = new CostCalculator(null);

        worldOverworld = mock(World.class);
        when(worldOverworld.getName()).thenReturn("world");

        worldNether = mock(World.class);
        when(worldNether.getName()).thenReturn("world_nether");
    }

    // ── U-1: FIXED, same world ─────────────────────────────────

    @Test
    void U1_fixedSameWorld() {
        CommandConfig cfg = fixedConfig(1, 2);

        Location from = loc(worldOverworld, 0, 0, 0);
        Location to = loc(worldOverworld, 100, 64, 200);

        assertEquals(1, calculator.calculate(cfg, from, to));
    }

    // ── U-2: FIXED, cross world ────────────────────────────────

    @Test
    void U2_fixedCrossWorld() {
        CommandConfig cfg = fixedConfig(1, 2);

        Location from = loc(worldOverworld, 0, 0, 0);
        Location to = loc(worldNether, 100, 64, 200);

        assertEquals(3, calculator.calculate(cfg, from, to)); // 1 + 2
    }

    // ── U-3: DISTANCE, same world ──────────────────────────────

    @Test
    void U3_distanceSameWorld() {
        CommandConfig cfg = distanceConfig(10, 0.05, 20, 300, 200);

        Location from = loc(worldOverworld, 0, 0, 0);
        Location to = loc(worldOverworld, 300, 0, 400);

        // distance = sqrt(300² + 400²) = 500
        // raw = 10 + 0.05 * 500 = 35
        // clamp(35, 20, 300) = 35
        assertEquals(35, calculator.calculate(cfg, from, to));
    }

    // ── U-4: DISTANCE, cross world ─────────────────────────────

    @Test
    void U4_distanceCrossWorld() {
        CommandConfig cfg = distanceConfig(10, 0.05, 20, 300, 200);

        Location from = loc(worldOverworld, 0, 0, 0);
        Location to = loc(worldNether, 300, 0, 400);

        // distance = 500, raw = 10 + 25 = 35
        // crossWorldExtra = 200 → raw = 235
        // clamp(235, 20, 300) = 235
        assertEquals(235, calculator.calculate(cfg, from, to));
    }

    // ── Edge cases ─────────────────────────────────────────────

    @Test
    void distanceBelowMinimumClampsToMin() {
        CommandConfig cfg = distanceConfig(0, 0.0, 20, 300, 0);

        Location from = loc(worldOverworld, 0, 0, 0);
        Location to = loc(worldOverworld, 10, 0, 0);

        // distance = 10, raw = 0 + 0*10 = 0, clamp(0,20,300) = 20
        assertEquals(20, calculator.calculate(cfg, from, to));
    }

    @Test
    void distanceAboveMaximumClampsToMax() {
        CommandConfig cfg = distanceConfig(0, 1.0, 0, 50, 0);

        Location from = loc(worldOverworld, 0, 0, 0);
        Location to = loc(worldOverworld, 100, 0, 0);

        // distance = 100, raw = 100, clamp(100,0,50) = 50
        assertEquals(50, calculator.calculate(cfg, from, to));
    }

    @Test
    void distanceRoundsRaw() {
        CommandConfig cfg = distanceConfig(0, 1.0, 0, Integer.MAX_VALUE, 0);

        Location from = loc(worldOverworld, 0, 0, 0);
        Location to = loc(worldOverworld, 100, 0, 0);

        // distance = 100, raw = 100
        assertEquals(100, calculator.calculate(cfg, from, to));
    }

    @Test
    void distanceYIgnored() {
        CommandConfig cfg = distanceConfig(0, 0.05, 0, Integer.MAX_VALUE, 0);

        Location from = loc(worldOverworld, 0, 0, 0);
        // Same XZ, different Y → distance = 0
        Location to = loc(worldOverworld, 0, 200, 0);

        assertEquals(0, calculator.calculate(cfg, from, to));
    }

    // ── Helpers ────────────────────────────────────────────────

    private static CommandConfig fixedConfig(int amount, int crossWorldExtra) {
        CommandConfig cfg = mock(CommandConfig.class);
        when(cfg.costType()).thenReturn(CostType.FIXED);
        when(cfg.amount()).thenReturn(amount);
        when(cfg.crossWorldExtra()).thenReturn(crossWorldExtra);
        return cfg;
    }

    private static CommandConfig distanceConfig(double base, double perBlock,
                                                 int min, int max, int crossWorldExtra) {
        CommandConfig cfg = mock(CommandConfig.class);
        when(cfg.costType()).thenReturn(CostType.DISTANCE);
        when(cfg.base()).thenReturn(base);
        when(cfg.perBlock()).thenReturn(perBlock);
        when(cfg.min()).thenReturn(min);
        when(cfg.max()).thenReturn(max);
        when(cfg.crossWorldExtra()).thenReturn(crossWorldExtra);
        return cfg;
    }

    private static Location loc(World world, double x, double y, double z) {
        Location loc = mock(Location.class);
        when(loc.getWorld()).thenReturn(world);
        when(loc.getX()).thenReturn(x);
        when(loc.getY()).thenReturn(y);
        when(loc.getZ()).thenReturn(z);
        return loc;
    }
}