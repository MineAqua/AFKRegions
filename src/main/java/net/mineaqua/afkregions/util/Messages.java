
package net.mineaqua.afkregions.util;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class Messages {

    private final AFKRegionsPlugin plugin;
    private YamlConfiguration config;

    public Messages(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            File file = new File(plugin.getDataFolder(), "lang.yml");

            if (!file.exists()) {
                plugin.saveResource("lang.yml", false);
            }

            config = YamlConfiguration.loadConfiguration(file);

            InputStream inputStream = plugin.getResource("lang.yml");
            if (inputStream != null) {
                InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(reader);

                config.setDefaults(defaultConfig);
                config.options().copyDefaults(true);
            }

            config.save(file);

            String[] importantKeys = new String[]{
                    "prefix",
                    "invalid_args", "region_not_found", "no_perm",
                    "created_region", "removed_region",
                    "entered_region", "left_region",
                    "added_reward", "reward_given",
                    "list_header", "list_item",
                    "enter_title.title", "enter_title.subtitle",
                    "exit_title.title", "exit_title.subtitle",
                    "progress_title.title", "progress_title.subtitle"
            };

            for (String key : importantKeys) {
                if (getRaw(key) == null) {
                    plugin.getLogger().warning("[lang] Falta el mensaje: " + key);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("No se pudo cargar lang.yml: " + e.getMessage());
            config = new YamlConfiguration();
        }
    }

    // helper interno que NO colorea ni agrega prefijo: devuelve null si no existe
    private String getRaw(String key) {
        return config == null ? null : config.getString(key);
    }

    /**
     * Devuelve el prefijo coloreado (o vacío).
     */
    public String prefix() {
        String prefix = config.getString("prefix", "");

        return color(prefix);
    }

    /**
     * Mensaje con prefijo + color. Si falta la clave, devuelve '[key]'.
     */
    public String msg(String key) {
        String raw = config.getString(key);

        if (raw == null) {
            plugin.getLogger().warning("[lang] clave no encontrada: " + key);
            return prefix() + ChatColor.RED + "[" + key + "]";
        }

        return prefix() + color(raw);
    }

    /**
     * Texto sin prefijo, coloreado. Soporta claves anidadas (p.ej. progress_title.title).
     */
    public String raw(String key) {
        String value = config.getString(key);
        if (value == null) {
            return "";
        }

        return color(value);
    }

    public TitleData title(String sectionKey) {
        ConfigurationSection section = config.getConfigurationSection(sectionKey);
        if (section == null) {
            plugin.getLogger().warning("[lang] Falta la sección: " + sectionKey);

            return new TitleData("", "", 5, 30, 10);
        }

        String title = section.getString("title", "");
        String subtitle = section.getString("subtitle", "");
        int fadeIn = section.getInt("fadeIn", 5);
        int stay = section.getInt("stay", 30);
        int fadeOut = section.getInt("fadeOut", 10);

        return new TitleData(color(title), color(subtitle), fadeIn, stay, fadeOut);
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static class TitleData {
        private final String title;
        private final String subtitle;
        private final int fadeIn, stay, fadeOut;

        public TitleData(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
            this.title = title;
            this.subtitle = subtitle;
            this.fadeIn = fadeIn;
            this.stay = stay;
            this.fadeOut = fadeOut;
        }

        public String title() {
            return title;
        }

        public String subtitle() {
            return subtitle;
        }

        public int fadeOut() {
            return fadeOut;
        }

        public int stay() {
            return stay;
        }

        public int fadeIn() {
            return fadeIn;
        }
    }
}