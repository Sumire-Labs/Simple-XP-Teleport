package com.example.sxt.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Holder for the waypoint action GUI (teleport / delete / share / back).
 *
 * <p>A 9-slot single-row inventory. Identified in
 * {@code InventoryClickEvent} via {@code instanceof}.</p>
 */
public final class WaypointActionGuiHolder implements InventoryHolder {

    public static final int GUI_SIZE = 9;
    public static final int TELEPORT_SLOT = 0;
    public static final int DELETE_SLOT = 2;
    public static final int SHARE_SLOT = 4;
    public static final int BACK_SLOT = 8;

    private Inventory inventory;
    private final String waypointName;
    private final int returnPage;

    public WaypointActionGuiHolder(String waypointName, int returnPage) {
        this.waypointName = waypointName;
        this.returnPage = returnPage;
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
}
