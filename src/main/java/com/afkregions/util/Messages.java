
package com.afkregions.util;

import com.afkregions.AFKRegionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class Messages {

  private final AFKRegionsPlugin plugin;
  private File file;
  private YamlConfiguration cfg;

  public Messages(AFKRegionsPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    try {
      if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
      file = new File(plugin.getDataFolder(), "lang.yml");

      // Carga el archivo del disco (si falta, copia el de dentro del jar)
      if (!file.exists()) plugin.saveResource("lang.yml", false);
      cfg = YamlConfiguration.loadConfiguration(file);

      // Mezcla con defaults del jar (por si falta alguna key)
      java.io.InputStream in = plugin.getResource("lang.yml");
      if (in != null) {
        java.io.InputStreamReader reader =
                new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8);
        YamlConfiguration def = YamlConfiguration.loadConfiguration(reader);
        cfg.setDefaults(def);
        cfg.options().copyDefaults(true);     // ← importante: copia defaults faltantes
      }

      // Guardar de vuelta con defaults copiados (rellena claves ausentes)
      cfg.save(file);

      // Sanity log (opcional): avisa si alguna clave crítica falta
      String[] must = new String[] {
              "prefix",
              "invalid_args", "region_not_found", "no_perm",
              "created_region", "removed_region",
              "entered_region", "left_region",
              "added_reward", "reward_given",
              "list_header", "list_item",
              "enter_title.title", "enter_title.subtitle",
              "exit_title.title",  "exit_title.subtitle",
              "progress_title.title", "progress_title.subtitle"
      };
      for (String k : must) {
        if (getRaw(k) == null) {
          plugin.getLogger().warning("[lang] Falta la clave: " + k);
        }
      }
    } catch (Exception e) {
      plugin.getLogger().severe("No se pudo cargar lang.yml: " + e.getMessage());
      cfg = new YamlConfiguration();
    }
  }

  // helper interno que NO colorea ni agrega prefijo: devuelve null si no existe
  private String getRaw(String key) {
    return cfg == null ? null : cfg.getString(key);
  }


  /** Devuelve el prefijo coloreado (o vacío). */
  public String prefix() {
    String p = cfg.getString("prefix", "");
    return color(p);
  }

  /** Mensaje con prefijo + color. Si falta la clave, devuelve '[key]'. */
  public String msg(String key) {
    String raw = cfg.getString(key);
    if (raw == null) {
      plugin.getLogger().warning("[lang] clave no encontrada: " + key);
      return prefix() + ChatColor.RED + "[" + key + "]";
    }
    return prefix() + color(raw);
  }

  /** Texto sin prefijo, coloreado. Soporta claves anidadas (p.ej. progress_title.title). */
  public String raw(String key) {
    String v = cfg.getString(key);
    if (v == null) return "";
    return color(v);
  }

  public Messages.TitleSpec title(String sectionKey) {
    org.bukkit.configuration.ConfigurationSection s = cfg.getConfigurationSection(sectionKey);
    if (s == null) {
      plugin.getLogger().warning("[lang] Falta la sección: " + sectionKey);
      return new TitleSpec("", "", 5, 30, 10);
    }
    String title = s.getString("title", "");
    String subtitle = s.getString("subtitle", "");
    int fi = s.getInt("fadeIn", 5);
    int st = s.getInt("stay", 30);
    int fo = s.getInt("fadeOut", 10);
    return new TitleSpec(color(title), color(subtitle), fi, st, fo);
  }

  /** Traductor de &-codes a § y soporte de colores. */
  public static String color(String s) {
    return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
  }

  public static class TitleSpec {
    public final String title;
    public final String subtitle;
    public final int fi, st, fo;
    public TitleSpec(String title, String subtitle, int fi, int st, int fo) {
      this.title = title;
      this.subtitle = subtitle;
      this.fi = fi;
      this.st = st;
      this.fo = fo;
    }
  }
}