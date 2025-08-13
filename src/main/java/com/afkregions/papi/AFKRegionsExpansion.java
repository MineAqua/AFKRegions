
package com.afkregions.papi;

import com.afkregions.AFKRegionsPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AFKRegionsExpansion extends PlaceholderExpansion {
  private final AFKRegionsPlugin plugin;
  public AFKRegionsExpansion(AFKRegionsPlugin plugin) { this.plugin = plugin; }

  @Override public @NotNull String getIdentifier() { return "afkregions"; }
  @Override public @NotNull String getAuthor() { return "Jaimexo"; }
  @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

  @Override
  public String onPlaceholderRequest(org.bukkit.entity.Player p, @org.jetbrains.annotations.NotNull String params) {
    if (p == null) return "";
    String k = params.toLowerCase();
    java.util.UUID id = p.getUniqueId();

    switch (k) {
      // existentes
      case "is_afk":        return String.valueOf(plugin.tracker().isAfk(id));
      case "region_name":   return plugin.tracker().regionName(id);
      case "time":          return String.valueOf(plugin.tracker().elapsedSeconds(id));  // segundos actuales

      // NUEVOS (funcionando)
      case "duration":      return String.valueOf(plugin.tracker().durationSeconds(id)); // duración total (s)
      case "progress":      return String.valueOf(plugin.tracker().progressPercent(id)); // 0..100
      case "progress_bar":  return plugin.tracker().progressBar(id);                     // barra textual
      case "time_left":     return String.valueOf(plugin.tracker().timeLeftSeconds(id)); // segundos restantes

      default:
        // Fallback a los placeholders “viejos” que mantengas en tracker.placeholder(...)
        return plugin.tracker().placeholder(id, k);
    }
  }


}
