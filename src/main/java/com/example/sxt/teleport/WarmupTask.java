package com.example.sxt.teleport;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import com.example.sxt.config.CommandKey;
import com.example.sxt.message.MessageService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;

/**
 * Runs a per-tick warmup countdown.
 *
 * <p>Every tick the player's horizontal distance from the start location
 * is checked. If {@link CommandConfig#cancelOnMove()} is {@code true} and
 * {@code Math.hypot(dx, dz) &gt; 0.1}, the warmup is cancelled with
 * {@code warmup.cancelled-move}.</p>
 *
 * <p>An external {@code EntityDamageEvent} listener may also call
 * {@link #cancel(String)} when {@link CommandConfig#cancelOnDamage()} is
 * enabled.</p>
 *
 * <p>When the countdown reaches zero, {@link TeleportService#executeTeleport}
 * is called to complete the pipeline (steps 9–11).</p>
 */
public final class WarmupTask implements Runnable {

    private final SimpleXpTeleportPlugin plugin;
    private final TeleportService teleportService;
    private final MessageService messageService;
    private final Player player;       // the mover
    private final Player payer;        // who pays cost/cooldown (may be same as player)
    private final UUID playerUuid;
    private final Location destination;
    private final CommandKey key;
    private final CommandConfig cfg;
    private final int cost;
    private final Location startLocation;
    private final int totalTicks;
    private final Map<String, String> extraPlaceholders;
    private int remainingTicks;
    private boolean cancelled;
    private BukkitTask bukkitTask;

    public WarmupTask(SimpleXpTeleportPlugin plugin,
                      TeleportService teleportService,
                      Player player,
                      Location destination,
                      CommandKey key,
                      CommandConfig cfg,
                      int cost) {
        this(plugin, teleportService, player, player, destination, key, cfg, cost, Map.of());
    }

    public WarmupTask(SimpleXpTeleportPlugin plugin,
                      TeleportService teleportService,
                      Player player,
                      Player payer,
                      Location destination,
                      CommandKey key,
                      CommandConfig cfg,
                      int cost) {
        this(plugin, teleportService, player, payer, destination, key, cfg, cost, Map.of());
    }

    public WarmupTask(SimpleXpTeleportPlugin plugin,
                      TeleportService teleportService,
                      Player player,
                      Player payer,
                      Location destination,
                      CommandKey key,
                      CommandConfig cfg,
                      int cost,
                      Map<String, String> extraPlaceholders) {
        this.plugin = plugin;
        this.teleportService = teleportService;
        this.messageService = plugin.getMessageService();
        this.player = player;
        this.payer = payer;
        this.playerUuid = player.getUniqueId();
        this.destination = destination.clone();
        this.key = key;
        this.cfg = cfg;
        this.cost = cost;
        this.extraPlaceholders = extraPlaceholders;
        this.startLocation = player.getLocation().clone();
        this.totalTicks = cfg.warmupSeconds() * 20;
        this.remainingTicks = totalTicks;
    }

    /** Start the repeating scheduler task. Called on the main server thread. */
    public void start() {
        messageService.send(player, "warmup.start",
                Map.of("warmup", String.valueOf(cfg.warmupSeconds())));
        bukkitTask = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this, 0L, 1L);
    }

    /**
     * Cancel this warmup from outside (e.g. damage listener).
     * Sends the given message key to the player.
     */
    public void cancel(String messageKey) {
        if (cancelled) {
            return;
        }
        cancelled = true;
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }
        messageService.send(player, messageKey, Map.of());
        teleportService.removeWarmupInternal(playerUuid);
    }

    // ── Per-tick logic ───────────────────────────────────────

    @Override
    public void run() {
        if (cancelled || !player.isOnline()) {
            cancelInternal();
            return;
        }

        // Movement detection
        if (cfg.cancelOnMove()) {
            Location current = player.getLocation();
            double dx = current.getX() - startLocation.getX();
            double dz = current.getZ() - startLocation.getZ();
            if (Math.hypot(dx, dz) > 0.1) {
                cancel("warmup.cancelled-move");
                return;
            }
        }

        remainingTicks--;
        if (remainingTicks <= 0) {
            // Warmup complete — execute teleport
            cancelInternal(); // clean up task registration
            teleportService.executeTeleport(player, payer, destination, key,
                    cfg, cost, startLocation, extraPlaceholders);
        }
    }

    // ── Internal helpers ─────────────────────────────────────

    private void cancelInternal() {
        cancelled = true;
        if (bukkitTask != null) {
            bukkitTask.cancel();
        }
    }

    /** For listener use: whether {@link CommandConfig#cancelOnDamage()} is enabled. */
    public boolean cancelOnDamage() {
        return cfg.cancelOnDamage();
    }
}
