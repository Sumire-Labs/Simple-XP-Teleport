package com.example.sxt.data.model;

import java.util.UUID;

public record Warp(long id, String name,
                   String world, double x, double y, double z,
                   float yaw, float pitch,
                   UUID createdBy, long createdAt, long updatedAt) {
}
