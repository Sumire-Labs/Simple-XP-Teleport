package com.example.sxt.data.model;

import java.util.UUID;

public record BackLocation(UUID playerUuid,
                           String world, double x, double y, double z,
                           float yaw, float pitch,
                           long savedAt, BackReason reason) {
    public enum BackReason {
        TELEPORT,
        DEATH
    }
}
