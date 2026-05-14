package com.example.sxt.gui;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.data.dao.WarpDao;
import com.example.sxt.data.model.Warp;
import com.example.sxt.message.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Creates and opens warp GUI inventories (public and admin).
 *
 * <p>All DAO calls happen asynchronously; inventory creation and
 * opening are dispatched back to the main server thread via
 * {@code mainThread}.</p>
 */
public final class WarpGuiService {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final WarpDao warpDao;
    private final Executor mainThread;

    public WarpGuiService(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.warpDao = plugin.getWarpDao();
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    // ── Public GUI ────────────────────────────────────────────

    public void openPublicGui(Player player) {
        openPublicGui(player, 0);
    }

    public void openPublicGui(Player player, int page) {
        int targetPage = page;
        warpDao.listAll().thenAcceptAsync(warps -> {
            if (warps.isEmpty()) {
                msg.send(player, "warp.gui.empty", Map.of());
                return;
            }

            int totalPages = (int) Math.ceil((double) warps.size() / WarpGuiHolder.ITEMS_PER_PAGE);
            int p = clampPage(targetPage, totalPages);

            List<Warp> pageWarps = subList(warps, p, WarpGuiHolder.ITEMS_PER_PAGE);

            Component title = msg.format("warp.gui.title",
                    Map.of("page", String.valueOf(p + 1),
                            "total", String.valueOf(totalPages)));

            WarpGuiHolder holder = new WarpGuiHolder(p, totalPages, pageWarps);
            Inventory inv = Bukkit.createInventory(holder, WarpGuiHolder.TOTAL_SLOTS, title);
            holder.setInventory(inv);

            fillWarpItems(inv, pageWarps);
            fillPrevNext(inv, holder, "warp.gui.prev", "warp.gui.next");

            player.openInventory(inv);
        }, mainThread);
    }

    // ── Admin GUI ─────────────────────────────────────────────

    public void openAdminGui(Player player) {
        openAdminGui(player, 0);
    }

    public void openAdminGui(Player player, int page) {
        int targetPage = page;
        warpDao.listAll().thenAcceptAsync(warps -> {
            int totalPages = Math.max(1, (int) Math.ceil((double) warps.size() / WarpGuiHolder.ITEMS_PER_PAGE));
            int p = clampPage(targetPage, totalPages);

            List<Warp> pageWarps = subList(warps, p, WarpGuiHolder.ITEMS_PER_PAGE);

            Component title = msg.format("warp.gui.admin-title",
                    Map.of("page", String.valueOf(p + 1),
                            "total", String.valueOf(totalPages)));

            WarpAdminGuiHolder holder = new WarpAdminGuiHolder(p, totalPages, pageWarps);
            Inventory inv = Bukkit.createInventory(holder, WarpGuiHolder.TOTAL_SLOTS, title);
            holder.setInventory(inv);

            fillAdminWarpItems(inv, pageWarps);
            fillPrevNext(inv, holder, "warp.gui.prev", "warp.gui.next");
            fillAddButton(inv, holder);

            player.openInventory(inv);
        }, mainThread);
    }

    // ── Admin Action GUI ─────────────────────────────────────

    public void openAdminActionGui(Player player, String warpName, int returnPage) {
        WarpAdminActionGuiHolder holder = new WarpAdminActionGuiHolder(warpName, returnPage);
        Component title = msg.format("warp.gui.action-title",
                Map.of("warp", warpName));
        Inventory inv = Bukkit.createInventory(holder,
                WarpAdminActionGuiHolder.GUI_SIZE, title);
        holder.setInventory(inv);

        // Teleport
        ItemStack teleport = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tMeta = teleport.getItemMeta();
        tMeta.displayName(msg.format("warp.gui.action-teleport", Map.of()));
        teleport.setItemMeta(tMeta);
        inv.setItem(WarpAdminActionGuiHolder.TELEPORT_SLOT, teleport);

        // Overwrite
        ItemStack overwrite = new ItemStack(Material.COMPASS);
        ItemMeta oMeta = overwrite.getItemMeta();
        oMeta.displayName(msg.format("warp.gui.action-overwrite", Map.of()));
        overwrite.setItemMeta(oMeta);
        inv.setItem(WarpAdminActionGuiHolder.OVERWRITE_SLOT, overwrite);

        // Delete
        ItemStack delete = new ItemStack(Material.BARRIER);
        ItemMeta dMeta = delete.getItemMeta();
        dMeta.displayName(msg.format("warp.gui.action-delete", Map.of()));
        delete.setItemMeta(dMeta);
        inv.setItem(WarpAdminActionGuiHolder.DELETE_SLOT, delete);

        // Back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.displayName(msg.format("warp.gui.action-back", Map.of()));
        back.setItemMeta(bMeta);
        inv.setItem(WarpAdminActionGuiHolder.BACK_SLOT, back);

        player.openInventory(inv);
    }

    // ── Item population helpers ───────────────────────────────

    private void fillWarpItems(Inventory inv, List<Warp> warps) {
        for (int i = 0; i < warps.size(); i++) {
            Warp warp = warps.get(i);
            ItemStack item = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(msg.format("warp.gui.warp-name",
                    Map.of("warp", warp.name())));

            meta.lore(List.of(msg.format("warp.gui.warp-lore",
                    Map.of("world", warp.world(),
                            "x", String.valueOf((int) warp.x()),
                            "y", String.valueOf((int) warp.y()),
                            "z", String.valueOf((int) warp.z())))));

            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
    }

    private void fillAdminWarpItems(Inventory inv, List<Warp> warps) {
        for (int i = 0; i < warps.size(); i++) {
            Warp warp = warps.get(i);
            ItemStack item = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(msg.format("warp.gui.admin-warp-name",
                    Map.of("warp", warp.name())));

            meta.lore(List.of(msg.format("warp.gui.admin-warp-lore",
                    Map.of("world", warp.world(),
                            "x", String.valueOf((int) warp.x()),
                            "y", String.valueOf((int) warp.y()),
                            "z", String.valueOf((int) warp.z())))));

            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
    }

    private void fillPrevNext(Inventory inv, WarpGuiHolder holder,
                              String prevKey, String nextKey) {
        if (holder.hasPrev()) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(msg.format(prevKey,
                    Map.of("page", String.valueOf(holder.page()))));
            prev.setItemMeta(meta);
            inv.setItem(WarpGuiHolder.PREV_SLOT, prev);
        }

        if (holder.hasNext()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(msg.format(nextKey,
                    Map.of("page", String.valueOf(holder.page() + 2))));
            next.setItemMeta(meta);
            inv.setItem(WarpGuiHolder.NEXT_SLOT, next);
        }
    }

    private void fillAddButton(Inventory inv, WarpAdminGuiHolder holder) {
        ItemStack add = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = add.getItemMeta();
        meta.displayName(msg.format("warp.gui.add-button", Map.of()));
        add.setItemMeta(meta);
        inv.setItem(WarpAdminGuiHolder.ADD_SLOT, add);
    }

    // ── Utility ───────────────────────────────────────────────

    private static int clampPage(int page, int totalPages) {
        if (page < 0) return 0;
        if (page >= totalPages) return totalPages - 1;
        return page;
    }

    private static List<Warp> subList(List<Warp> all, int page, int perPage) {
        int from = page * perPage;
        if (from >= all.size()) {
            return List.of();
        }
        int to = Math.min(from + perPage, all.size());
        return all.subList(from, to);
    }
}
