
package net.mineaqua.afkregions.region;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import net.mineaqua.afkregions.model.Region;
import net.mineaqua.afkregions.model.RegionReward;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RegionManager {
    private final AFKRegionsPlugin plugin;

    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final ChunkIndex index = new ChunkIndex();

    private FileConfiguration regionsConfig;
    private File regionsFile;

    public RegionManager(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.regionsFile = new File(plugin.getDataFolder(), "regions.yml");
        if (!regionsFile.exists()) {
            plugin.saveResource("regions.yml", false);
        }

        this.regionsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(regionsFile);

        regions.clear();
        index.clear();

        ConfigurationSection section = regionsConfig.getConfigurationSection("regions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Region region = Region.fromConfig(key, section.getConfigurationSection(key));
                regions.put(key.toLowerCase(Locale.ROOT), region);
                index.add(region);
            }
        }
    }

    private void saveRegions() {
        try {
            regionsConfig.save(regionsFile);
        } catch (Exception e) {
            plugin.getLogger().severe("No se pudo guardar el regions.yml: " + e.getMessage());
        }
    }
    
    public Collection<Region> all() {
        return Collections.unmodifiableCollection(regions.values());
    }

    public Region get(String name) {
        return regions.get(name.toLowerCase(Locale.ROOT));
    }

    public void add(Region region) {
        regions.put(region.name().toLowerCase(Locale.ROOT), region);
        index.add(region);

        persist(region);
    }

    public void persist(Region region) {
        String base = "regions." + region.name() + ".";
        regionsConfig.set(base + "world", region.world());

        Map<String, Integer> pos1 = new LinkedHashMap<>();
        pos1.put("x", region.minX());
        pos1.put("y", region.minY());
        pos1.put("z", region.minZ());

        Map<String, Integer> pos2 = new LinkedHashMap<>();
        pos2.put("x", region.maxX());
        pos2.put("y", region.maxY());
        pos2.put("z", region.maxZ());

        regionsConfig.set(base + "pos1", pos1);
        regionsConfig.set(base + "pos2", pos2);
        regionsConfig.set(base + "duration_seconds", region.durationSeconds());

        List<Map<String, Object>> list = new ArrayList<>();
        for (RegionReward reward : region.rewards()) {
            Map<String, Object> map = new LinkedHashMap<>();
            if (reward.always()) {
                map.put("always", true);
            } else {
                map.put("at", reward.atSeconds());
            }

            if (reward.chance() != 1.0) {
                map.put("chance", reward.chance());
            }

            map.put("command", reward.command());
            list.add(map);
        }

        regionsConfig.set(base + "rewards", list);

        saveRegions();
    }

    public boolean remove(String name) {
        Region r = regions.remove(name.toLowerCase(Locale.ROOT));
        if (r == null) {
            return false;
        }

        regionsConfig.set("regions." + r.name(), null);
        saveRegions();
        reload();

        return true;
    }

    public List<Region> candidates(String world, int bx, int bz) {
        return index.candidates(world, bx, bz);
    }
}
