package com.example.sxt;

import com.example.sxt.command.BackCommand;
import com.example.sxt.command.DelHomeCommand;
import com.example.sxt.command.DelWarpCommand;
import com.example.sxt.command.HomeCommand;
import com.example.sxt.command.RtpCommand;
import com.example.sxt.command.SetHomeCommand;
import com.example.sxt.command.SetWarpCommand;
import com.example.sxt.command.TpPosCommand;
import com.example.sxt.command.TpaAcceptCommand;
import com.example.sxt.command.TpaCommand;
import com.example.sxt.command.TpaDenyCommand;
import com.example.sxt.command.TpaHereCommand;
import com.example.sxt.command.WarpCommand;
import com.example.sxt.command.WayxCommand;
import com.example.sxt.command.admin.SxtAdminCommand;
import com.example.sxt.config.PluginConfig;
import com.example.sxt.data.DatabaseManager;
import com.example.sxt.data.dao.BackLocationDao;
import com.example.sxt.data.dao.HomeDao;
import com.example.sxt.data.dao.WarpDao;
import com.example.sxt.data.dao.WaypointDao;
import com.example.sxt.hook.PlaceholderApiHook;
import com.example.sxt.hook.WorldGuardHook;
import com.example.sxt.gui.WarpGuiListener;
import com.example.sxt.gui.WarpGuiService;
import com.example.sxt.gui.WaypointGuiListener;
import com.example.sxt.gui.WaypointGuiService;
import com.example.sxt.listener.EntityDamageListener;
import com.example.sxt.listener.PlayerDeathListener;
import com.example.sxt.listener.PlayerMoveListener;
import com.example.sxt.listener.PlayerTeleportListener;
import com.example.sxt.message.LangLoader;
import com.example.sxt.message.MessageService;
import com.example.sxt.teleport.CombatTagManager;
import com.example.sxt.teleport.CooldownManager;
import com.example.sxt.teleport.RandomLocationFinder;
import com.example.sxt.teleport.SafetyChecker;
import com.example.sxt.teleport.TeleportRequest;
import com.example.sxt.teleport.TeleportService;
import com.example.sxt.teleport.WaypointShareRequest;
import com.example.sxt.util.DebugLogger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleXpTeleportPlugin extends JavaPlugin {
    private PluginConfig pluginConfig;
    private LangLoader langLoader;
    private MessageService messageService;
    private DatabaseManager databaseManager;
    private HomeDao homeDao;
    private WarpDao warpDao;
    private WaypointDao waypointDao;
    private BackLocationDao backLocationDao;
    private PlaceholderApiHook placeholderApiHook;
    private WorldGuardHook worldGuardHook;
    private CooldownManager cooldownManager;
    private CombatTagManager combatTagManager;
    private SafetyChecker safetyChecker;
    private RandomLocationFinder randomLocationFinder;
    private TeleportService teleportService;
    private TeleportRequest teleportRequest;
    private WaypointShareRequest waypointShareRequest;
    private WarpGuiService warpGuiService;
    private WaypointGuiService waypointGuiService;
    private DebugLogger debugLogger;

    @Override
    public void onLoad() {
        // WorldGuard flag registration must happen during onLoad (§4.6).
        // Guard with a pure-Bukkit check so we never trigger class-loading
        // of WorldGuardHook (or its WorldGuard/WorldEdit imports) when the
        // soft-dependency is absent.
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            WorldGuardHook.tryRegisterFlag();
        }
    }

    @Override
    public void onEnable() {
        pluginConfig = new PluginConfig(this);
        getLogger().info("Config loaded: " + pluginConfig.commands().size() + " command configs");

        langLoader = new LangLoader(this);
        langLoader.load(pluginConfig.language());
        messageService = new MessageService(this, langLoader);
        getLogger().info("Language loaded: " + pluginConfig.language());

        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.connect();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        homeDao = new HomeDao(databaseManager);
        warpDao = new WarpDao(databaseManager);
        waypointDao = new WaypointDao(databaseManager);
        backLocationDao = new BackLocationDao(databaseManager);

        cooldownManager = new CooldownManager(this);
        combatTagManager = new CombatTagManager(this);
        safetyChecker = new SafetyChecker(this);
        randomLocationFinder = new RandomLocationFinder(this, safetyChecker);
        teleportService = new TeleportService(this);
        teleportRequest = new TeleportRequest(this);
        teleportRequest.startCleanupTask();

        waypointShareRequest = new WaypointShareRequest(this);
        waypointShareRequest.startCleanupTask();

        warpGuiService = new WarpGuiService(this);
        waypointGuiService = new WaypointGuiService(this);
        debugLogger = new DebugLogger(this);

        // Register listeners
        getServer().getPluginManager().registerEvents(combatTagManager, this);
        getServer().getPluginManager().registerEvents(
                new PlayerMoveListener(this, teleportService), this);
        getServer().getPluginManager().registerEvents(
                new EntityDamageListener(this, teleportService), this);
        getServer().getPluginManager().registerEvents(
                new PlayerDeathListener(this), this);
        getServer().getPluginManager().registerEvents(
                new PlayerTeleportListener(this), this);
        getServer().getPluginManager().registerEvents(
                new WarpGuiListener(this, warpGuiService), this);
        getServer().getPluginManager().registerEvents(
                new WaypointGuiListener(this, waypointGuiService), this);

        getLogger().info("Simple XP Teleport skeleton is loading.");

        // Register commands with TeleportService injected
        registerCommand("homex", new HomeCommand(this));
        registerCommand("sethomex", new SetHomeCommand(this));
        registerCommand("delhomex", new DelHomeCommand(this));
        registerCommand("warpx", new WarpCommand(this));
        registerCommand("setwarpx", new SetWarpCommand(this));
        registerCommand("delwarpx", new DelWarpCommand(this));
        registerCommand("tpax", new TpaCommand(this));
        registerCommand("tpahere", new TpaHereCommand(this));
        registerCommand("tpacceptx", new TpaAcceptCommand(this));
        registerCommand("tpdenyx", new TpaDenyCommand(this));
        registerCommand("rtpx", new RtpCommand(this));
        registerCommand("tpposx", new TpPosCommand(this));
        registerCommand("backx", new BackCommand(this));
        registerCommand("sxtadmin", new SxtAdminCommand(this));
        registerCommand("wayx", new WayxCommand(this));

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderApiHook = new PlaceholderApiHook(this);
            placeholderApiHook.register();
            getLogger().info("PlaceholderAPI detected.");
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardHook = new WorldGuardHook(this);
            getLogger().info("WorldGuard detected.");
        }

        getLogger().info("Simple XP Teleport enabled.");
    }

    // ── Getters ─────────────────────────────────────────────

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public LangLoader getLangLoader() {
        return langLoader;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public HomeDao getHomeDao() {
        return homeDao;
    }

    public WarpDao getWarpDao() {
        return warpDao;
    }

    public WaypointDao getWaypointDao() {
        return waypointDao;
    }

    public BackLocationDao getBackLocationDao() {
        return backLocationDao;
    }

    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public CombatTagManager getCombatTagManager() {
        return combatTagManager;
    }

    public SafetyChecker getSafetyChecker() {
        return safetyChecker;
    }

    public RandomLocationFinder getRandomLocationFinder() {
        return randomLocationFinder;
    }

    public TeleportService getTeleportService() {
        return teleportService;
    }

    public TeleportRequest getTeleportRequest() {
        return teleportRequest;
    }

    public WaypointShareRequest getWaypointShareRequest() {
        return waypointShareRequest;
    }

    public PlaceholderApiHook getPlaceholderApiHook() {
        return placeholderApiHook;
    }

    public WorldGuardHook getWorldGuardHook() {
        return worldGuardHook;
    }

    public WarpGuiService getWarpGuiService() {
        return warpGuiService;
    }

    public WaypointGuiService getWaypointGuiService() {
        return waypointGuiService;
    }

    public DebugLogger getDebugLogger() {
        return debugLogger;
    }

    @Override
    public void onDisable() {
        getLogger().info("Simple XP Teleport disabled.");
    }

    private void registerCommand(String name, CommandExecutor executor) {
        var command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command not found in plugin.yml: " + name);
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter tabCompleter) {
            command.setTabCompleter(tabCompleter);
        }
    }
}
