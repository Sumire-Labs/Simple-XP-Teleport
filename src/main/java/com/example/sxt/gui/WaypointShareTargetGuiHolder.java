package com.example.sxt.gui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Holder for the share-target selection GUI.
 *
 * <p>Displays online players (excluding the invoker) as pageable items.
 * Carries the waypoint name and return page so the listener can initiate
 * the share after a target is chosen.</p>
 */
public final class WaypointShareTargetGuiHolder implements InventoryHolder {

    public static final int TOTAL_SLOTS = 54;
    public static final int ITEMS_PER_PAGE = 45;
    public static final int PREV_SLOT = 45;
    public static final int BACK_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    private Inventory inventory;
    private final String waypointName;
    private final int returnPage;
    private final int page;
    private final int totalPages;
    private final List<Player> playersOnPage;

    public WaypointShareTargetGuiHolder(String waypointName, int returnPage,
                                        int page, int totalPages,
                                        List<Player> playersOnPage) {
        this.waypointName = waypointName;
        this.returnPage = returnPage;
        this.page = page;
        this.totalPages = totalPages;
        this.playersOnPage = Collections.unmodifiableList(playersOnPage);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String waypointName() {
        return waypointName;
    }

    public int returnPage() {
        return returnPage;
    }

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

    public boolean isPrevSlot(int slot) {
        return slot == PREV_SLOT && hasPrev();
    }

    public boolean isNextSlot(int slot) {
        return slot == NEXT_SLOT && hasNext();
    }

    public boolean isBackSlot(int slot) {
        return slot == BACK_SLOT;
    }

    @Nullable
    public Player getPlayerAtSlot(int slot) {
        if (slot < 0 || slot >= playersOnPage.size()) {
            return null;
        }
        return playersOnPage.get(slot);
    }

    public List<Player> playersOnPage() {
        return playersOnPage;
    }
}
