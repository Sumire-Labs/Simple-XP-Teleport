package com.example.sxt.gui;

import com.example.sxt.data.model.Warp;

import java.util.List;

/**
 * Holder for the admin warp management GUI.
 *
 * <p>Extends {@link WarpGuiHolder} with additional control slots
 * for warp administration (add, delete, overwrite). Full save
 * handling is deferred to a later task — this holder provides the
 * slot-mapping infrastructure.</p>
 */
public final class WarpAdminGuiHolder extends WarpGuiHolder {

    public static final int ADD_SLOT = 47;

    public WarpAdminGuiHolder(int page, int totalPages, List<Warp> warpsOnPage) {
        super(page, totalPages, warpsOnPage);
    }

    public boolean isAddSlot(int slot) {
        return slot == ADD_SLOT;
    }
}
