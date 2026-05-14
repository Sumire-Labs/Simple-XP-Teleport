package com.example.sxt.gui;

import com.example.sxt.data.model.Warp;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Holder for the public warp selection GUI.
 *
 * <p>Slots 0–44 carry warp items (max 45 per page). The bottom row
 * (slots 45–53) is reserved for navigation controls.</p>
 *
 * <p>Identified in {@code InventoryClickEvent} via
 * {@code event.getInventory().getHolder() instanceof WarpGuiHolder}.</p>
 */
public class WarpGuiHolder implements InventoryHolder {

    public static final int TOTAL_SLOTS = 54;
    public static final int ITEMS_PER_PAGE = 45; // rows 0–4
    public static final int PREV_SLOT = 45;
    public static final int NEXT_SLOT = 53;

    private Inventory inventory;
    private final int page;
    private final int totalPages;
    private final List<Warp> warpsOnPage;

    public WarpGuiHolder(int page, int totalPages, List<Warp> warpsOnPage) {
        this.page = page;
        this.totalPages = totalPages;
        this.warpsOnPage = Collections.unmodifiableList(warpsOnPage);
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

    /**
     * Returns the warp at the given slot index (0–44), or {@code null}
     * if the slot is not a warp slot.
     */
    @Nullable
    public Warp getWarpAtSlot(int slot) {
        if (slot < 0 || slot >= warpsOnPage.size()) {
            return null;
        }
        return warpsOnPage.get(slot);
    }

    public List<Warp> warpsOnPage() {
        return warpsOnPage;
    }
}
