package com.example.sxt.data;

import com.example.sxt.SimpleXpTeleportPlugin;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Manages the SQLite connection lifecycle.
 *
 * <p>{@link #connect()} is called once synchronously during {@code onEnable}
 * to apply PRAGMAs and run DDL.  DAO classes obtain connections via
 * {@link #getConnection()} inside async tasks.</p>
 */
public final class DatabaseManager {

    private final SimpleXpTeleportPlugin plugin;
    private final Logger logger;
    private final String url;

    public DatabaseManager(SimpleXpTeleportPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        File dbFile = new File(plugin.getDataFolder(), plugin.getPluginConfig().storageFile());
        this.url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
    }

    /**
     * Constructor for unit tests.
     * Allows injecting a custom JDBC URL (e.g., a temporary file or in-memory SQLite).
     */
    @VisibleForTesting
    public DatabaseManager(SimpleXpTeleportPlugin plugin, String url) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.url = url;
    }

    /**
     * Opens the database, applies PRAGMAs, and creates tables if they
     * do not yet exist.  Must be called on the server thread during
     * {@code onEnable}, before any DAO method is invoked.
     */
    public void connect() throws SQLException {
        try (Connection conn = DriverManager.getConnection(url)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA foreign_keys=ON;");
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS homes (
                            id            INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid   TEXT    NOT NULL,
                            name          TEXT    NOT NULL,
                            world         TEXT    NOT NULL,
                            x             REAL    NOT NULL,
                            y             REAL    NOT NULL,
                            z             REAL    NOT NULL,
                            yaw           REAL    NOT NULL,
                            pitch         REAL    NOT NULL,
                            created_at    INTEGER NOT NULL,
                            updated_at    INTEGER NOT NULL,
                            UNIQUE(player_uuid, name)
                        );
                        """);

                stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_homes_player
                            ON homes(player_uuid);
                        """);

                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS warps (
                            id            INTEGER PRIMARY KEY AUTOINCREMENT,
                            name          TEXT    NOT NULL UNIQUE,
                            world         TEXT    NOT NULL,
                            x             REAL    NOT NULL,
                            y             REAL    NOT NULL,
                            z             REAL    NOT NULL,
                            yaw           REAL    NOT NULL,
                            pitch         REAL    NOT NULL,
                            created_by    TEXT    NOT NULL,
                            created_at    INTEGER NOT NULL,
                            updated_at    INTEGER NOT NULL
                        );
                        """);

                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS waypoints (
                            id            INTEGER PRIMARY KEY AUTOINCREMENT,
                            owner_uuid    TEXT    NOT NULL,
                            name          TEXT    NOT NULL,
                            world         TEXT    NOT NULL,
                            x             REAL    NOT NULL,
                            y             REAL    NOT NULL,
                            z             REAL    NOT NULL,
                            yaw           REAL    NOT NULL,
                            pitch         REAL    NOT NULL,
                            created_at    INTEGER NOT NULL,
                            updated_at    INTEGER NOT NULL,
                            UNIQUE(owner_uuid, name)
                        );
                        """);

                stmt.execute("""
                        CREATE INDEX IF NOT EXISTS idx_waypoints_owner
                            ON waypoints(owner_uuid);
                        """);

                stmt.execute("""
                        CREATE TABLE IF NOT EXISTS back_locations (
                            player_uuid   TEXT    PRIMARY KEY,
                            world         TEXT    NOT NULL,
                            x             REAL    NOT NULL,
                            y             REAL    NOT NULL,
                            z             REAL    NOT NULL,
                            yaw           REAL    NOT NULL,
                            pitch         REAL    NOT NULL,
                            saved_at      INTEGER NOT NULL,
                            reason        TEXT    NOT NULL
                        );
                        """);
            }

            logger.info("Database connected and tables verified: " + url);
        }
    }

    /**
     * Returns a new JDBC connection with {@code foreign_keys=ON} already applied.
     * Callers are responsible for closing the connection in a try-with-resources block.
     */
    @VisibleForTesting
    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=ON;");
        }
        return conn;
    }

    /**
     * Exposed for DAO classes that need to schedule async work.
     */
    public SimpleXpTeleportPlugin plugin() {
        return plugin;
    }

    /**
     * Exposed for DAO classes to log warnings.
     */
    public Logger logger() {
        return logger;
    }
}
