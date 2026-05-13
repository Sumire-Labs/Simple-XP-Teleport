package com.example.sxt.cost;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * §6.1 U-5 & U-6: Verify XpUtil level/point calculations using the Minecraft XP formulas.
 */
class XpUtilTest {

    private Player player;
    private AtomicInteger level;
    private AtomicReference<Float> expProgress;

    @BeforeEach
    void setUp() {
        level = new AtomicInteger(0);
        expProgress = new AtomicReference<>(0.0f);

        player = mock(Player.class);

        doAnswer(inv -> {
            level.set(inv.getArgument(0));
            return null;
        }).when(player).setLevel(anyInt());

        doAnswer(inv -> {
            expProgress.set(inv.getArgument(0));
            return null;
        }).when(player).setExp(anyFloat());

        when(player.getLevel()).thenAnswer(inv -> level.get());
        when(player.getExp()).thenAnswer(inv -> expProgress.get());

        // getExpToLevel() must match the Minecraft formulas for the *current* level.
        // Marked lenient so individual tests (e.g. U-5) can override with a different value.
        lenient().when(player.getExpToLevel()).thenAnswer(inv -> {
            int lvl = level.get();
            if (lvl <= 15) {
                return 2 * lvl + 7;
            } else if (lvl <= 30) {
                return 5 * lvl - 38;
            } else {
                return 9 * lvl - 158;
            }
        });
    }

    // ── U-5: getTotalExperience ────────────────────────────────

    @Test
    void U5_getTotalExperience() {
        // §6.1 U-5: level=5, exp=0.5, expToLevel=10
        // Override the formula-based expToLevel with the value stated in the spec.
        lenient().when(player.getExpToLevel()).thenReturn(10);
        level.set(5);
        expProgress.set(0.5f);

        // total XP to reach level 5 = 5² + 6*5 = 55
        // floor(0.5 * 10) = 5
        // total = 55 + 5 = 60
        assertEquals(60, XpUtil.getTotalExperience(player));
    }

    // ── U-6: setTotalExperience → getTotalExperience round-trip ─

    @Test
    void U6_setTotalExperienceRoundTrip() {
        XpUtil.setTotalExperience(player, 50);

        int total = XpUtil.getTotalExperience(player);
        assertEquals(50, total);

        // Verify level was set to 4 (total XP for level 4 = 40 ≤ 50 < 55 = level 5)
        assertEquals(4, level.get());
        // remainder = 50 - 40 = 10, expToLevel(4) = 2*4+7 = 15
        // expProgress = 10 / 15 ≈ 0.6666667
        assertEquals(10.0f / 15.0f, expProgress.get(), 0.0001f);
    }

    // ── Additional verification ────────────────────────────────

    @Test
    void getTotalExperienceLevel0() {
        level.set(0);
        expProgress.set(0.0f);
        assertEquals(0, XpUtil.getTotalExperience(player));
    }

    @Test
    void getTotalExperienceLevel16() {
        // total for level 16 = 16² + 96 = 352
        level.set(16);
        expProgress.set(0.0f);
        assertEquals(352, XpUtil.getTotalExperience(player));
    }

    @Test
    void getTotalExperienceLevel31() {
        // total for level 31 = 2.5*961 - 40.5*31 + 360 = 1507
        level.set(31);
        expProgress.set(0.0f);
        assertEquals(1507, XpUtil.getTotalExperience(player));
    }

    @Test
    void getTotalExperienceWithProgress() {
        // Level 0, exp=0.0 → 0
        level.set(0);
        expProgress.set(0.0f);
        assertEquals(0, XpUtil.getTotalExperience(player));

        // Level 0, exp=1.0 → should never happen (1.0 means level up), but floor(1.0*7)=7
        level.set(0);
        expProgress.set(0.999f);
        // floor(0.999 * 7) = 6
        assertEquals(6, XpUtil.getTotalExperience(player));
    }

    // ── setTotalExperience round-trips at various values ───────

    @Test
    void setTotalExperienceZero() {
        XpUtil.setTotalExperience(player, 0);
        assertEquals(0, XpUtil.getTotalExperience(player));
        assertEquals(0, level.get());
        assertEquals(0.0f, expProgress.get(), 0.0001f);
    }

    @Test
    void setTotalExperienceExactlyLevelBoundary() {
        // 7 XP = exactly level 1
        XpUtil.setTotalExperience(player, 7);
        assertEquals(7, XpUtil.getTotalExperience(player));
        assertEquals(1, level.get());
        assertEquals(0.0f, expProgress.get(), 0.0001f);
    }

    @Test
    void setTotalExperienceLargeValue() {
        // 2000 XP (somewhere between level 31=1507 and level 39≈?)
        XpUtil.setTotalExperience(player, 2000);
        assertEquals(2000, XpUtil.getTotalExperience(player));
    }

    // ── takePoints ─────────────────────────────────────────────

    @Test
    void takePointsSufficient() {
        XpUtil.setTotalExperience(player, 100);
        boolean result = XpUtil.takePoints(player, 30);
        assertTrue(result);
        assertEquals(70, XpUtil.getTotalExperience(player));
    }

    @Test
    void takePointsInsufficient() {
        XpUtil.setTotalExperience(player, 100);
        boolean result = XpUtil.takePoints(player, 200);
        assertFalse(result);
        // Should not change anything
        assertEquals(100, XpUtil.getTotalExperience(player));
    }

    @Test
    void takePointsAll() {
        XpUtil.setTotalExperience(player, 100);
        boolean result = XpUtil.takePoints(player, 100);
        assertTrue(result);
        assertEquals(0, XpUtil.getTotalExperience(player));
    }

    // ── takeLevels ─────────────────────────────────────────────

    @Test
    void takeLevelsSufficient() {
        level.set(10);
        expProgress.set(0.5f);
        boolean result = XpUtil.takeLevels(player, 3);
        assertTrue(result);
        assertEquals(7, level.get());
        // exp progress preserved
        assertEquals(0.5f, expProgress.get(), 0.0001f);
    }

    @Test
    void takeLevelsInsufficient() {
        level.set(5);
        expProgress.set(0.2f);
        boolean result = XpUtil.takeLevels(player, 10);
        assertFalse(result);
        assertEquals(5, level.get());
        assertEquals(0.2f, expProgress.get(), 0.0001f);
    }

    @Test
    void takeLevelsAll() {
        level.set(3);
        expProgress.set(0.75f);
        boolean result = XpUtil.takeLevels(player, 3);
        assertTrue(result);
        assertEquals(0, level.get());
        assertEquals(0.75f, expProgress.get(), 0.0001f);
    }
}