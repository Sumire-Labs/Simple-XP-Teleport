package com.example.sxt.data.dao;

import com.example.sxt.data.DatabaseManager;
import com.example.sxt.data.SqlException;
import com.example.sxt.data.model.Warp;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * CRUD operations for the {@code warps} table.
 * Every public method returns a {@link CompletableFuture} and executes
 * SQL on an async scheduler thread.
 */
public final class WarpDao {

    private final DatabaseManager db;

    public WarpDao(DatabaseManager db) {
        this.db = db;
    }

    /** Insert or replace a warp row (unique on name). */
    public CompletableFuture<Void> save(Warp warp) {
        if (warp == null) {
            return CompletableFuture.failedFuture(new SqlException("warp must not be null"));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = """
                    INSERT OR REPLACE INTO warps
                        (name, world, x, y, z, yaw, pitch, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, warp.name());
                ps.setString(2, warp.world());
                ps.setDouble(3, warp.x());
                ps.setDouble(4, warp.y());
                ps.setDouble(5, warp.z());
                ps.setFloat(6, warp.yaw());
                ps.setFloat(7, warp.pitch());
                ps.setString(8, warp.createdBy().toString());
                ps.setLong(9, warp.createdAt());
                ps.setLong(10, warp.updatedAt());
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to save warp", e));
            }
        });
        return future;
    }

    /** Delete a warp by name. */
    public CompletableFuture<Void> delete(String name) {
        if (name == null) {
            return CompletableFuture.failedFuture(new SqlException("name must not be null"));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = "DELETE FROM warps WHERE name = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to delete warp", e));
            }
        });
        return future;
    }

    /** Find a single warp by name. */
    public CompletableFuture<Optional<Warp>> findOne(String name) {
        if (name == null) {
            return CompletableFuture.failedFuture(new SqlException("name must not be null"));
        }
        CompletableFuture<Optional<Warp>> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = "SELECT * FROM warps WHERE name = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(Optional.of(mapRow(rs)));
                    } else {
                        future.complete(Optional.empty());
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to find warp", e));
            }
        });
        return future;
    }

    /** List all warps. Returns empty list if none. */
    public CompletableFuture<List<Warp>> listAll() {
        CompletableFuture<List<Warp>> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = "SELECT * FROM warps ORDER BY name";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                List<Warp> warps = new ArrayList<>();
                while (rs.next()) {
                    warps.add(mapRow(rs));
                }
                future.complete(Collections.unmodifiableList(warps));
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to list warps", e));
            }
        });
        return future;
    }

    private static Warp mapRow(ResultSet rs) throws SQLException {
        return new Warp(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                UUID.fromString(rs.getString("created_by")),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
