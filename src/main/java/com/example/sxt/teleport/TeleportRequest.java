package com.example.sxt.teleport;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import com.example.sxt.config.CommandKey;
import com.example.sxt.message.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds pending TPA requests.
 * Each target can have at most one outstanding request (latest wins).
 *
 * <p>A repeating cleanup task automatically expires requests that
 * exceed their configured {@code request-timeout}.</p>
 */
public final class TeleportRequest {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService messageService;

    /** Pending TPA requests, keyed by target UUID. */
    private final Map<UUID, Pending> pendingRequests = new ConcurrentHashMap<>();

    public TeleportRequest(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.messageService = plugin.getMessageService();
    }

    // ── Pending accessors ────────────────────────────────────

    public void putPending(UUID targetUuid, Pending pending) {
        pendingRequests.put(targetUuid, pending);
    }

    /** Remove and return the pending request for {@code targetUuid}, if any. */
    public Optional<Pending> removePending(UUID targetUuid) {
        return Optional.ofNullable(pendingRequests.remove(targetUuid));
    }

    /** Return the pending request for {@code targetUuid}, if any. */
    public Optional<Pending> getPending(UUID targetUuid) {
        return Optional.ofNullable(pendingRequests.get(targetUuid));
    }

    // ── Timeout cleanup ──────────────────────────────────────

    /**
     * Start a repeating task (every 20 ticks) that expires timed-out requests.
     * Must be called from the main server thread during {@code onEnable}.
     */
    public void startCleanupTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupExpired,
                20L, 20L);
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Pending>> it = pendingRequests.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Pending> entry = it.next();
            Pending pending = entry.getValue();
            CommandKey key = pending.type() == Pending.Type.TPA
                    ? CommandKey.TPAX : CommandKey.TPAHERE;
            CommandConfig cfg = plugin.getPluginConfig().commandConfig(key);
            if (cfg == null) {
                continue;
            }
            long timeoutMs = cfg.requestTimeoutSeconds() * 1000L;
            if (now - pending.createdAtMs() > timeoutMs) {
                it.remove();
                sendExpired(pending);
            }
        }
    }

    private void sendExpired(Pending pending) {
        Player requester = Bukkit.getPlayer(pending.requesterUuid());
        Player target = Bukkit.getPlayer(pending.targetUuid());
        if (requester != null && requester.isOnline()) {
            messageService.send(requester, "tpa.expired", Map.of());
        }
        if (target != null && target.isOnline()) {
            messageService.send(target, "tpa.expired", Map.of());
        }
    }

    // ── TPA pending record ───────────────────────────────────

    /** Represents an outstanding TPA / TPAHERE request. */
    public record Pending(UUID requesterUuid, UUID targetUuid,
                          Type type, long createdAtMs) {
        public enum Type { TPA, TPAHERE }
    }
}
