
package com.afkregions.selection;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
  public static class Sel {
    public Location p1;
    public Location p2;
  }
  private final Map<UUID, Sel> map = new HashMap<>();

  public Sel get(UUID id) { return map.computeIfAbsent(id, k -> new Sel()); }
  public Sel peek(UUID id) { return map.get(id); }
  public void clear(UUID id) { map.remove(id); }
}
