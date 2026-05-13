package com.example.sxt.util;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.config.CommandConfig;
import com.example.sxt.config.CommandKey;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Writes structured audit events as JSON Lines to the audit log file.
 *
 * <p>Each successful teleport produces one JSON line in
 * {@code plugins/SimpleXpTeleport/logs/audit.log} (path configurable via
 * {@code audit-log.file} in {@code config.yml}).
 *
 * <p>File I/O is performed asynchronously via
 * {@code Bukkit.getScheduler().runTaskAsynchronously}. No external JSON
 * library is used — JSON is built by hand with proper escaping.</p>
 */
public final class AuditLogger {

    private final SimpleXpTeleportPlugin plugin;

    public AuditLogger(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    // ─────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────

    /**
     * Write a generic audit event as one JSON line.
     *
     * <p>The JSON object always includes {@code "ts"} (ISO-8601 timestamp)
     * and {@code "event"}. Every entry in {@code fields} is appended as an
     * additional top-level property.  Supported value types are
     * {@link String}, {@link Number} (Integer/Long/Double/Float),
     * {@link Boolean}, nested {@code Map<String,Object>}, and {@code null}.</p>
     *
     * <p>File I/O is performed asynchronously via
     * {@code Bukkit.getScheduler().runTaskAsynchronously}.  The parent
     * directory of the log file is created if necessary.</p>
     *
     * @param event  the event name (e.g. {@code "TELEPORT_SUCCESS"})
     * @param fields additional key-value pairs to include in the JSON object
     */
    public void log(String event, Map<String, Object> fields) {
        String jsonLine = buildGenericLogJson(event, fields);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Path logFile = resolveLogFile();
                Path parent = logFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(logFile,
                        Collections.singletonList(jsonLine),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to write audit log", e);
            }
        });
    }

    /**
     * Log a teleport success event as one JSON line.
     *
     * <p>Delegates to {@link #log(String, Map)} with event
     * {@code TELEPORT_SUCCESS} and the structured teleport fields.</p>
     *
     * @param player the player who teleported
     * @param from   the location the player left (must not be null)
     * @param to     the destination location (must not be null)
     * @param key    the command that triggered the teleport
     * @param cfg    the command configuration used
     * @param cost   the cost that was actually consumed
     */
    public void logTeleport(Player player, Location from, Location to,
                            CommandKey key, CommandConfig cfg, int cost) {
        if (from == null || to == null || player == null) {
            return;
        }

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double distance = Math.round(Math.sqrt(dx * dx + dz * dz) * 100.0) / 100.0;

        Map<String, Object> costMap = new LinkedHashMap<>();
        costMap.put("mode", cfg.costMode().name());
        costMap.put("amount", cost);

        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("command", key.name().toLowerCase());
        fields.put("player", player.getName());
        fields.put("uuid", player.getUniqueId().toString());
        fields.put("from", locationToMap(from));
        fields.put("to", locationToMap(to));
        fields.put("cost", costMap);
        fields.put("distance", distance);

        log("TELEPORT_SUCCESS", fields);
    }

    // ─────────────────────────────────────────────────────
    //  Generic JSON builder
    // ─────────────────────────────────────────────────────

    private String buildGenericLogJson(String event, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        appendField(sb, "ts", Instant.now().toString());
        sb.append(',');
        appendField(sb, "event", event);

        if (fields != null && !fields.isEmpty()) {
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                sb.append(',');
                appendJsonEntry(sb, entry.getKey(), entry.getValue());
            }
        }

        sb.append('}');
        return sb.toString();
    }

    private static void appendJsonEntry(StringBuilder sb, String key, Object value) {
        sb.append('"');
        escapeJson(sb, key);
        sb.append("\":");
        appendJsonValue(sb, value);
    }

    @SuppressWarnings("unchecked")
    private static void appendJsonValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"');
            escapeJson(sb, s);
            sb.append('"');
        } else if (value instanceof Number n) {
            sb.append(n);
        } else if (value instanceof Boolean b) {
            sb.append(b);
        } else if (value instanceof Map<?, ?> m) {
            sb.append('{');
            int count = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (count > 0) sb.append(',');
                if (e.getKey() instanceof String k) {
                    appendJsonEntry(sb, k, e.getValue());
                }
                count++;
            }
            sb.append('}');
        }
    }

    // ─────────────────────────────────────────────────────
    //  Location helper
    // ─────────────────────────────────────────────────────

    private static Map<String, Object> locationToMap(Location loc) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", loc.getWorld() != null ? loc.getWorld().getName() : "?");
        map.put("x", round1(loc.getX()));
        map.put("y", round1(loc.getY()));
        map.put("z", round1(loc.getZ()));
        return map;
    }

    // ─────────────────────────────────────────────────────
    //  String / escape helpers
    // ─────────────────────────────────────────────────────

    private static void appendField(StringBuilder sb, String key, String value) {
        sb.append('"');
        escapeJson(sb, key);
        sb.append("\":\"");
        escapeJson(sb, value);
        sb.append('"');
    }

    /**
     * Escape a string for inclusion as a JSON string value (without
     * surrounding quotes). Handles {@code "}, {@code \}, and control
     * characters.
     */
    private static void escapeJson(StringBuilder sb, String raw) {
        for (int i = 0, len = raw.length(); i < len; i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    // ── Path resolution ──────────────────────────────────────

    private Path resolveLogFile() {
        String configured = plugin.getPluginConfig().auditLogFile();
        // Safety: do not allow escaping the plugin data folder
        if (configured.contains("..")) {
            plugin.getLogger().warning("audit-log.file contains '..' — falling back to default");
            configured = "logs/audit.log";
        }
        return Paths.get(plugin.getDataFolder().getAbsolutePath(), configured);
    }
}
