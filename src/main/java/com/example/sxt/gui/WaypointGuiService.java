package com.example.sxt.gui;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.data.dao.WaypointDao;
import com.example.sxt.data.model.Waypoint;
import com.example.sxt.message.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Creates and opens waypoint GUI inventories (public, manage, action, share-target).
 *
 * <p>All DAO calls execute asynchronously; inventory creation and opening
 * are dispatched back to the main server thread via {@code mainThread}.</p>
 */
public final class WaypointGuiService {

    private final SimpleXpTeleportPlugin plugin;
    private final MessageService msg;
    private final WaypointDao waypointDao;
    private final Executor mainThread;

    public WaypointGuiService(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageService();
        this.waypointDao = plugin.getWaypointDao();
        this.mainThread = task -> plugin.getServer().getScheduler().runTask(plugin, task);
    }

    // ── Public GUI ────────────────────────────────────────────

    /** Open the public waypoint GUI (own waypoints, with add & manage buttons). */
    public void openPublicGui(Player player) {
        openPublicGui(player, 0);
    }

    /** Open the public waypoint GUI at the given page. */
    public void openPublicGui(Player player, int page) {
        int targetPage = page;
        waypointDao.listByOwner(player.getUniqueId()).thenAcceptAsync(waypoints -> {
            if (waypoints.isEmpty()) {
                msg.send(player, "waypoint.gui.empty", Map.of());
                return;
            }

            int totalPages = Math.max(1, (int) Math.ceil(
                    (double) waypoints.size() / WaypointGuiHolder.ITEMS_PER_PAGE));
            int p = clampPage(targetPage, totalPages);

            List<Waypoint> pageWaypoints = subList(waypoints, p, WaypointGuiHolder.ITEMS_PER_PAGE);

            Component title = msg.format("waypoint.gui.title",
                    Map.of("page", String.valueOf(p + 1),
                            "total", String.valueOf(totalPages)));

            WaypointGuiHolder holder = new WaypointGuiHolder(p, totalPages, pageWaypoints);
            Inventory inv = Bukkit.createInventory(holder, WaypointGuiHolder.TOTAL_SLOTS, title);
            holder.setInventory(inv);

            fillWaypointItems(inv, pageWaypoints);
            fillPrevNext(inv, holder, "waypoint.gui.prev", "waypoint.gui.next");
            fillAddButton(inv, WaypointGuiHolder.ADD_SLOT);
            fillManageButton(inv);

            player.openInventory(inv);
        }, mainThread);
    }

    // ── Manage GUI ────────────────────────────────────────────

    /** Open the waypoint management GUI at page 0. */
    public void openManageGui(Player player) {
        openManageGui(player, 0);
    }

    /** Open the waypoint management GUI at the given page. */
    public void openManageGui(Player player, int page) {
        int targetPage = page;
        waypointDao.listByOwner(player.getUniqueId()).thenAcceptAsync(waypoints -> {
            if (waypoints.isEmpty()) {
                msg.send(player, "waypoint.gui.empty", Map.of());
                return;
            }

            int totalPages = Math.max(1, (int) Math.ceil(
                    (double) waypoints.size() / WaypointGuiHolder.ITEMS_PER_PAGE));
            int p = clampPage(targetPage, totalPages);

            List<Waypoint> pageWaypoints = subList(waypoints, p, WaypointGuiHolder.ITEMS_PER_PAGE);

            Component title = msg.format("waypoint.gui.manage-title",
                    Map.of("page", String.valueOf(p + 1),
                            "total", String.valueOf(totalPages)));

            WaypointManageGuiHolder holder = new WaypointManageGuiHolder(p, totalPages, pageWaypoints);
            Inventory inv = Bukkit.createInventory(holder, WaypointGuiHolder.TOTAL_SLOTS, title);
            holder.setInventory(inv);

            fillWaypointItems(inv, pageWaypoints);
            fillPrevNext(inv, holder, "waypoint.gui.prev", "waypoint.gui.next");
            fillAddButton(inv, WaypointManageGuiHolder.MANAGE_ADD_SLOT);

            player.openInventory(inv);
        }, mainThread);
    }

    // ── Action GUI ────────────────────────────────────────────

    /**
     * Open the action GUI for the given waypoint.
     *
     * @param player       the player
     * @param waypointName the waypoint to act on
     * @param returnPage   the page to return to in the manage GUI
     */
    public void openActionGui(Player player, String waypointName, int returnPage) {
        WaypointActionGuiHolder holder = new WaypointActionGuiHolder(waypointName, returnPage);
        Component title = msg.format("waypoint.gui.action-title",
                Map.of("waypoint", waypointName));
        Inventory inv = Bukkit.createInventory(holder,
                WaypointActionGuiHolder.GUI_SIZE, title);
        holder.setInventory(inv);

        // Teleport
        ItemStack teleport = new ItemStack(Material.ENDER_PEARL);
        ItemMeta tMeta = teleport.getItemMeta();
        tMeta.displayName(msg.format("waypoint.gui.action-teleport", Map.of()));
        teleport.setItemMeta(tMeta);
        inv.setItem(WaypointActionGuiHolder.TELEPORT_SLOT, teleport);

        // Delete
        ItemStack delete = new ItemStack(Material.BARRIER);
        ItemMeta dMeta = delete.getItemMeta();
        dMeta.displayName(msg.format("waypoint.gui.action-delete", Map.of()));
        delete.setItemMeta(dMeta);
        inv.setItem(WaypointActionGuiHolder.DELETE_SLOT, delete);

        // Share
        ItemStack share = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta sMeta = share.getItemMeta();
        sMeta.displayName(msg.format("waypoint.gui.action-share", Map.of()));
        share.setItemMeta(sMeta);
        inv.setItem(WaypointActionGuiHolder.SHARE_SLOT, share);

        // Back
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bMeta = back.getItemMeta();
        bMeta.displayName(msg.format("waypoint.gui.action-back", Map.of()));
        back.setItemMeta(bMeta);
        inv.setItem(WaypointActionGuiHolder.BACK_SLOT, back);

        player.openInventory(inv);
    }

    // ── Share Target GUI ──────────────────────────────────────

    /**
     * Open the share-target selection GUI (list of online players, excluding self).
     *
     * @param player       the player sharing a waypoint
     * @param waypointName the waypoint being shared
     * @param returnPage   the page to return to in the manage GUI
     * @param page         current page of the player list
     */
    public void openShareTargetGui(Player player, String waypointName,
                                   int returnPage, int page) {
        // Build list synchronously (online players from main thread)
        List<Player> onlinePlayers = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getUniqueId().equals(player.getUniqueId())) {
                onlinePlayers.add(online);
            }
        }

        if (onlinePlayers.isEmpty()) {
            msg.send(player, "waypoint.gui.share-no-targets", Map.of());
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(
                (double) onlinePlayers.size() / WaypointShareTargetGuiHolder.ITEMS_PER_PAGE));
        int p = clampPage(page, totalPages);
        List<Player> pagePlayers = subList(onlinePlayers, p,
                WaypointShareTargetGuiHolder.ITEMS_PER_PAGE);

        Component title = msg.format("waypoint.gui.share-title",
                Map.of("waypoint", waypointName,
                        "page", String.valueOf(p + 1),
                        "total", String.valueOf(totalPages)));

        WaypointShareTargetGuiHolder holder = new WaypointShareTargetGuiHolder(
                waypointName, returnPage, p, totalPages, pagePlayers);
        Inventory inv = Bukkit.createInventory(holder,
                WaypointShareTargetGuiHolder.TOTAL_SLOTS, title);
        holder.setInventory(inv);

        fillPlayerItems(inv, pagePlayers);
        fillPrevNext(inv, holder, "waypoint.gui.prev", "waypoint.gui.next");
        fillBackButton(inv, holder);

        player.openInventory(inv);
    }

    // ── Item population helpers ───────────────────────────────

    private void fillWaypointItems(Inventory inv, List<Waypoint> waypoints) {
        for (int i = 0; i < waypoints.size(); i++) {
            Waypoint wp = waypoints.get(i);
            ItemStack item = new ItemStack(Material.ENDER_PEARL);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(msg.format("waypoint.gui.waypoint-name",
                    Map.of("waypoint", wp.name())));

            meta.lore(List.of(msg.format("waypoint.gui.waypoint-lore",
                    Map.of("world", wp.world(),
                            "x", String.valueOf((int) wp.x()),
                            "y", String.valueOf((int) wp.y()),
                            "z", String.valueOf((int) wp.z())))));

            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
    }

    private void fillPlayerItems(Inventory inv, List<Player> players) {
        for (int i = 0; i < players.size(); i++) {
            Player target = players.get(i);
            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = item.getItemMeta();

            meta.displayName(msg.format("waypoint.gui.share-player-name",
                    Map.of("player", target.getName())));

            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
    }

    private void fillPrevNext(Inventory inv, WaypointGuiHolder holder,
                              String prevKey, String nextKey) {
        if (holder.hasPrev()) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(msg.format(prevKey,
                    Map.of("page", String.valueOf(holder.page()))));
            prev.setItemMeta(meta);
            inv.setItem(WaypointGuiHolder.PREV_SLOT, prev);
        }

        if (holder.hasNext()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(msg.format(nextKey,
                    Map.of("page", String.valueOf(holder.page() + 2))));
            next.setItemMeta(meta);
            inv.setItem(WaypointGuiHolder.NEXT_SLOT, next);
        }
    }

    private void fillPrevNext(Inventory inv, WaypointShareTargetGuiHolder holder,
                              String prevKey, String nextKey) {
        if (holder.hasPrev()) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            meta.displayName(msg.format(prevKey,
                    Map.of("page", String.valueOf(holder.page()))));
            prev.setItemMeta(meta);
            inv.setItem(WaypointShareTargetGuiHolder.PREV_SLOT, prev);
        }

        if (holder.hasNext()) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            meta.displayName(msg.format(nextKey,
                    Map.of("page", String.valueOf(holder.page() + 2))));
            next.setItemMeta(meta);
            inv.setItem(WaypointShareTargetGuiHolder.NEXT_SLOT, next);
        }
    }

    private void fillAddButton(Inventory inv, int slot) {
        ItemStack add = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = add.getItemMeta();
        meta.displayName(msg.format("waypoint.gui.add-button", Map.of()));
        add.setItemMeta(meta);
        inv.setItem(slot, add);
    }

    private void fillManageButton(Inventory inv) {
        ItemStack manage = new ItemStack(Material.CHEST);
        ItemMeta meta = manage.getItemMeta();
        meta.displayName(msg.format("waypoint.gui.manage-button", Map.of()));
        manage.setItemMeta(meta);
        inv.setItem(WaypointGuiHolder.MANAGE_SLOT, manage);
    }

    private void fillBackButton(Inventory inv, WaypointShareTargetGuiHolder holder) {
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta meta = back.getItemMeta();
        meta.displayName(msg.format("waypoint.gui.action-back", Map.of()));
        back.setItemMeta(meta);
        inv.setItem(WaypointShareTargetGuiHolder.BACK_SLOT, back);
    }

    // ── Utility ───────────────────────────────────────────────

    private static int clampPage(int page, int totalPages) {
        if (page < 0) return 0;
        if (page >= totalPages) return totalPages - 1;
        return page;
    }

    private static <T> List<T> subList(List<T> all, int page, int perPage) {
        int from = page * perPage;
        if (from >= all.size()) {
            return List.of();
        }
        int to = Math.min(from + perPage, all.size());
        return all.subList(from, to);
    }
}
