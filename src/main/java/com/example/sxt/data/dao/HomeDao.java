package com.example.sxt.data.dao;

import com.example.sxt.data.DatabaseManager;
import com.example.sxt.data.SqlException;
import com.example.sxt.data.model.Home;

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
 * CRUD operations for the {@code homes} table.
 * Every public method returns a {@link CompletableFuture} and executes
 * SQL on an async scheduler thread.
 */
public final class HomeDao {

    private final DatabaseManager db;

    public HomeDao(DatabaseManager db) {
        this.db = db;
    }

    /** Insert or replace a home row (unique on player + name). */
    public CompletableFuture<Void> save(Home home) {
        if (home == null) {
            return CompletableFuture.failedFuture(new SqlException("home must not be null"));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = """
                    INSERT OR REPLACE INTO homes
                        (player_uuid, name, world, x, y, z, yaw, pitch, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, home.playerUuid().toString());
                ps.setString(2, home.name());
                ps.setString(3, home.world());
                ps.setDouble(4, home.x());
                ps.setDouble(5, home.y());
                ps.setDouble(6, home.z());
                ps.setFloat(7, home.yaw());
                ps.setFloat(8, home.pitch());
                ps.setLong(9, home.createdAt());
                ps.setLong(10, home.updatedAt());
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to save home", e));
            }
        });
        return future;
    }

    /** Delete a home by player UUID and name. */
    public CompletableFuture<Void> delete(UUID playerUuid, String name) {
        if (playerUuid == null || name == null) {
            return CompletableFuture.failedFuture(new SqlException("playerUuid and name must not be null"));
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = "DELETE FROM homes WHERE player_uuid = ? AND name = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, name);
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to delete home", e));
            }
        });
        return future;
    }

    /** Find a single home by player UUID and name. */
    public CompletableFuture<Optional<Home>> findOne(UUID playerUuid, String name) {
        if (playerUuid == null || name == null) {
            return CompletableFuture.failedFuture(new SqlException("playerUuid and name must not be null"));
        }
        CompletableFuture<Optional<Home>> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = "SELECT * FROM homes WHERE player_uuid = ? AND name = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.setString(2, name);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(Optional.of(mapRow(rs)));
                    } else {
                        future.complete(Optional.empty());
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to find home", e));
            }
        });
        return future;
    }

    /** List all homes belonging to a player. Returns empty list if none. */
    public CompletableFuture<List<Home>> listByPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.failedFuture(new SqlException("playerUuid must not be null"));
        }
        CompletableFuture<List<Home>> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = "SELECT * FROM homes WHERE player_uuid = ? ORDER BY name";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    List<Home> homes = new ArrayList<>();
                    while (rs.next()) {
                        homes.add(mapRow(rs));
                    }
                    future.complete(Collections.unmodifiableList(homes));
                }
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to list homes", e));
            }
        });
        return future;
    }

    /** Count how many homes a player has. */
    public CompletableFuture<Integer> countByPlayer(UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.failedFuture(new SqlException("playerUuid must not be null"));
        }
        CompletableFuture<Integer> future = new CompletableFuture<>();
        db.plugin().getServer().getScheduler().runTaskAsynchronously(db.plugin(), () -> {
            String sql = "SELECT COUNT(*) FROM homes WHERE player_uuid = ?";
            try (Connection conn = db.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    future.complete(rs.next() ? rs.getInt(1) : 0);
                }
            } catch (SQLException e) {
                future.completeExceptionally(new SqlException("Failed to count homes", e));
            }
        });
        return future;
    }

    private static Home mapRow(ResultSet rs) throws SQLException {
        return new Home(
                rs.getLong("id"),
                UUID.fromString(rs.getString("player_uuid")),
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
