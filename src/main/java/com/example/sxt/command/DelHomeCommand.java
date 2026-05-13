package com.example.sxt.command;

import com.example.sxt.SimpleXpTeleportPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public final class DelHomeCommand implements CommandExecutor {
    public DelHomeCommand(SimpleXpTeleportPlugin plugin) {
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return true;
    }
}
