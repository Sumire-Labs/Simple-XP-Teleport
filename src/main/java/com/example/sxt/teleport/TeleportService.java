package com.example.sxt.teleport;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import com.example.sxt.config.CommandKey;
import com.example.sxt.config.SafetyCheck;
import com.example.sxt.cost.CostCalculator;
import com.example.sxt.cost.CostMode;
import com.example.sxt.cost.XpUtil;
import com.example.sxt.data.model.BackLocation;
import com.example.sxt.data.model.BackLocation.BackReason;
import com.example.sxt.hook.WorldGuardHook;
import com.example.sxt.message.MessageService;
import com.example.sxt.util.AuditLogger;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Orchestrates the full teleport pipeline (§5 Step 6).
 *
 * <p>Callers on the main server thread invoke
 * {@link #requestTeleport(Player, Location, CommandKey)} which runs checks
 * 1–8 synchronously. On success:
 * <ul>
 *   <li>If a warmup is configured the method starts a {@link WarmupTask}
 *       and returns {@link TeleportResult.Scheduled}.</li>
 *   <li>Otherwise steps 9–11 execute immediately and
 *       {@link TeleportResult.Immediate} is returned.</li>
 * </ul></p>
 *
 * <p>For TPA commands where the cost payer differs from the teleporting
 * player, use the package-private
 * {@link #requestTeleportWithPayer(Player, Player, Location, CommandKey)}.</p>
 */
public final class TeleportService {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService messageService;
    private final CooldownManager cooldownManager;
    private final CombatTagManager combatTagManager;
    private final SafetyChecker safetyChecker;
    private final CostCalculator costCalculator;
    private final AuditLogger auditLogger;

    /** Active warmup tasks, keyed by mover UUID. */
    private final Map<UUID, WarmupTask> activeWarmups = new ConcurrentHashMap<>();

    public TeleportService(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
        this.cooldownManager = plugin.getCooldownManager();
        this.combatTagManager = plugin.getCombatTagManager();
        this.safetyChecker = plugin.getSafetyChecker();
        this.costCalculator = new CostCalculator(plugin);
        this.auditLogger = new AuditLogger(plugin);
    }

    // ── Public API (§4.1) ────────────────────────────────────

    /**
     * Initiate a teleport with cost/cooldown/warmup/safety pipeline.
     * Must be called from the main server thread.
     *
     * @param player      the player to teleport (also pays cost and starts cooldown)
     * @param destination where the player should land
     * @param key         which command is triggering the teleport
     * @return the result (scheduled, denied, or immediate)
     */
    public TeleportResult requestTeleport(Player player,
                                          Location destination,
                                          CommandKey key) {
        return requestTeleportWithPayer(player, player, destination, key);
    }

    /**
     * Like {@link #requestTeleport} but with a separate cost/cooldown payer.
     * Used for TPAHERE where the target moves but the requester pays.
     *
     * <p>This is a low-level internal method; prefer {@link #requestTeleport}
     * when mover and payer are the same player.</p>
     *
     * @param mover       the player who will be teleported
     * @param payer       the player from whom cost/cooldown is deducted
     * @param destination where the mover should land
     * @param key         which command is triggering the teleport
     * @return the result (scheduled, denied, or immediate)
     */
    public TeleportResult requestTeleportWithPayer(Player mover,
                                            Player payer,
                                            Location destination,
                                            CommandKey key) {
        CommandConfig cfg = plugin.getPluginConfig().commandConfig(key);
        if (cfg == null) {
            messageService.send(mover, "general.no-permission", Map.of());
            return new TeleportResult.Denied(DenyReason.NO_PERMISSION, 0);
        }

        UUID moverUuid = mover.getUniqueId();
        String worldName = mover.getWorld().getName();
        String destWorldName = destination.getWorld() != null
                ? destination.getWorld().getName() : worldName;

        // ── 1. Permission check (mover must have use permission) ──
        String permNode = "sxt.use." + key.name().toLowerCase();
        if (!mover.hasPermission(permNode)) {
            messageService.send(mover, "general.no-permission", Map.of());
            return new TeleportResult.Denied(DenyReason.NO_PERMISSION, 0);
        }

        // ── 2. World blacklist (global + per-command) ──
        if (isWorldBlacklisted(worldName, destWorldName, cfg)) {
            messageService.send(mover, "world.blacklisted", Map.of());
            return new TeleportResult.Denied(DenyReason.WORLD_BLACKLISTED, 0);
        }

        // ── 3. WorldGuard ──
        WorldGuardHook wgHook = plugin.getWorldGuardHook();
        if (wgHook != null && wgHook.isDenied(destination)) {
            messageService.send(mover, "world.worldguard-denied", Map.of());
            return new TeleportResult.Denied(DenyReason.WORLDGUARD_DENIED, 0);
        }

        // ── 4. Combat check (mover) ──
        if (!cfg.allowInCombat() && combatTagManager.isInCombat(mover)
                && !hasBypass(mover, "combat", key)) {
            long remaining = combatTagManager.remainingSeconds(mover);
            messageService.send(mover, "combat.blocked",
                    Map.of("cooldown", String.valueOf(remaining)));
            return new TeleportResult.Denied(DenyReason.IN_COMBAT, remaining);
        }

        // ── 5. Cooldown (payer) ──
        long remainingCd = cooldownManager.remainingSeconds(payer, key,
                cfg.cooldownSeconds());
        if (remainingCd > 0) {
            messageService.send(payer, "cooldown.active",
                    Map.of("cooldown", String.valueOf(remainingCd)));
            return new TeleportResult.Denied(DenyReason.ON_COOLDOWN, remainingCd);
        }

        // ── 6. Safety check (may modify destination) ──
        Location dest = destination.clone();
        switch (cfg.safetyCheck()) {
            case CANCEL:
                if (!safetyChecker.isSafe(dest)) {
                    messageService.send(mover, "safety.unsafe-cancelled", Map.of());
                    return new TeleportResult.Denied(DenyReason.UNSAFE_DESTINATION, 0);
                }
                break;
            case FIND_SAFE:
                Optional<Location> safeOpt = safetyChecker.findSafe(dest,
                        cfg.safeSearchRadius());
                if (safeOpt.isEmpty()) {
                    messageService.send(mover, "safety.no-safe-location", Map.of());
                    return new TeleportResult.Denied(DenyReason.NO_SAFE_LOCATION, 0);
                }
                dest = safeOpt.get();
                break;
            case NONE:
            default:
                break;
        }

        // ── 7. Cost check (payer) ──
        Location from = mover.getLocation();
        int cost = costCalculator.calculate(cfg, from, dest);
        if (hasBypass(payer, "cost", key)) {
            cost = 0;
        }
        if (cost > 0) {
            if (cfg.costMode() == CostMode.LEVEL) {
                if (payer.getLevel() < cost) {
                    messageService.send(payer, "cost.not-enough-level",
                            Map.of("cost", String.valueOf(cost)));
                    return new TeleportResult.Denied(DenyReason.NOT_ENOUGH_XP, 0);
                }
            } else {
                if (XpUtil.getTotalExperience(payer) < cost) {
                    messageService.send(payer, "cost.not-enough-points",
                            Map.of("cost", String.valueOf(cost)));
                    return new TeleportResult.Denied(DenyReason.NOT_ENOUGH_XP, 0);
                }
            }
        }

        // ── 8. Warmup (mover) ──
        if (cfg.warmupSeconds() > 0 && !hasBypass(mover, "warmup", key)) {
            WarmupTask task = new WarmupTask(plugin, this, mover, payer, dest,
                    key, cfg, cost);
            activeWarmups.put(moverUuid, task);
            task.start();
            return new TeleportResult.Scheduled(cfg.warmupSeconds() * 20);
        }

        // No warmup — execute pipeline steps 9–11 immediately
        executeTeleport(mover, payer, dest, key, cfg, cost, from);
        return new TeleportResult.Immediate();
    }

    // ── Package-private helpers (called by WarmupTask / listeners) ──

    /**
     * Execute the final stage of the pipeline: consume cost from {@code payer},
     * save back location for {@code mover}, and perform the async teleport
     * of {@code mover}. Called on the main thread.
     *
     * @param mover         the player being teleported
     * @param payer         the player from whom cost/cooldown is deducted
     * @param fromLocation  the mover location at the time the teleport was
     *                      requested (used for back-location when warmup=0)
     */
    void executeTeleport(Player mover, Player payer, Location dest,
                         CommandKey key, CommandConfig cfg, int cost,
                         Location fromLocation) {
        UUID moverUuid = mover.getUniqueId();

        // 9 ─ Consume cost from payer
        if (cost > 0) {
            boolean ok;
            if (cfg.costMode() == CostMode.LEVEL) {
                ok = XpUtil.takeLevels(payer, cost);
            } else {
                ok = XpUtil.takePoints(payer, cost);
            }
            if (!ok) {
                // Should not happen (checked in step 7), but guard anyway
                String msgKey = cfg.costMode() == CostMode.LEVEL
                        ? "cost.not-enough-level" : "cost.not-enough-points";
                messageService.send(payer, msgKey,
                        Map.of("cost", String.valueOf(cost)));
                activeWarmups.remove(moverUuid);
                return;
            }
        }

        // 10 ─ Save back location for mover (skip BackLocationDao call for /backx)
        if (key != CommandKey.BACKX) {
            Location backLoc = (fromLocation != null)
                    ? fromLocation : mover.getLocation();
            BackLocation back = new BackLocation(
                    moverUuid,
                    backLoc.getWorld().getName(),
                    backLoc.getX(), backLoc.getY(), backLoc.getZ(),
                    backLoc.getYaw(), backLoc.getPitch(),
                    System.currentTimeMillis(),
                    BackReason.TELEPORT
            );
            // Non-blocking async DAO call — do NOT join/get on main thread
            plugin.getBackLocationDao().upsert(back).exceptionally(ex -> {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to save back location", ex);
                return null;
            });
        }

        // 11 ─ Async teleport of mover
        mover.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.COMMAND)
                .thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        // Schedule post-teleport actions on the main thread
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            // Start cooldown on payer
                            cooldownManager.setLastUsed(payer, key,
                                    System.currentTimeMillis());

                            // Send teleporting message to mover
                            String msgKey = commandMessagePrefix(key) + ".teleporting";
                            messageService.send(mover, msgKey,
                                    teleportingPlaceholders(dest));

                            // Audit log (async I/O inside AuditLogger)
                            if (plugin.getPluginConfig().isAuditLogEnabled()) {
                                try {
                                    auditLogger.logTeleport(mover,
                                            fromLocation, dest, key, cfg, cost);
                                } catch (Exception ex) {
                                    plugin.getLogger().log(Level.WARNING,
                                            "Failed to write audit log", ex);
                                }
                            }

                            // Effects
                            playSuccessEffects(mover, dest);
                        });
                    }
                });

        // Clean up warmup tracking
        activeWarmups.remove(moverUuid);
    }

    /**
     * Called from {@code EntityDamageListener} to cancel an active warmup
     * when the player takes damage and {@code cancel-on-damage} is enabled.
     */
    public void onPlayerDamaged(Player player) {
        UUID uuid = player.getUniqueId();
        WarmupTask task = activeWarmups.get(uuid);
        if (task != null && task.cancelOnDamage()) {
            task.cancel("warmup.cancelled-damage");
        }
    }

    /** Remove warmup from internal map (called by WarmupTask.cancel). */
    void removeWarmupInternal(UUID uuid) {
        activeWarmups.remove(uuid);
    }

    // ── Helpers ──────────────────────────────────────────────

    private boolean isWorldBlacklisted(String fromWorld, String toWorld,
                                       CommandConfig cfg) {
        var global = plugin.getPluginConfig().globalBlacklistWorlds();
        var cmd = cfg.blacklistWorlds();
        return global.contains(fromWorld) || global.contains(toWorld)
                || cmd.contains(fromWorld) || cmd.contains(toWorld);
    }

    private boolean hasBypass(Player player, String bypassType, CommandKey key) {
        if (player.hasPermission("sxt.bypass." + bypassType + ".*")) {
            return true;
        }
        return player.hasPermission("sxt.bypass." + bypassType + "."
                + key.name().toLowerCase());
    }

    private static String commandMessagePrefix(CommandKey key) {
        return switch (key) {
            case HOMEX -> "home";
            case WARPX -> "warp";
            case TPAX, TPAHERE -> "tpa";
            case RTPX -> "rtp";
            case TPPOSX -> "tppos";
            case BACKX -> "back";
        };
    }

    private static Map<String, String> teleportingPlaceholders(Location dest) {
        return Map.of(
                "world", dest.getWorld() != null ? dest.getWorld().getName() : "?",
                "x", String.valueOf((int) dest.getX()),
                "y", String.valueOf((int) dest.getY()),
                "z", String.valueOf((int) dest.getZ())
        );
    }

    private void playSuccessEffects(Player player, Location dest) {
        if (!plugin.getPluginConfig().isEffectsEnabled()) {
            return;
        }
        try {
            NamespacedKey particleKey = NamespacedKey.minecraft(
                    plugin.getPluginConfig().particle().toLowerCase());
            Particle particle = Registry.PARTICLE_TYPE.get(particleKey);
            if (particle != null) {
                player.getWorld().spawnParticle(particle, dest, 30,
                        0.5, 0.5, 0.5, 0.1);
            }
        } catch (Exception ignored) {
            // Invalid or missing particle — skip
        }
        try {
            NamespacedKey soundKey = NamespacedKey.minecraft(
                    plugin.getPluginConfig().successSound().toLowerCase());
            Sound sound = Registry.SOUNDS.get(soundKey);
            if (sound != null) {
                player.getWorld().playSound(dest, sound, 1.0f, 1.0f);
            }
        } catch (Exception ignored) {
            // Invalid or missing sound — skip
        }
    }
}
