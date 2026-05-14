package com.example.sxt.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder for the admin warp action GUI (teleport / overwrite / delete / back).
 *
 * <p>A 9-slot single-row inventory. Identified in
 * {@code InventoryClickEvent} via {@code instanceof}.</p>
 */
public final class WarpAdminActionGuiHolder implements InventoryHolder {

    public static final int GUI_SIZE = 9;
    public static final int TELEPORT_SLOT = 0;
    public static final int OVERWRITE_SLOT = 2;
    public static final int DELETE_SLOT = 4;
    public static final int BACK_SLOT = 8;

    private Inventory inventory;
    private final String warpName;
    private final int returnPage;

    public WarpAdminActionGuiHolder(String warpName, int returnPage) {
        this.warpName = warpName;
        this.returnPage = returnPage;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String warpName() {
        return warpName;
    }

    public int returnPage() {
        return returnPage;
    }
}
