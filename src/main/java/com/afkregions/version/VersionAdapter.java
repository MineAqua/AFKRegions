
package com.afkregions.version;

import com.afkregions.model.Region;
import org.bukkit.entity.Player;

public interface VersionAdapter {
  void onEnter(Player p, Region r);
  void onExit(Player p, Region r);
  void updateProgress(Player p, Region r, int elapsedSeconds, double progress0to1);
  void clearUI(Player p);
  void shutdown();
}
