package com.example.sxt.gui;

import com.example.sxt.data.model.Waypoint;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Holder for the public waypoint selection GUI opened by {@code /wayx}.
 *
 * <p>Slots 0–44 carry waypoint items (max 45 per page). The bottom row
 * (slots 45–53) holds navigation, add, and manage buttons.</p>
 *
 * <p>Identified in {@code InventoryClickEvent} via
 * {@code event.getInventory().getHolder() instanceof WaypointGuiHolder}.</p>
 */
public class WaypointGuiHolder implements InventoryHolder {

    public static final int TOTAL_SLOTS = 54;
    public static final int ITEMS_PER_PAGE = 45; // rows 0–4
    public static final int PREV_SLOT = 45;
    public static final int ADD_SLOT = 47;
    public static final int MANAGE_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    private Inventory inventory;
    private final int page;
    private final int totalPages;
    private final List<Waypoint> waypointsOnPage;

    public WaypointGuiHolder(int page, int totalPages, List<Waypoint> waypointsOnPage) {
        this.page = page;
        this.totalPages = totalPages;
        this.waypointsOnPage = Collections.unmodifiableList(waypointsOnPage);
    }

    // ── InventoryHolder contract ──────────────────────────────

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    // ── Page & navigation ─────────────────────────────────────

    public int page() {
        return page;
    }

    public int totalPages() {
        return totalPages;
    }

    public boolean hasPrev() {
        return page > 0;
    }

    public boolean hasNext() {
        return page < totalPages - 1;
    }

    // ── Slot helpers ──────────────────────────────────────────

    public boolean isPrevSlot(int slot) {
        return slot == PREV_SLOT && hasPrev();
    }

    public boolean isNextSlot(int slot) {
        return slot == NEXT_SLOT && hasNext();
    }

    public boolean isAddSlot(int slot) {
        return slot == ADD_SLOT;
    }

    public boolean isManageSlot(int slot) {
        return slot == MANAGE_SLOT;
    }

    /**
     * Returns the waypoint at the given slot index (0–44), or {@code null}
     * if the slot is not a waypoint slot.
     */
    @Nullable
    public Waypoint getWaypointAtSlot(int slot) {
        if (slot < 0 || slot >= waypointsOnPage.size()) {
            return null;
        }
        return waypointsOnPage.get(slot);
    }

    public List<Waypoint> waypointsOnPage() {
        return waypointsOnPage;
    }
}
