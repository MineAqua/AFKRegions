
package com.afkregions.region;

import com.afkregions.AFKRegionsPlugin;
import com.afkregions.model.Region;

import java.io.File;
import java.util.*;

public class RegionManager {
    private final AFKRegionsPlugin plugin;
    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final ChunkIndex index = new ChunkIndex();
    private File regionsFile;
    private org.bukkit.configuration.file.FileConfiguration regionsCfg;

    public RegionManager(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        // Asegúrate de tener estos campos en la clase:
        // private File regionsFile;
        // private FileConfiguration regionsCfg;

        // 1) (Re)cargar regions.yml
        this.regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            plugin.saveResource("regions.yml", false);
        }
        this.regionsCfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(regionsFile);

        // 2) Reconstruir en memoria
        regions.clear();
        index.clear();

        // 👇 AQUÍ está el cambio importante: leemos de regionsCfg, NO de plugin.getConfig()
        org.bukkit.configuration.ConfigurationSection rs = regionsCfg.getConfigurationSection("regions");
        if (rs != null) {
            for (String key : rs.getKeys(false)) {
                Region r = Region.fromConfig(key, rs.getConfigurationSection(key));
                regions.put(key.toLowerCase(java.util.Locale.ROOT), r);
                index.add(r);
            }
        }
    }

    private void saveRegions() {
        try {
            regionsCfg.save(regionsFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Cannot save regions.yml: " + e.getMessage());
        }
    }


    public Collection<Region> all() {
        return Collections.unmodifiableCollection(regions.values());
    }

    public Region get(String name) {
        return regions.get(name.toLowerCase(Locale.ROOT));
    }

    public void add(Region r) {
        regions.put(r.name.toLowerCase(java.util.Locale.ROOT), r);
        index.add(r);

        String base = "regions." + r.name + ".";
        regionsCfg.set(base + "world", r.world);

        java.util.Map<String, Integer> p1 = new java.util.LinkedHashMap<>();
        p1.put("x", r.minX);
        p1.put("y", r.minY);
        p1.put("z", r.minZ);
        java.util.Map<String, Integer> p2 = new java.util.LinkedHashMap<>();
        p2.put("x", r.maxX);
        p2.put("y", r.maxY);
        p2.put("z", r.maxZ);

        regionsCfg.set(base + "pos1", p1);
        regionsCfg.set(base + "pos2", p2);
        regionsCfg.set(base + "duration_seconds", r.durationSeconds);

        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (com.afkregions.model.RegionReward rr : r.rewards) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            if (rr.always) m.put("always", true);
            else m.put("at", rr.atSeconds);
            if (rr.chance != 1.0) m.put("chance", rr.chance);
            m.put("command", rr.command);
            list.add(m);
        }
        regionsCfg.set(base + "rewards", list);

        saveRegions(); // ← persistir regions.yml
    }

    public void persist(Region r) {
        String base = "regions." + r.name + ".";

        regionsCfg.set(base + "world", r.world);

        java.util.Map<String, Integer> p1 = new java.util.LinkedHashMap<>();
        p1.put("x", r.minX);
        p1.put("y", r.minY);
        p1.put("z", r.minZ);
        java.util.Map<String, Integer> p2 = new java.util.LinkedHashMap<>();
        p2.put("x", r.maxX);
        p2.put("y", r.maxY);
        p2.put("z", r.maxZ);

        regionsCfg.set(base + "pos1", p1);
        regionsCfg.set(base + "pos2", p2);
        regionsCfg.set(base + "duration_seconds", r.durationSeconds);

        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        for (com.afkregions.model.RegionReward rr : r.rewards) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            if (rr.always) m.put("always", true);
            else m.put("at", rr.atSeconds);
            if (rr.chance != 1.0) m.put("chance", rr.chance);
            m.put("command", rr.command);
            list.add(m);
        }
        regionsCfg.set(base + "rewards", list);

        saveRegions();
    }


    public boolean remove(String name) {
        Region r = regions.remove(name.toLowerCase(java.util.Locale.ROOT));
        if (r == null) return false;

        regionsCfg.set("regions." + r.name, null);
        saveRegions(); // persistir
        reload();      // reconstruir mapas/índice en memoria
        return true;
    }

    public List<Region> candidates(String world, int bx, int bz) {
        return index.candidates(world, bx, bz);
    }
}
