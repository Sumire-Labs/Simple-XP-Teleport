package com.example.sxt.util;

import com.example.sxt.SimpleXpTeleportPlugin;

/**
 * Writes debug-level messages to the Bukkit logger when {@code debug} is
 * enabled in {@code config.yml}.
 *
 * <p>Messages are prefixed with {@code [DEBUG] } and emitted at
 * {@code INFO} level so they appear in the server console without
 * requiring a special log level.</p>
 */
public final class DebugLogger {

    private final SimpleXpTeleportPlugin plugin;

    public DebugLogger(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Log a debug message.  The message is only written when
     * {@link com.example.sxt.config.PluginConfig#isDebug()} returns
     * {@code true}.
     *
     * @param msg the debug message (plain text, no formatting required)
     */
    public void debug(String msg) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }
}
