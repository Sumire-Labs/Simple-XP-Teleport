package com.example.sxt.data.dao;

import com.example.sxt.data.DatabaseManager;
import com.example.sxt.data.SqlException;
import com.example.sxt.data.model.BackLocation;
import com.example.sxt.data.model.BackLocation.BackReason;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * CRUD operations for the {@code back_locations} table.
 * Every public method returns a {@link CompletableFuture} and executes
 * SQL on an async scheduler thread.
 */
public final class BackLocationDao {

    private final DatabaseManager db;

    public BackLocationDao(DatabaseManager db) {
        this.db = db;
    }

    /** Insert or replace a back location (unique on player_uuid = PRIMARY KEY). */
    public CompletableFuture<Void> upsert(BackLocation loc) {
        if (loc == null) {
            return CompletableFuture.failedFuture(new SqlException("backLocation must not be null"));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = """
                    INSERT OR REPLACE INTO back_locations
                        (player_uuid, world, x, y, z, yaw, pitch, saved_at, reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, loc.playerUuid().toString());
                ps.setString(2, loc.world());
                ps.setDouble(3, loc.x());
                ps.setDouble(4, loc.y());
                ps.setDouble(5, loc.z());
                ps.setFloat(6, loc.yaw());
                ps.setFloat(7, loc.pitch());
                ps.setLong(8, loc.savedAt());
                ps.setString(9, loc.reason().name());
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to upsert back location", e));
            }
        });
        return future;
    }

    /** Find the stored back location for a player, if any. */
    public CompletableFuture<Optional<BackLocation>> find(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.failedFuture(new SqlException("playerUuid must not be null"));
        }
        CompletableFuture<Optional<BackLocation>> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = "SELECT * FROM back_locations WHERE player_uuid = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(Optional.of(mapRow(rs)));
                    } else {
                        future.complete(Optional.empty());
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to find back location", e));
            }
        });
        return future;
    }

    private static BackLocation mapRow(ResultSet rs) throws SQLException {
        return new BackLocation(
                UUID.fromString(rs.getString("player_uuid")),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getLong("saved_at"),
                BackReason.valueOf(rs.getString("reason"))
        );
    }
}
