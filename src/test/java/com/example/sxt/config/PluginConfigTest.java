package com.example.sxt.config;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.cost.CostMode;
import com.example.sxt.cost.CostType;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * §6.1 U-7: Verify config loading, especially {@code commands.homex.cost.amount=1}.
 */
class PluginConfigTest {

    private SimpleXpTeleportPlugin plugin;
    private Logger logger;

    @BeforeEach
    void setUp() {
        plugin = mock(SimpleXpTeleportPlugin.class);
        logger = Logger.getLogger("test");
        when(plugin.getLogger()).thenReturn(logger);
    }

    @Test
    void shouldLoadHomexCostAmount() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("commands.homex.cost.amount", 1);
        yaml.set("commands.homex.cost.mode", "LEVEL");
        yaml.set("commands.homex.cost.type", "FIXED");

        PluginConfig config = new PluginConfig(plugin, yaml);

        Map<CommandKey, CommandConfig> commands = config.commands();
        assertEquals(1, commands.size(), "Should contain exactly one command config");

        CommandConfig homexCfg = commands.get(CommandKey.HOMEX);
        assertNotNull(homexCfg, "homex config should be present");
        assertEquals(1, homexCfg.amount(), "homex cost amount should be 1");
        assertEquals(CostMode.LEVEL, homexCfg.costMode());
        assertEquals(CostType.FIXED, homexCfg.costType());
    }

    @Test
    void shouldLoadAllSevenCommandKeys() {
        YamlConfiguration yaml = new YamlConfiguration();
        // minimal config for each of the 7 command keys
        for (String key : List.of("homex", "warpx", "tpax", "tpahere", "rtpx", "tpposx", "backx")) {
            yaml.set("commands." + key + ".cost.mode", "LEVEL");
            yaml.set("commands." + key + ".cost.type", "FIXED");
            yaml.set("commands." + key + ".cost.amount", 0);
        }

        PluginConfig config = new PluginConfig(plugin, yaml);
        assertEquals(7, config.commands().size(), "Should contain all 7 command configs");
    }

    @Test
    void shouldSkipUnknownCommandKey() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("commands.unknowncmd.cost.amount", 5);
        yaml.set("commands.homex.cost.amount", 1);

        PluginConfig config = new PluginConfig(plugin, yaml);
        assertEquals(1, config.commands().size(), "Unknown key should be skipped");
        assertNotNull(config.commands().get(CommandKey.HOMEX));
    }

    @Test
    void shouldUseDefaultValuesForMissingFields() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("commands.homex.cost.amount", 1);

        PluginConfig config = new PluginConfig(plugin, yaml);
        CommandConfig cfg = config.commands().get(CommandKey.HOMEX);

        // Check defaults are applied for unspecified fields
        assertEquals(0, cfg.cooldownSeconds(), "Default cooldown should be 0");
        assertEquals(0, cfg.warmupSeconds(), "Default warmup should be 0");
        assertTrue(cfg.cancelOnMove(), "Default cancelOnMove should be true");
        assertTrue(cfg.cancelOnDamage(), "Default cancelOnDamage should be true");
        assertFalse(cfg.allowInCombat(), "Default allowInCombat should be false");
        assertEquals(SafetyCheck.NONE, cfg.safetyCheck(), "Default safetyCheck should be NONE");
        assertTrue(cfg.blacklistWorlds().isEmpty(), "Default blacklistWorlds should be empty");
    }

    @Test
    void shouldLoadTopLevelSettings() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("language", "en_US");
        yaml.set("debug", true);
        yaml.set("storage.sqlite.file", "mydata.db");

        PluginConfig config = new PluginConfig(plugin, yaml);
        assertEquals("en_US", config.language());
        assertTrue(config.isDebug());
        assertEquals("mydata.db", config.storageFile());
    }

    @Test
    void shouldFallBackToDefaultsForMissingTopLevelKeys() {
        YamlConfiguration yaml = new YamlConfiguration(); // empty config

        PluginConfig config = new PluginConfig(plugin, yaml);
        assertEquals("ja_JP", config.language(), "Default language should be ja_JP");
        assertFalse(config.isDebug(), "Default debug should be false");
        assertEquals("data.db", config.storageFile(), "Default storageFile should be data.db");
        assertEquals(3, config.defaultMaxHomes(), "Default max homes should be 3");
        assertEquals(15, config.combatTagPvpDuration(), "Default combat tag duration should be 15");
    }

    @Test
    void shouldLoadDistanceCommandConfig() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("commands.rtpx.cost.mode", "POINTS");
        yaml.set("commands.rtpx.cost.type", "DISTANCE");
        yaml.set("commands.rtpx.cost.base", 50.0);
        yaml.set("commands.rtpx.cost.per-block", 0.1);
        yaml.set("commands.rtpx.cost.min", 100);
        yaml.set("commands.rtpx.cost.max", 500);
        yaml.set("commands.rtpx.cost.cross-world-extra", 200);
        yaml.set("commands.rtpx.safety-check", "FIND_SAFE");
        yaml.set("commands.rtpx.min-radius", 500);
        yaml.set("commands.rtpx.max-radius", 5000);
        yaml.set("commands.rtpx.max-attempts", 16);

        PluginConfig config = new PluginConfig(plugin, yaml);
        CommandConfig cfg = config.commands().get(CommandKey.RTPX);

        assertEquals(CostMode.POINTS, cfg.costMode());
        assertEquals(CostType.DISTANCE, cfg.costType());
        assertEquals(50.0, cfg.base(), 0.001);
        assertEquals(0.1, cfg.perBlock(), 0.001);
        assertEquals(100, cfg.min());
        assertEquals(500, cfg.max());
        assertEquals(200, cfg.crossWorldExtra());
        assertEquals(SafetyCheck.FIND_SAFE, cfg.safetyCheck());
        assertEquals(500, cfg.minRadius());
        assertEquals(5000, cfg.maxRadius());
        assertEquals(16, cfg.maxAttempts());
    }
}
