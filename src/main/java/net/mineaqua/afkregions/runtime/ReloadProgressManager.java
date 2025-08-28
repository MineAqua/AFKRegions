package net.mineaqua.afkregions.runtime;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import net.mineaqua.afkregions.model.Region;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maneja la preservación del progreso AFK durante reloads del plugin.
 * Calcula ajustes porcentuales cuando cambian las configuraciones de las regiones.
 */
public class ReloadProgressManager {

    private final AFKRegionsPlugin plugin;

    // Datos de progreso guardados antes del reload
    private final Map<UUID, SavedProgress> savedProgress = new HashMap<>();

    public ReloadProgressManager(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Guarda el progreso actual de todos los jugadores antes de un reload
     */
    public void saveCurrentProgress(Map<UUID, PlayerTracker.State> currentStates) {
        savedProgress.clear();

        for (Map.Entry<UUID, PlayerTracker.State> entry : currentStates.entrySet()) {
            UUID playerId = entry.getKey();
            PlayerTracker.State state = entry.getValue();

            if (state.region() != null) {
                int currentElapsedTicks = state.elapsed();
                int currentElapsedSeconds = currentElapsedTicks / 20;
                int originalDurationSeconds = state.region().durationSeconds();
                double progressPercentage = (double) currentElapsedSeconds / originalDurationSeconds;

                SavedProgress progress = new SavedProgress(
                    state.region().name(),
                    currentElapsedTicks,
                    currentElapsedSeconds,
                    originalDurationSeconds,
                    progressPercentage,
                    state.firedSeconds()
                );

                savedProgress.put(playerId, progress);

                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    plugin.getLogger().info("[DEBUG] Saved progress for player " + playerId +
                        ": region=" + state.region().name() +
                        ", elapsed=" + currentElapsedSeconds + "s" +
                        ", duration=" + originalDurationSeconds + "s" +
                        ", percentage=" + String.format("%.2f", progressPercentage * 100) + "%");
                }
            }
        }
    }

    /**
     * Restaura el progreso después de un reload, ajustando porcentualmente si es necesario
     */
    public void restoreProgress(Map<UUID, PlayerTracker.State> newStates) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID playerId = player.getUniqueId();
            SavedProgress saved = savedProgress.get(playerId);

            if (saved == null) continue;

            // Buscar si el jugador sigue en la misma región
            Region currentRegion = findRegionAt(player);
            if (currentRegion == null || !currentRegion.name().equals(saved.regionName)) {
                continue; // El jugador ya no está en la región guardada
            }

            PlayerTracker.State state = newStates.get(playerId);
            if (state == null) continue;

            // Configurar la región actual
            state.region(currentRegion);

            int newDurationSeconds = currentRegion.durationSeconds();

            if (newDurationSeconds == saved.originalDurationSeconds) {
                // La duración no cambió, restaurar el tiempo exacto
                state.elapsed(saved.elapsedTicks);
                state.firedSeconds().addAll(saved.firedSeconds);

                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    plugin.getLogger().info("[DEBUG] Restored exact progress for player " + player.getName() +
                        ": " + saved.elapsedSeconds + "s (no duration change)");
                }
            } else {
                // La duración cambió, ajustar porcentualmente
                int newElapsedSeconds = (int) Math.round(saved.progressPercentage * newDurationSeconds);
                int newElapsedTicks = newElapsedSeconds * 20;

                state.elapsed(newElapsedTicks);

                // Recalcular recompensas disparadas basándose en el nuevo tiempo
                state.firedSeconds().clear();
                for (int firedSecond : saved.firedSeconds) {
                    // Convertir el segundo disparado al nuevo sistema
                    double firedPercentage = (double) firedSecond / saved.originalDurationSeconds;
                    int newFiredSecond = (int) Math.round(firedPercentage * newDurationSeconds);

                    // Solo agregar si el nuevo segundo disparado es menor o igual al tiempo actual
                    if (newFiredSecond <= newElapsedSeconds) {
                        state.firedSeconds().add(newFiredSecond);
                    }
                }

                if (plugin.getConfig().getBoolean("general.debug", false)) {
                    plugin.getLogger().info("[DEBUG] Restored adjusted progress for player " + player.getName() +
                        ": " + saved.elapsedSeconds + "s -> " + newElapsedSeconds + "s" +
                        " (duration " + saved.originalDurationSeconds + "s -> " + newDurationSeconds + "s)" +
                        ", percentage=" + String.format("%.2f", saved.progressPercentage * 100) + "%");
                }
            }
        }

        // Limpiar datos guardados
        savedProgress.clear();
    }

    /**
     * Función auxiliar para encontrar una región en la posición del jugador
     */
    private Region findRegionAt(Player player) {
        for (Region region : plugin.regions().candidates(
            player.getWorld().getName(),
            player.getLocation().getBlockX(),
            player.getLocation().getBlockZ())) {

            if (region.contains(
                player.getWorld().getName(),
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ())) {
                return region;
            }
        }
        return null;
    }

    /**
     * Clase para almacenar el progreso guardado de un jugador
     */
    private static class SavedProgress {
        final String regionName;
        final int elapsedTicks;
        final int elapsedSeconds;
        final int originalDurationSeconds;
        final double progressPercentage;
        final java.util.Set<Integer> firedSeconds;

        SavedProgress(String regionName, int elapsedTicks, int elapsedSeconds,
                     int originalDurationSeconds, double progressPercentage,
                     java.util.Set<Integer> firedSeconds) {
            this.regionName = regionName;
            this.elapsedTicks = elapsedTicks;
            this.elapsedSeconds = elapsedSeconds;
            this.originalDurationSeconds = originalDurationSeconds;
            this.progressPercentage = progressPercentage;
            this.firedSeconds = new java.util.HashSet<>(firedSeconds);
        }
    }
}
