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
import com.example.sxt.command.admin.SxtAdminCommand;
import com.example.sxt.hook.PlaceholderApiHook;
import com.example.sxt.hook.WorldGuardHook;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

public final class SimpleXpTeleportPlugin extends JavaPlugin {
    private PlaceholderApiHook placeholderApiHook;
    private WorldGuardHook worldGuardHook;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("Simple XP Teleport skeleton is loading.");

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

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderApiHook = new PlaceholderApiHook(this);
            getLogger().info("PlaceholderAPI detected.");
        }

        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            worldGuardHook = new WorldGuardHook(this);
            getLogger().info("WorldGuard detected.");
        }

        getLogger().info("Simple XP Teleport skeleton enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Simple XP Teleport skeleton disabled.");
    }

    private void registerCommand(String name, CommandExecutor executor) {
        var command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command not found in plugin.yml: " + name);
            return;
        }
        command.setExecutor(executor);
    }
}
