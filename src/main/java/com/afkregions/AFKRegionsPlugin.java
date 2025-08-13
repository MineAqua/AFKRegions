
package com.afkregions;

import com.afkregions.papi.AFKRegionsExpansion;
import com.afkregions.region.RegionManager;
import com.afkregions.runtime.PlayerTracker;
import com.afkregions.util.Messages;
import com.afkregions.version.LegacyAdapter;
import com.afkregions.version.ModernAdapter;
import com.afkregions.version.VersionAdapter;
import com.afkregions.cmd.AFKRegionsCommand;
import com.afkregions.selection.SelectionManager;
import com.afkregions.selection.SelectionListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class AFKRegionsPlugin extends JavaPlugin {
  private static AFKRegionsPlugin instance;
  private Messages messages;
  private RegionManager regionManager;
  private PlayerTracker tracker;
  private VersionAdapter adapter;
  private SelectionManager selections;
  private java.io.File regionsFile;

  public static AFKRegionsPlugin get() { return instance; }
  public Messages messages() { return messages; }
  public RegionManager regions() { return regionManager; }
  public PlayerTracker tracker() { return tracker; }
  public VersionAdapter adapter() { return adapter; }
  public SelectionManager selections() { return selections; }

  @Override public void onEnable() {
    instance = this;
    saveDefaultConfig();
    saveResource("lang.yml", false);
    saveResource("regions.yml", false);
    this.regionsFile = new java.io.File(getDataFolder(), "regions.yml");

    this.messages = new Messages(this);
    this.regionManager = new RegionManager(this);
    this.adapter = hasClass("org.bukkit.boss.BossBar") ? new ModernAdapter(this) : new LegacyAdapter(this);
    this.tracker = new PlayerTracker(this);

    this.selections = new SelectionManager();
    getServer().getPluginManager().registerEvents(new SelectionListener(this, selections), this);

    AFKRegionsCommand cmd = new AFKRegionsCommand(this);
    getCommand("afkregions").setExecutor(cmd);
    getCommand("afkregions").setTabCompleter(cmd);

    if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
      new AFKRegionsExpansion(this).register();
      getLogger().info("PlaceholderAPI detected — registering placeholders.");
    }

    tracker.start();
    getLogger().info("AFKRegions enabled.");
  }

  @Override public void onDisable() {
    if (tracker != null) tracker.stop();
    if (adapter != null) adapter.shutdown();
  }

  public void reloadAll() {
    reloadConfig();
    messages.reload();
    regions().reload();
    tracker.reloadSettings();
  }

  public void saveRegionsToFile() {
    try {
      org.bukkit.configuration.file.YamlConfiguration out =
              new org.bukkit.configuration.file.YamlConfiguration();

      // Necesitamos iterar todas las regiones conocidas por RegionManager.
      // Si tu RegionManager aún no expone "all()", abajo te digo qué agregar ahí.
      for (com.afkregions.model.Region r : regions().all()) {
        String base = r.name + ".";
        out.set(base + "world", r.world);

        out.set(base + "pos1.x", r.minX);
        out.set(base + "pos1.y", r.minY);
        out.set(base + "pos1.z", r.minZ);

        out.set(base + "pos2.x", r.maxX);
        out.set(base + "pos2.y", r.maxY);
        out.set(base + "pos2.z", r.maxZ);

        out.set(base + "duration_seconds", r.durationSeconds);

        java.util.List<java.util.Map<String,Object>> list = new java.util.ArrayList<>();
        for (com.afkregions.model.RegionReward rr : r.rewards) {
          java.util.Map<String,Object> m = new java.util.LinkedHashMap<>();
          if (rr.always) m.put("always", true);
          if (rr.atSeconds >= 0) m.put("at", rr.atSeconds);
          if (rr.chance < 1.0) m.put("chance", rr.chance);
          m.put("command", rr.command);
          list.add(m);
        }
        out.set(base + "rewards", list);
      }

      out.save(regionsFile);
      getLogger().info("Saving regions.yml (" + regionsFile.getAbsolutePath() + ")");
    } catch (Exception e) {
      getLogger().warning("Cannot save regions.yml: " + e.getMessage());
    }
  }


  private boolean hasClass(String cn) {
    try { Class.forName(cn); return true; } catch (ClassNotFoundException e) { return false; }
  }
}
