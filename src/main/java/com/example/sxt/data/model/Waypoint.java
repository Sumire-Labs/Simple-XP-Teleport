package com.example.sxt.data.model;

import java.util.UUID;

/**
 * Represents a personal waypoint owned by a player.
 *
 * <p>Each waypoint is uniquely identified by the combination of
 * {@code ownerUuid} and {@code name}.  It stores a location
 * (world, coordinates, rotation) with timestamps for creation and
 * last update.</p>
 */
public record Waypoint(long id, UUID ownerUuid, String name,
                       String world, double x, double y, double z,
                       float yaw, float pitch,
                       long createdAt, long updatedAt) {
}
