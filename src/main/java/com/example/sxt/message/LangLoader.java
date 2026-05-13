package com.example.sxt.message;

import com.example.sxt.SimpleXpTeleportPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Loads language files ({@code ja_JP.yml}, {@code en_US.yml}) from the
 * plugin data folder, falling back to jar extraction via {@code saveResource}.
 *
 * <p>All message keys are flattened into a single {@code Map<String, String>}
 * (e.g. {@code "home.set"} → the MiniMessage template string).</p>
 */
public final class LangLoader {

    private final SimpleXpTeleportPlugin plugin;
    private final Logger logger;
    private Map<String, String> messages = Map.of();

    public LangLoader(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    /**
     * Load (or reload) messages for the given language code.
     */
    public void load(String language) {
        Path langFolder = plugin.getDataFolder().toPath().resolve("lang");
        Path langFile = langFolder.resolve(language + ".yml");

        try {
            if (!Files.exists(langFile)) {
                Files.createDirectories(langFolder);
            }
            // Extract from jar (no-op if already exists)
            plugin.saveResource("lang/" + language + ".yml", false);
        } catch (IllegalArgumentException e) {
            logger.warning("Language resource not found in jar: lang/" + language + ".yml");
        } catch (Exception e) {
            logger.warning("Failed to extract language file: lang/" + language + ".yml — " + e.getMessage());
        }

        if (Files.exists(langFile)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(langFile.toFile());
            messages = flatten(yaml, "");
            logger.info("Loaded language: " + language + " (" + messages.size() + " keys)");
        } else {
            logger.warning("Language file not found: " + language + ".yml — messages will be empty");
            messages = Map.of();
        }
    }

    /** Return the raw MiniMessage template for {@code key}, or {@link Optional#empty()} if missing. */
    public Optional<String> get(String key) {
        return Optional.ofNullable(messages.get(key));
    }

    /**
     * Reload messages for the given language code.
     * Respects the data-folder-first priority: if an external file exists
     * it is loaded as-is; otherwise the jar-bundled version is extracted.
     */
    public void reload(String language) {
        load(language);
    }

    // ── helpers ──────────────────────────────────────────────

    /**
     * Recursively flatten a {@link ConfigurationSection} into a dotted-key map.
     * Only leaf string values are kept.
     */
    private Map<String, String> flatten(ConfigurationSection section, String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection sub = section.getConfigurationSection(key);
                if (sub != null) {
                    result.putAll(flatten(sub, fullKey));
                }
            } else {
                result.put(fullKey, section.getString(key, ""));
            }
        }
        return result;
    }
}
