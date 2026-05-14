package com.example.sxt.gui;

import com.example.sxt.data.model.Waypoint;

import java.util.List;

/**
 * Holder for the waypoint management GUI opened by {@code /wayx list}.
 *
 * <p>Extends {@link WaypointGuiHolder} with a dedicated add button slot
 * but without the manage button (already in management view). Waypoint
 * item clicks open the {@link WaypointActionGuiHolder}.</p>
 */
public final class WaypointManageGuiHolder extends WaypointGuiHolder {

    public static final int MANAGE_ADD_SLOT = 47;

    public WaypointManageGuiHolder(int page, int totalPages, List<Waypoint> waypointsOnPage) {
        super(page, totalPages, waypointsOnPage);
    }

    @Override
    public boolean isAddSlot(int slot) {
        return slot == MANAGE_ADD_SLOT;
    }

    @Override
    public boolean isManageSlot(int slot) {
        // No manage button in the manage GUI itself
        return false;
    }
}
