package com.example.sxt.config;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.cost.CostMode;
import com.example.sxt.cost.CostType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads and holds all settings from {@code config.yml}.
 * Missing or invalid values fall back to documented defaults (see §3.4)
 * with a warning written to the plugin logger.
 */
public final class PluginConfig {

    private final SimpleXpTeleportPlugin plugin;
    private final Logger logger;

    private String language;
    private boolean debug;
    private String storageFile;
    private int defaultMaxHomes;
    private int combatTagPvpDuration;
    private List<String> globalBlacklistWorlds;
    private boolean effectsEnabled;
    private String particle;
    private String warmupSound;
    private String startSound;
    private String successSound;
    private String cancelSound;
    private boolean auditLogEnabled;
    private String auditLogFile;
    private boolean backRecordNonPluginTeleports;
    private int waypointsMaxPerPlayer;
    private boolean waypointsShareEnabled;
    private int waypointsShareExpireSeconds;
    private boolean waypointsShareChargeOnAccept;
    private Map<CommandKey, CommandConfig> commands;

    public PluginConfig(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        parseConfig(plugin.getConfig());
    }

    /** Package-private constructor for unit testing – reads directly from a ConfigurationSection. */
    PluginConfig(SimpleXpTeleportPlugin plugin, ConfigurationSection config) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        parseConfig(config);
    }

    // ── Getters ─────────────────────────────────────────────

    public String language()                       { return language; }
    public boolean isDebug()                       { return debug; }
    public String storageFile()                    { return storageFile; }
    public int defaultMaxHomes()                   { return defaultMaxHomes; }
    public int combatTagPvpDuration()              { return combatTagPvpDuration; }
    public List<String> globalBlacklistWorlds()    { return globalBlacklistWorlds; }
    public boolean isEffectsEnabled()              { return effectsEnabled; }
    public String particle()                       { return particle; }
    public String warmupSound()                    { return warmupSound; }
    public String startSound()                     { return startSound; }
    public String successSound()                   { return successSound; }
    public String cancelSound()                    { return cancelSound; }
    public boolean isAuditLogEnabled()             { return auditLogEnabled; }
    public String auditLogFile()                   { return auditLogFile; }
    public boolean isBackRecordNonPluginTeleports() { return backRecordNonPluginTeleports; }

    /**
     * Maximum number of waypoints a single player can create.
     * Default: 10
     */
    public int waypointsMaxPerPlayer() { return waypointsMaxPerPlayer; }

    /**
     * Whether waypoint sharing between players is enabled.
     * Default: true
     */
    public boolean isWaypointsShareEnabled() { return waypointsShareEnabled; }

    /**
     * Expiry time in seconds for waypoint share requests.
     * Default: 60
     */
    public int waypointsShareExpireSeconds() { return waypointsShareExpireSeconds; }

    /**
     * If true, the player who accepts a shared waypoint also pays the XP cost
     * for teleporting to it. If false, only the requester pays.
     * Default: false
     */
    public boolean isWaypointsShareChargeOnAccept() { return waypointsShareChargeOnAccept; }

    public Map<CommandKey, CommandConfig> commands() { return Collections.unmodifiableMap(commands); }

    // ── Convenience ─────────────────────────────────────────

    public void toggleDebug() {
        this.debug = !this.debug;
    }

    public CommandConfig commandConfig(CommandKey key) {
        return commands.get(key);
    }

    /** Reload from the on-disk config.yml. */
    public void reload() {
        plugin.reloadConfig();
        parseConfig(plugin.getConfig());
    }

    // ── Parsing ─────────────────────────────────────────────

    private void parseConfig(ConfigurationSection config) {
        language         = config.getString("language", "ja_JP");
        debug            = config.getBoolean("debug", false);
        storageFile      = config.getString("storage.sqlite.file", "data.db");
        defaultMaxHomes  = config.getInt("home.default-max-count", 3);
        combatTagPvpDuration = config.getInt("combat-tag.pvp-duration", 15);
        globalBlacklistWorlds = config.getStringList("worlds.global-blacklist");

        // effects
        effectsEnabled    = config.getBoolean("effects.enabled", true);
        particle          = config.getString("effects.particle", "ENCHANTMENT_TABLE");
        warmupSound       = config.getString("effects.warmup-sound", "BLOCK_PORTAL_AMBIENT");
        startSound        = config.getString("effects.start-sound", "BLOCK_BEACON_ACTIVATE");
        successSound      = config.getString("effects.success-sound", "ENTITY_ENDERMAN_TELEPORT");
        cancelSound       = config.getString("effects.cancel-sound", "ENTITY_VILLAGER_NO");

        // audit-log
        auditLogEnabled   = config.getBoolean("audit-log.enabled", true);
        auditLogFile      = config.getString("audit-log.file", "logs/audit.log");

        // back
        backRecordNonPluginTeleports = config.getBoolean("back.record-non-plugin-teleports", false);

        // waypoints
        waypointsMaxPerPlayer       = config.getInt("waypoints.max-per-player", 10);
        waypointsShareEnabled       = config.getBoolean("waypoints.share.enabled", true);
        waypointsShareExpireSeconds = config.getInt("waypoints.share.expire-seconds", 60);
        waypointsShareChargeOnAccept= config.getBoolean("waypoints.share.charge-on-accept", false);

        // commands
        commands = new EnumMap<>(CommandKey.class);
        ConfigurationSection commandsSection = config.getConfigurationSection("commands");
        if (commandsSection != null) {
            Set<String> cmdNames = commandsSection.getKeys(false);
            for (String name : cmdNames) {
                CommandKey key = keyFromName(name);
                if (key == null) {
                    logger.warning("Unknown command key in config: '" + name + "' — skipping.");
                    continue;
                }
                ConfigurationSection cmdSec = commandsSection.getConfigurationSection(name);
                if (cmdSec == null) {
                    logger.warning("Command '" + name + "' section is not a map — skipping.");
                    continue;
                }
                commands.put(key, parseCommandConfig(cmdSec, name));
            }
        }
    }

    private CommandConfig parseCommandConfig(ConfigurationSection sec, String cmdName) {
        String prefix = "commands." + cmdName + ".";

        CommandConfig.Builder b = CommandConfig.builder();

        // ── cost sub-section ──
        ConfigurationSection costSec = sec.getConfigurationSection("cost");
        if (costSec != null) {
            b.costMode(parseEnum(CostMode.class, costSec.getString("mode", "LEVEL"), prefix + "cost.mode"));
            b.costType(parseEnum(CostType.class, costSec.getString("type", "FIXED"), prefix + "cost.type"));
            b.amount(costSec.getInt("amount", 0));
            b.crossWorldExtra(costSec.getInt("cross-world-extra", 0));

            // DISTANCE 用（FIXED のときはデフォルトのまま）
            if (costSec.contains("base"))           b.base(costSec.getDouble("base", 0.0));
            if (costSec.contains("per-block"))      b.perBlock(costSec.getDouble("per-block", 0.0));
            if (costSec.contains("min"))            b.min(costSec.getInt("min", 0));
            if (costSec.contains("max"))            b.max(costSec.getInt("max", Integer.MAX_VALUE));
        }

        // ── top-level command settings ──
        if (sec.contains("cooldown"))               b.cooldownSeconds(sec.getInt("cooldown", 0));
        if (sec.contains("warmup"))                 b.warmupSeconds(sec.getInt("warmup", 0));
        if (sec.contains("cancel-on-move"))         b.cancelOnMove(sec.getBoolean("cancel-on-move", true));
        if (sec.contains("cancel-on-damage"))       b.cancelOnDamage(sec.getBoolean("cancel-on-damage", true));
        if (sec.contains("allow-in-combat"))        b.allowInCombat(sec.getBoolean("allow-in-combat", false));
        if (sec.contains("safety-check")) {
            b.safetyCheck(parseEnum(SafetyCheck.class, sec.getString("safety-check", "NONE"), prefix + "safety-check"));
        }
        if (sec.contains("blacklist-worlds"))       b.blacklistWorlds(sec.getStringList("blacklist-worlds"));

        // rtpx 用
        if (sec.contains("safe-search-radius"))     b.safeSearchRadius(sec.getInt("safe-search-radius", 16));
        if (sec.contains("min-radius"))             b.minRadius(sec.getInt("min-radius", 500));
        if (sec.contains("max-radius"))             b.maxRadius(sec.getInt("max-radius", 5000));
        if (sec.contains("max-attempts"))           b.maxAttempts(sec.getInt("max-attempts", 16));

        // tpa 用
        if (sec.contains("request-timeout"))        b.requestTimeoutSeconds(sec.getInt("request-timeout", 60));

        return b.build();
    }

    // ── Helpers ─────────────────────────────────────────────

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String raw, String path) {
        if (raw == null || raw.isEmpty()) {
            // should not happen when getString provides a default, but guard anyway
            return defaultValue(enumClass);
        }
        try {
            return Enum.valueOf(enumClass, raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid value for " + path + ": '" + raw + "'. Using default.");
            return defaultValue(enumClass);
        }
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> E defaultValue(Class<E> enumClass) {
        if (enumClass == CostMode.class)   return (E) CostMode.LEVEL;
        if (enumClass == CostType.class)   return (E) CostType.FIXED;
        if (enumClass == SafetyCheck.class) return (E) SafetyCheck.NONE;
        throw new IllegalArgumentException("Unknown enum class: " + enumClass);
    }

    private static CommandKey keyFromName(String name) {
        return switch (name.toLowerCase()) {
            case "homex"   -> CommandKey.HOMEX;
            case "warpx"   -> CommandKey.WARPX;
            case "tpax"    -> CommandKey.TPAX;
            case "tpahere" -> CommandKey.TPAHERE;
            case "rtpx"    -> CommandKey.RTPX;
            case "tpposx"  -> CommandKey.TPPOSX;
            case "wayx"    -> CommandKey.WAYX;
            case "backx"   -> CommandKey.BACKX;
            default        -> null;
        };
    }
}
