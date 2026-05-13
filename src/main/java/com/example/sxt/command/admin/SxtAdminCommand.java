package com.example.sxt.command.admin;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

/**
 * /sxtadmin reload | debug | home &lt;player&gt; list | home &lt;player&gt; delete &lt;name&gt; | home &lt;player&gt; tp &lt;name&gt;
 *
 * <p>Currently implements {@code reload} and {@code debug}. The {@code home} subcommands
 * are stubs until Step 7.</p>
 */
public final class SxtAdminCommand implements CommandExecutor {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;

    public SxtAdminCommand(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        if (args.length == 0) {
            return false; // show usage
        }

        String sub = args[0].toLowerCase();

        return switch (sub) {
            case "reload" -> handleReload(sender);
            case "debug"  -> handleDebug(sender);
            // Step 7+ will add: case "home" -> handleHome(sender, args);
            default       -> false;
        };
    }

    private boolean handleReload(CommandSender sender) {
        plugin.getPluginConfig().reload();
        plugin.getLangLoader().reload(plugin.getPluginConfig().language());
        msg.send(sender, "general.reload-success", null);
        return true;
    }

    private boolean handleDebug(CommandSender sender) {
        plugin.getPluginConfig().toggleDebug();
        String key = plugin.getPluginConfig().isDebug() ? "general.debug-on" : "general.debug-off";
        msg.send(sender, key, null);
        return true;
    }
}
