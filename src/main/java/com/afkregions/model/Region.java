
package com.afkregions.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class Region {
  public final String name;
  public final String world;
  public final int minX, minY, minZ, maxX, maxY, maxZ;
  public final int durationSeconds;
  public List<RegionReward> rewards;

  public Region(String name, String world, int minX, int minY, int minZ,
                int maxX, int maxY, int maxZ, int durationSeconds,
                List<RegionReward> rewards) {
    this.name = name;
    this.world = world;
    this.minX = Math.min(minX, maxX);
    this.minY = Math.min(minY, maxY);
    this.minZ = Math.min(minZ, maxZ);
    this.maxX = Math.max(minX, maxX);
    this.maxY = Math.max(minY, maxY);
    this.maxZ = Math.max(minZ, maxZ);
    this.durationSeconds = durationSeconds;
    // clave: lista SIEMPRE mutable
    this.rewards = new java.util.ArrayList<>(
            rewards != null ? rewards : java.util.Collections.emptyList()
    );
  }

  public boolean contains(String world, int bx, int by, int bz) {
    if (!Objects.equals(this.world, world)) return false;
    return bx>=minX && bx<=maxX && by>=minY && by<=maxY && bz>=minZ && bz<=maxZ;
  }

  @SuppressWarnings("unchecked")
  public static Region fromConfig(String name, ConfigurationSection sec) {
    String world = sec.getString("world");
    ConfigurationSection p1 = sec.getConfigurationSection("pos1");
    ConfigurationSection p2 = sec.getConfigurationSection("pos2");
    int d = sec.getInt("duration_seconds", 600);
    List<RegionReward> list = new ArrayList<>();
    for (Map<?,?> m : sec.getMapList("rewards")) {
      boolean always = m.get("always") instanceof Boolean && (Boolean)m.get("always");
      int at = m.get("at") instanceof Number ? ((Number)m.get("at")).intValue() : -1;
      double chance = m.get("chance") instanceof Number ? ((Number)m.get("chance")).doubleValue() : 1.0;
      String cmd = String.valueOf(m.get("command"));
      list.add(new RegionReward(always, at, Math.max(0, Math.min(1, chance)), cmd));
    }
    return new Region(name, world,
      p1.getInt("x"), p1.getInt("y"), p1.getInt("z"),
      p2.getInt("x"), p2.getInt("y"), p2.getInt("z"), d, list);
  }
}
