package net.mineaqua.afkregions.database;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseManager {
    private final AFKRegionsPlugin plugin;
    private final ExecutorService executor;
    private Connection connection;
    private boolean enabled;

    // Configuración de la base de datos
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;
    private boolean useSSL;

    public DatabaseManager(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AFKRegions-Database");
            t.setDaemon(true);
            return t;
        });

        loadConfig();

        if (enabled) {
            initializeDatabase();
        }
    }

    private void loadConfig() {
        ConfigurationSection dbConfig = plugin.getConfig().getConfigurationSection("database");
        if (dbConfig == null) {
            this.enabled = false;
            plugin.getLogger().info("Database disabled - no configuration found.");
            return;
        }

        this.enabled = dbConfig.getBoolean("enabled", false);
        this.host = dbConfig.getString("host", "localhost");
        this.port = dbConfig.getInt("port", 3306);
        this.database = dbConfig.getString("database", "afkregions");
        this.username = dbConfig.getString("username", "root");
        this.password = dbConfig.getString("password", "");
        this.useSSL = dbConfig.getBoolean("use_ssl", false);

        if (!enabled) {
            plugin.getLogger().info("Database disabled in configuration.");
        }
    }

    private void initializeDatabase() {
        executor.submit(() -> {
            try {
                // Cargar el driver de MariaDB
                Class.forName("org.mariadb.jdbc.Driver");

                String url = String.format("jdbc:mariadb://%s:%d/%s?useSSL=%s&autoReconnect=true",
                        host, port, database, useSSL);

                this.connection = DriverManager.getConnection(url, username, password);

                // Crear la tabla si no existe
                createTables();

                plugin.getLogger().info("Database connection established successfully.");

            } catch (ClassNotFoundException e) {
                plugin.getLogger().severe("MariaDB driver not found! Please add MariaDB connector to your plugins folder.");
                this.enabled = false;
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to connect to database: " + e.getMessage());
                this.enabled = false;
            }
        });
    }

    private void createTables() throws SQLException {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS afk_statistics (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "total_afk_seconds BIGINT NOT NULL DEFAULT 0, " +
                "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "INDEX idx_player_name (player_name), " +
                "INDEX idx_total_seconds (total_afk_seconds)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public CompletableFuture<Long> getPlayerAFKSeconds(UUID playerId) {
        if (!enabled) {
            return CompletableFuture.completedFuture(0L);
        }

        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT total_afk_seconds FROM afk_statistics WHERE uuid = ?";

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getLong("total_afk_seconds");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to fetch AFK seconds for player " + playerId + ": " + e.getMessage());
            }

            return 0L;
        }, executor);
    }

    public CompletableFuture<Void> savePlayerAFKSeconds(UUID playerId, String playerName, long totalSeconds) {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO afk_statistics (uuid, player_name, total_afk_seconds) " +
                    "VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "player_name = VALUES(player_name), " +
                    "total_afk_seconds = VALUES(total_afk_seconds)";

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, totalSeconds);

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to save AFK seconds for player " + playerId + ": " + e.getMessage());
            }
        }, executor);
    }

    public CompletableFuture<Void> addPlayerAFKSeconds(UUID playerId, String playerName, long secondsToAdd) {
        if (!enabled || secondsToAdd <= 0) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO afk_statistics (uuid, player_name, total_afk_seconds) " +
                    "VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "player_name = VALUES(player_name), " +
                    "total_afk_seconds = total_afk_seconds + VALUES(total_afk_seconds)";

            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerId.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, secondsToAdd);

                stmt.executeUpdate();

            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to add AFK seconds for player " + playerId + ": " + e.getMessage());
            }
        }, executor);
    }

    public void close() {
        if (enabled && connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
            }
        }

        executor.shutdown();
    }

    public boolean isConnectionValid() {
        if (!enabled || connection == null) {
            return false;
        }

        try {
            return connection.isValid(5);
        } catch (SQLException e) {
            return false;
        }
    }
}
