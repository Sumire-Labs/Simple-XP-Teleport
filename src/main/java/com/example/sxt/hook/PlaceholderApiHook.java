package com.example.sxt.hook;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import com.example.sxt.config.CommandKey;
import com.example.sxt.permission.HomeLimitResolver;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PlaceholderAPI expansion providing {@code %sxt_*} placeholders (§4.5).
 *
 * <p>To avoid blocking the main thread with SQLite I/O inside the
 * synchronous {@link #onRequest(OfflinePlayer, String)} callback,
 * home counts are stored in an async-populated cache.  When a cache
 * miss occurs the placeholder returns {@code "0"} and an async
 * query is dispatched; the result will be visible on a subsequent
 * call.</p>
 */
public final class PlaceholderApiHook extends PlaceholderExpansion {

    private final SimpleXpTeleportPlugin plugin;
    private final HomeLimitResolver homeLimitResolver;

    /** Async-populated cache: player UUID → home count. */
    private final Map<UUID, Integer> homeCountCache = new ConcurrentHashMap<>();

    public PlaceholderApiHook(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.homeLimitResolver = new HomeLimitResolver(plugin);
    }

    // ── Expansion metadata ───────────────────────────────────

    @Override
    public @NotNull String getIdentifier() {
        return "sxt";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty()
                ? "SimpleXpTeleport"
                : String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // survive PlaceholderAPI reloads
    }

    // ── Placeholder resolution (§4.5) ───────────────────────

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }

        // %sxt_home_count%
        if (params.equals("home_count")) {
            return resolveHomeCount(offlinePlayer.getUniqueId());
        }

        // %sxt_home_max%
        if (params.equals("home_max")) {
            return resolveHomeMax(offlinePlayer);
        }

        // %sxt_cooldown_<command>%
        if (params.startsWith("cooldown_")) {
            String cmdName = params.substring("cooldown_".length());
            return resolveCooldown(offlinePlayer, cmdName);
        }

        // %sxt_in_combat%
        if (params.equals("in_combat")) {
            return resolveInCombat(offlinePlayer);
        }

        // Unknown key → empty string (§4.5)
        return "";
    }

    // ── Resolvers ────────────────────────────────────────────

    private String resolveHomeCount(UUID uuid) {
        Integer cached = homeCountCache.get(uuid);
        if (cached != null) {
            return String.valueOf(cached);
        }
        // Trigger async load; return 0 for now (non-blocking)
        plugin.getHomeDao().countByPlayer(uuid).thenAccept(count ->
                homeCountCache.put(uuid, count));
        return "0";
    }

    private String resolveHomeMax(OfflinePlayer offlinePlayer) {
        if (offlinePlayer instanceof Player player) {
            int max = homeLimitResolver.resolve(player);
            return max == Integer.MAX_VALUE ? "\u221E" : String.valueOf(max); // ∞
        }
        return String.valueOf(plugin.getPluginConfig().defaultMaxHomes());
    }

    private String resolveCooldown(OfflinePlayer offlinePlayer, String cmdName) {
        CommandKey key = commandKeyFromName(cmdName);
        if (key == null) {
            return "";
        }
        if (!(offlinePlayer instanceof Player player)) {
            return "0";
        }
        CommandConfig cfg = plugin.getPluginConfig().commandConfig(key);
        if (cfg == null) {
            return "0";
        }
        long remaining = plugin.getCooldownManager()
                .remainingSeconds(player, key, cfg.cooldownSeconds());
        return String.valueOf(remaining);
    }

    private String resolveInCombat(OfflinePlayer offlinePlayer) {
        if (!(offlinePlayer instanceof Player player)) {
            return "false";
        }
        return plugin.getCombatTagManager().isInCombat(player) ? "true" : "false";
    }

    // ── Helpers ──────────────────────────────────────────────

    private static CommandKey commandKeyFromName(String name) {
        return switch (name.toLowerCase()) {
            case "homex"   -> CommandKey.HOMEX;
            case "warpx"   -> CommandKey.WARPX;
            case "tpax"    -> CommandKey.TPAX;
            case "tpahere" -> CommandKey.TPAHERE;
            case "rtpx"    -> CommandKey.RTPX;
            case "tpposx"  -> CommandKey.TPPOSX;
            case "backx"   -> CommandKey.BACKX;
            default        -> null;
        };
    }

    /**
     * Removes a player from the home-count cache so the next placeholder
     * resolution triggers an async reload.  Call this after a home is
     * saved or deleted.
     */
    public void invalidateHomeCount(UUID uuid) {
        homeCountCache.remove(uuid);
    }
}
