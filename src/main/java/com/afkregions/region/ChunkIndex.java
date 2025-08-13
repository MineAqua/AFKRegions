
package com.afkregions.region;

import com.afkregions.model.Region;

import java.util.*;

/** Índice espacial por chunk (16x16) para minimizar escaneos. */
public class ChunkIndex {
  private final Map<String, Map<Long, List<Region>>> byWorldChunk = new HashMap<>();

  public void clear() { byWorldChunk.clear(); }

  public void add(Region r) {
    Map<Long, List<Region>> map = byWorldChunk.computeIfAbsent(r.world, k -> new HashMap<>());
    int cminX = floorDiv(r.minX, 16), cmaxX = floorDiv(r.maxX, 16);
    int cminZ = floorDiv(r.minZ, 16), cmaxZ = floorDiv(r.maxZ, 16);
    for (int cx=cminX; cx<=cmaxX; cx++) {
      for (int cz=cminZ; cz<=cmaxZ; cz++) {
        long key = key(cx, cz);
        map.computeIfAbsent(key, k->new ArrayList<>()).add(r);
      }
    }
  }

  public List<Region> candidates(String world, int bx, int bz) {
    Map<Long, List<Region>> map = byWorldChunk.get(world);
    if (map == null) return Collections.emptyList();
    long k = key(floorDiv(bx,16), floorDiv(bz,16));
    List<Region> list = map.get(k);
    return list == null ? Collections.emptyList() : list;
  }

  private static int floorDiv(int x, int y) { int r = x / y; if ((x ^ y) < 0 && (r * y != x)) r--; return r; }
  private static long key(int cx, int cz) { return (((long)cx) << 32) ^ (cz & 0xffffffffL); }
}
