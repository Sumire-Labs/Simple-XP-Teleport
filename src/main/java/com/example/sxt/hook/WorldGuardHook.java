package com.example.sxt.hook;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.logging.Level;

/**
 * Lightweight WorldGuard integration (§4.6).
 *
 * <h3>Flag</h3>
 * Registers a custom {@link StateFlag} named {@code sxt-teleport}
 * with a default of {@code ALLOW}.  When a region sets the flag to
 * {@code DENY} the destination is rejected with
 * {@link com.example.sxt.teleport.DenyReason#WORLDGUARD_DENIED}.
 *
 * <h3>Lifecycle</h3>
 * Flag registration must happen during the {@code onLoad} phase
 * (WorldGuard 7.0.11+ requirement).  The caller must verify that
 * WorldGuard is present <em>before</em> invoking
 * {@link #tryRegisterFlag()}, otherwise the JVM may attempt to
 * load this class and fail with {@link NoClassDefFoundError}.
 *
 * <h3>No-ClassDef safety</h3>
 * This class references WorldGuard and WorldEdit types directly.
 * To avoid {@link NoClassDefFoundError} the caller must never
 * reference this class when WorldGuard is absent — check
 * {@code Bukkit.getPluginManager().getPlugin("WorldGuard") != null}
 * first.  {@link #tryRegisterFlag()} additionally guards itself with
 * {@code Class.forName} as a secondary safety net.</p>
 */
public final class WorldGuardHook {

    private static final String FLAG_NAME = "sxt-teleport";

    /** The registered flag instance, or {@code null} when unavailable. */
    private static StateFlag SXT_TELEPORT_FLAG;

    private final SimpleXpTeleportPlugin plugin;

    public WorldGuardHook(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Flag registration (onLoad) ──────────────────────────

    /**
     * Attempt to register the {@code sxt-teleport} {@link StateFlag}.
     * Must be called during the {@code onLoad} phase.
     *
     * <p>Safe to invoke unconditionally — the method returns
     * silently when WorldGuard is not on the classpath or the flag
     * cannot be registered.</p>
     */
    public static void tryRegisterFlag() {
        // Guard: WorldGuard classes must be loadable
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
        } catch (ClassNotFoundException ignored) {
            return; // WorldGuard not available — nothing to do
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }

        try {
            StateFlag flag = new StateFlag(FLAG_NAME, true); // default ALLOW
            WorldGuard.getInstance().getFlagRegistry().register(flag);
            SXT_TELEPORT_FLAG = flag;
        } catch (Exception e) {
            // FlagConflictException or other — silently skip.
            // isDenied() will report false (safe default).
        }
    }

    // ── Region check ────────────────────────────────────────

    /**
     * Returns {@code true} when the given location falls inside a
     * WorldGuard region that sets {@code sxt-teleport=DENY}.
     *
     * <p>When the flag has not been registered or WorldGuard is
     * unavailable this method always returns {@code false}.</p>
     */
    public boolean isDenied(Location location) {
        StateFlag flag = SXT_TELEPORT_FLAG;
        if (flag == null || location == null || location.getWorld() == null) {
            return false;
        }

        try {
            RegionQuery query = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .createQuery();
            StateFlag.State state = query.queryState(
                    BukkitAdapter.adapt(location),
                    null,
                    flag
            );
            return state == StateFlag.State.DENY;
        } catch (Exception e) {
            // If anything goes wrong (e.g., WorldGuard API mismatch),
            // default to allowing the teleport.
            plugin.getLogger().log(Level.WARNING,
                    "WorldGuard region check failed, allowing teleport", e);
            return false;
        }
    }

    public SimpleXpTeleportPlugin plugin() {
        return plugin;
    }
}
