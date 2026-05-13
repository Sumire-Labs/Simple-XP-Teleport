package com.example.sxt.message;

import com.example.sxt.SimpleXpTeleportPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Formats and sends MiniMessage strings with runtime placeholders.
 *
 * <p>Usage:
 * <pre>{@code
 *   messageService.send(sender, "home.set", Map.of("home", "mybase"));
 * }</pre></p>
 *
 * <p>The {@code <prefix>} placeholder is always resolved automatically
 * from the language file (unless the message key itself is {@code "prefix"}).
 * Caller-supplied placeholders are inserted as plain text (MiniMessage-injection safe).</p>
 */
public final class MessageService {

    private final SimpleXpTeleportPlugin plugin;
    private final Logger logger;
    private final MiniMessage miniMessage;
    private final LangLoader langLoader;

    public MessageService(SimpleXpTeleportPlugin plugin, LangLoader langLoader) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.miniMessage = MiniMessage.miniMessage();
        this.langLoader = langLoader;
    }

    // ── public API ───────────────────────────────────────────

    /**
     * Format a message and send it to the given sender.
     *
     * @param sender       recipient
     * @param key          dotted language key (e.g. {@code "home.set"})
     * @param placeholders optional runtime replacements ({@code {"home" → "mybase"}})
     */
    public void send(CommandSender sender, String key, Map<String, String> placeholders) {
        Component component = format(key, placeholders);
        sender.sendMessage(component);
    }

    /**
     * Format a message into an Adventure {@link Component} (usable for signs,
     * books, boss bars, etc.).
     *
     * @param key          dotted language key
     * @param placeholders optional runtime replacements
     * @return the parsed component, never null
     */
    public Component format(String key, Map<String, String> placeholders) {
        String template = langLoader.get(key).orElseGet(() -> {
            logger.warning("Missing message key: " + key);
            return "<red>Missing message: " + key + "</red>";
        });

        TagResolver.Builder builder = TagResolver.builder();

        // ── prefix placeholder (resolved from lang, always available) ──
        Optional<String> prefixOpt = langLoader.get("prefix");
        if (prefixOpt.isPresent() && !"prefix".equals(key)) {
            // parsed: the prefix value itself contains MiniMessage markup
            builder.resolver(Placeholder.parsed("prefix", prefixOpt.get()));
        }

        // ── caller placeholders (unparsed → injection-safe) ──
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                builder.resolver(Placeholder.unparsed(entry.getKey(), entry.getValue()));
            }
        }

        return miniMessage.deserialize(template, builder.build());
    }

    // ── accessor ─────────────────────────────────────────────

    /** Expose the underlying loader for reload orchestration. */
    public LangLoader langLoader() {
        return langLoader;
    }
}
