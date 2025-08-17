package net.mineaqua.afkregions.papi;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class AFKRegionsExpansion extends PlaceholderExpansion {
    private final AFKRegionsPlugin plugin;

    public AFKRegionsExpansion(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "afkregions";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Jaimexo";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        String key = params.toLowerCase();
        UUID id = player.getUniqueId();

        switch (key) {
            case "is_afk":
                return String.valueOf(plugin.tracker().isAfk(id));
            case "region_name":
                return plugin.tracker().regionName(id);
            case "time":
                return String.valueOf(plugin.tracker().elapsedSeconds(id));  // segundos actuales

            case "duration":
                return String.valueOf(plugin.tracker().durationSeconds(id)); // duración total (s)
            case "progress":
            case "progress_percent":
                return String.valueOf(plugin.tracker().progressPercent(id));
            case "progress_bar":
                return plugin.tracker().progressBar(id);
            case "time_left":
                return String.valueOf(plugin.tracker().timeLeftSeconds(id));

            // Nuevos placeholders para estadísticas
            case "total_afk_seconds":
                return String.valueOf(plugin.statistics().getTotalAFKSeconds(id));
            case "total_afk_time":
                return formatTime(plugin.statistics().getTotalAFKSeconds(id));
            case "total_afk_hours":
                return String.valueOf(plugin.statistics().getTotalAFKSeconds(id) / 3600);
            case "total_afk_minutes":
                return String.valueOf(plugin.statistics().getTotalAFKSeconds(id) / 60);

            default:
                return "";
        }
    }

    private String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        return String.format("%02dh %02dm %02ds", hours, minutes, seconds);
    }


}
