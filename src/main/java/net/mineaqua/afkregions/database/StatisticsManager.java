package net.mineaqua.afkregions.database;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StatisticsManager implements Listener {
    private final AFKRegionsPlugin plugin;
    private final DatabaseManager databaseManager;

    // Cache de estadísticas en memoria
    private final Map<UUID, PlayerStatistics> playerStats = new ConcurrentHashMap<>();

    // Configuración
    private boolean enabled;
    private int saveIntervalMinutes;
    private int autoSaveTaskId = -1;

    public StatisticsManager(AFKRegionsPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;

        loadConfig();

        if (enabled) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            startAutoSaveTask();
            plugin.getLogger().info("Statistics system enabled.");
        } else {
            plugin.getLogger().info("Statistics system disabled.");
        }
    }

    private void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("statistics.enabled", true);
        this.saveIntervalMinutes = Math.max(1, plugin.getConfig().getInt("statistics.auto_save_interval_minutes", 5));
    }

    public void reloadConfig() {
        loadConfig();

        if (enabled && autoSaveTaskId == -1) {
            startAutoSaveTask();
        } else if (!enabled && autoSaveTaskId != -1) {
            stopAutoSaveTask();
        }
    }

    private void startAutoSaveTask() {
        if (autoSaveTaskId != -1) return;

        autoSaveTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                saveAllDirtyStats();
            }
        }.runTaskTimerAsynchronously(plugin, saveIntervalMinutes * 20L * 60L, saveIntervalMinutes * 20L * 60L).getTaskId();
    }

    private void stopAutoSaveTask() {
        if (autoSaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(autoSaveTaskId);
            autoSaveTaskId = -1;
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enabled) return;

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Cargar estadísticas desde la base de datos
        if (databaseManager.isEnabled()) {
            databaseManager.getPlayerAFKSeconds(playerId).thenAccept(totalSeconds -> {
                PlayerStatistics stats = new PlayerStatistics(playerId, player.getName(), totalSeconds);
                playerStats.put(playerId, stats);
            });
        } else {
            // Si la base de datos está deshabilitada, crear estadísticas vacías
            PlayerStatistics stats = new PlayerStatistics(playerId, player.getName(), 0L);
            playerStats.put(playerId, stats);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!enabled) return;

        UUID playerId = event.getPlayer().getUniqueId();
        PlayerStatistics stats = playerStats.get(playerId);

        if (stats != null && stats.isDirty()) {
            // Guardar estadísticas antes de que el jugador se desconecte
            savePlayerStats(stats);
        }

        // Remover del cache después de un pequeño delay para asegurar que se guarde
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            playerStats.remove(playerId);
        }, 20L); // 1 segundo de delay
    }

    public void addAFKTime(UUID playerId, String playerName, long secondsToAdd) {
        if (!enabled || secondsToAdd <= 0) return;

        PlayerStatistics stats = playerStats.get(playerId);
        if (stats == null) {
            // Crear estadísticas si no existen
            stats = new PlayerStatistics(playerId, playerName, 0L);
            playerStats.put(playerId, stats);
        }

        stats.addAFKSeconds(secondsToAdd);
        stats.updatePlayerName(playerName); // Actualizar el nombre en caso de que haya cambiado
    }

    public long getTotalAFKSeconds(UUID playerId) {
        if (!enabled) return 0L;

        PlayerStatistics stats = playerStats.get(playerId);
        return stats != null ? stats.getTotalAFKSeconds() : 0L;
    }

    public void savePlayerStats(UUID playerId) {
        if (!enabled) return;

        PlayerStatistics stats = playerStats.get(playerId);
        if (stats != null && stats.isDirty()) {
            savePlayerStats(stats);
        }
    }

    private void savePlayerStats(PlayerStatistics stats) {
        if (databaseManager.isEnabled()) {
            databaseManager.savePlayerAFKSeconds(
                stats.getPlayerId(),
                stats.getPlayerName(),
                stats.getTotalAFKSeconds()
            ).thenRun(() -> {
                stats.markAsSaved();
            });
        } else {
            // Si la base de datos está deshabilitada, solo marcar como guardado
            stats.markAsSaved();
        }
    }

    public void saveAllStats() {
        if (!enabled) return;

        for (PlayerStatistics stats : playerStats.values()) {
            if (stats.isDirty()) {
                savePlayerStats(stats);
            }
        }
    }

    private void saveAllDirtyStats() {
        if (!enabled) return;

        int savedCount = 0;
        for (PlayerStatistics stats : playerStats.values()) {
            if (stats.isDirty()) {
                savePlayerStats(stats);
                savedCount++;
            }
        }

        if (savedCount > 0) {
            plugin.getLogger().info("Auto-saved statistics for " + savedCount + " players.");
        }
    }

    public void shutdown() {
        stopAutoSaveTask();

        if (enabled) {
            // Guardar todas las estadísticas antes de cerrar
            saveAllStats();

            // Esperar un poco para que se completen las operaciones asíncronas
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            playerStats.clear();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getCachedPlayersCount() {
        return playerStats.size();
    }

    // Clase interna para representar las estadísticas de un jugador
    private static class PlayerStatistics {
        private final UUID playerId;
        private String playerName;
        private long totalAFKSeconds;
        private boolean dirty; // Indica si las estadísticas han cambiado y necesitan guardarse

        public PlayerStatistics(UUID playerId, String playerName, long totalAFKSeconds) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.totalAFKSeconds = totalAFKSeconds;
            this.dirty = false;
        }

        public void addAFKSeconds(long seconds) {
            this.totalAFKSeconds += seconds;
            this.dirty = true;
        }

        public void updatePlayerName(String newName) {
            if (!this.playerName.equals(newName)) {
                this.playerName = newName;
                this.dirty = true;
            }
        }

        public void markAsSaved() {
            this.dirty = false;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public String getPlayerName() {
            return playerName;
        }

        public long getTotalAFKSeconds() {
            return totalAFKSeconds;
        }

        public boolean isDirty() {
            return dirty;
        }
    }
}
