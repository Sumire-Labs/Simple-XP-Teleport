package com.example.sxt.data.dao;

import com.example.sxt.SimpleXpTeleportPlugin;
import com.example.sxt.data.DatabaseManager;
import com.example.sxt.data.model.Waypoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link WaypointDao} using a temporary SQLite database
 * and the package-private synchronous constructor to avoid Bukkit scheduler
 * dependencies.
 */
class WaypointDaoTest {

    private SimpleXpTeleportPlugin plugin;
    private Path tempDbFile;
    private DatabaseManager databaseManager;
    private WaypointDao dao;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(SimpleXpTeleportPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        tempDbFile = Path.of(System.getProperty("java.io.tmpdir"), "sxt-waypoint-test-" + UUID.randomUUID() + ".db");
        databaseManager = new DatabaseManager(plugin, "jdbc:sqlite:" + tempDbFile.toAbsolutePath());
        databaseManager.connect();
        dao = new WaypointDao(databaseManager, true); // synchronous mode
    }

    @AfterEach
    void tearDown() {
        tempDbFile.toFile().delete();
    }

    // ── helper ───────────────────────────────────────────────

    private static Waypoint wp(UUID owner, String name, String world,
                               double x, double y, double z, long now) {
        return new Waypoint(0, owner, name, world, x, y, z, 0f, 0f, now, now);
    }

    private static <T> T await(CompletableFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) throw re;
            throw new RuntimeException(cause);
        }
    }

    private static void assertWaypointEquals(Waypoint expected, Waypoint actual) {
        assertEquals(expected.ownerUuid(), actual.ownerUuid(), "ownerUuid");
        assertEquals(expected.name(), actual.name(), "name");
        assertEquals(expected.world(), actual.world(), "world");
        assertEquals(expected.x(), actual.x(), 0.001, "x");
        assertEquals(expected.y(), actual.y(), 0.001, "y");
        assertEquals(expected.z(), actual.z(), 0.001, "z");
        assertEquals(expected.yaw(), actual.yaw(), 0.001, "yaw");
        assertEquals(expected.pitch(), actual.pitch(), 0.001, "pitch");
        assertEquals(expected.createdAt(), actual.createdAt(), "createdAt");
        assertEquals(expected.updatedAt(), actual.updatedAt(), "updatedAt");
        // id is auto-generated – not compared
    }

    // ── save / findOne ───────────────────────────────────────

    @Test
    void shouldSaveAndFindOne() {
        UUID owner = UUID.randomUUID();
        long now = System.currentTimeMillis();
        Waypoint w = wp(owner, "spawn", "world", 100, 64, 200, now);

        await(dao.save(w));
        Optional<Waypoint> found = await(dao.findOne(owner, "spawn"));

        assertTrue(found.isPresent(), "Waypoint should be found after save");
        assertWaypointEquals(w, found.get());
    }

    @Test
    void shouldReturnEmptyForUnknownWaypoint() {
        Optional<Waypoint> found = await(dao.findOne(UUID.randomUUID(), "nonexistent"));
        assertFalse(found.isPresent(), "Non-existent waypoint should not be found");
    }

    // ── listByOwner ──────────────────────────────────────────

    @Test
    void shouldListWaypointsByOwner() {
        UUID owner = UUID.randomUUID();
        long now = System.currentTimeMillis();

        await(dao.save(wp(owner, "home", "world", 0, 64, 0, now)));
        await(dao.save(wp(owner, "farm", "world", 50, 70, 50, now + 1)));
        await(dao.save(wp(owner, "mine", "world_nether", -10, 80, -10, now + 2)));

        List<Waypoint> list = await(dao.listByOwner(owner));

        assertEquals(3, list.size(), "Should list all 3 waypoints");
        // list is ordered by name
        assertEquals("farm", list.get(0).name());
        assertEquals("home", list.get(1).name());
        assertEquals("mine", list.get(2).name());
    }

    @Test
    void shouldReturnEmptyListForOwnerWithNoWaypoints() {
        List<Waypoint> list = await(dao.listByOwner(UUID.randomUUID()));
        assertTrue(list.isEmpty(), "List should be empty for unknown owner");
    }

    // ── countByOwner ─────────────────────────────────────────

    @Test
    void shouldCountWaypointsByOwner() {
        UUID owner = UUID.randomUUID();
        long now = System.currentTimeMillis();

        assertEquals(0, (int) await(dao.countByOwner(owner)), "Initial count should be 0");

        await(dao.save(wp(owner, "a", "world", 0, 0, 0, now)));
        assertEquals(1, (int) await(dao.countByOwner(owner)), "Count should be 1 after first save");

        await(dao.save(wp(owner, "b", "world", 0, 0, 0, now)));
        assertEquals(2, (int) await(dao.countByOwner(owner)), "Count should be 2 after second save");

        // different owner should not affect count
        assertEquals(0, (int) await(dao.countByOwner(UUID.randomUUID())), "Other owner should have 0");
    }

    // ── delete ───────────────────────────────────────────────

    @Test
    void shouldDeleteWaypoint() {
        UUID owner = UUID.randomUUID();
        long now = System.currentTimeMillis();

        await(dao.save(wp(owner, "temp", "world", 0, 64, 0, now)));
        assertTrue(await(dao.findOne(owner, "temp")).isPresent(), "Should exist before delete");

        await(dao.delete(owner, "temp"));
        assertFalse(await(dao.findOne(owner, "temp")).isPresent(), "Should be gone after delete");
        assertEquals(0, (int) await(dao.countByOwner(owner)), "Count should be 0 after delete");
    }

    @Test
    void deleteOfNonExistentWaypointShouldNotThrow() {
        await(dao.delete(UUID.randomUUID(), "nonexistent"));
        // no exception should be thrown
    }

    // ── upsert (INSERT OR REPLACE) ───────────────────────────

    @Test
    void shouldUpsertOnSameOwnerAndName() {
        UUID owner = UUID.randomUUID();
        long now = System.currentTimeMillis();

        Waypoint original = wp(owner, "base", "world", 10, 20, 30, now);
        await(dao.save(original));

        // save again with same owner+name but different location
        Waypoint updated = wp(owner, "base", "world_nether", 100, 200, 300, now + 1000);
        await(dao.save(updated));

        // Should have exactly one row
        assertEquals(1, (int) await(dao.countByOwner(owner)), "Should still be exactly 1 waypoint after upsert");

        Optional<Waypoint> found = await(dao.findOne(owner, "base"));
        assertTrue(found.isPresent());
        assertWaypointEquals(updated, found.get());
    }
}
