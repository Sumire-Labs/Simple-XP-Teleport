package com.example.sxt.data.dao;

import com.example.sxt.data.DatabaseManager;
import com.example.sxt.data.SqlException;
import com.example.sxt.data.model.Waypoint;
import org.jetbrains.annotations.VisibleForTesting;

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
 * CRUD operations for the {@code waypoints} table.
 * Every public method returns a {@link CompletableFuture} and executes
 * SQL on an async scheduler thread.
 */
public final class WaypointDao {

    private final DatabaseManager db;
    private final boolean sync;

    public WaypointDao(DatabaseManager db) {
        this(db, false);
    }

    /** Package-private constructor for unit tests: runs SQL on the calling thread. */
    @VisibleForTesting
    WaypointDao(DatabaseManager db, boolean sync) {
        this.db = db;
        this.sync = sync;
    }

    private void runAsync(Runnable task) {
        if (sync) {
            task.run();
        } else {
            db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), task);
        }
    }

    /** Insert or replace a waypoint row (unique on owner + name). */
    public CompletableFuture<Void> save(Waypoint waypoint) {
        if (waypoint == null) {
            return CompletableFuture.failedFuture(new SqlException("waypoint must not be null"));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        runAsync(() -> {
            String sql = """
                    INSERT OR REPLACE INTO waypoints
                        (owner_uuid, name, world, x, y, z, yaw, pitch, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, waypoint.ownerUuid().toString());
                ps.setString(2, waypoint.name());
                ps.setString(3, waypoint.world());
                ps.setDouble(4, waypoint.x());
                ps.setDouble(5, waypoint.y());
                ps.setDouble(6, waypoint.z());
                ps.setFloat(7, waypoint.yaw());
                ps.setFloat(8, waypoint.pitch());
                ps.setLong(9, waypoint.createdAt());
                ps.setLong(10, waypoint.updatedAt());
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to save waypoint", e));
            }
        });
        return future;
    }

    /** Delete a waypoint by owner UUID and name. */
    public CompletableFuture<Void> delete(UUID ownerUuid, String name) {
        if (ownerUuid == null || name == null) {
            return CompletableFuture.failedFuture(new SqlException("ownerUuid and name must not be null"));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        runAsync(() -> {
            String sql = "DELETE FROM waypoints WHERE owner_uuid = ? AND name = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, name);
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to delete waypoint", e));
            }
        });
        return future;
    }

    /** Find a single waypoint by owner UUID and name. */
    public CompletableFuture<Optional<Waypoint>> findOne(UUID ownerUuid, String name) {
        if (ownerUuid == null || name == null) {
            return CompletableFuture.failedFuture(new SqlException("ownerUuid and name must not be null"));
        }
        CompletableFuture<Optional<Waypoint>> future = new CompletableFuture<>();
        runAsync(() -> {
            String sql = "SELECT * FROM waypoints WHERE owner_uuid = ? AND name = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(Optional.of(mapRow(rs)));
                    } else {
                        future.complete(Optional.empty());
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to find waypoint", e));
            }
        });
        return future;
    }

    /** List all waypoints belonging to an owner. Returns empty list if none. */
    public CompletableFuture<List<Waypoint>> listByOwner(UUID ownerUuid) {
        if (ownerUuid == null) {
            return CompletableFuture.failedFuture(new SqlException("ownerUuid must not be null"));
        }
        CompletableFuture<List<Waypoint>> future = new CompletableFuture<>();
        runAsync(() -> {
            String sql = "SELECT * FROM waypoints WHERE owner_uuid = ? ORDER BY name";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Waypoint> waypoints = new ArrayList<>();
                    while (rs.next()) {
                        waypoints.add(mapRow(rs));
                    }
                    future.complete(Collections.unmodifiableList(waypoints));
                }
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to list waypoints", e));
            }
        });
        return future;
    }

    /** Count how many waypoints an owner has. */
    public CompletableFuture<Integer> countByOwner(UUID ownerUuid) {
        if (ownerUuid == null) {
            return CompletableFuture.failedFuture(new SqlException("ownerUuid must not be null"));
        }
        CompletableFuture<Integer> future = new CompletableFuture<>();
        runAsync(() -> {
            String sql = "SELECT COUNT(*) FROM waypoints WHERE owner_uuid = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, ownerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    future.complete(rs.next() ? rs.getInt(1) : 0);
                }
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to count waypoints", e));
            }
        });
        return future;
    }

    private static Waypoint mapRow(ResultSet rs) throws SQLException {
        return new Waypoint(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner_uuid")),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getLong("created_at"),
                rs.getLong("updated_at")
        );
    }
}
