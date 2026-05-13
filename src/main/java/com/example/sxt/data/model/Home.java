package com.example.sxt.data.model;

import java.util.UUID;

public record Home(long id, UUID playerUuid, String name,
                   String world, double x, double y, double z,
                   float yaw, float pitch,
                   long createdAt, long updatedAt) {
}
