
package net.mineaqua.afkregions.selection;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectionManager {
    public static class Selection {
        private Location position1;
        private Location position2;

        public Location position1() {
            return position1;
        }

        public void position1(Location position1) {
            this.position1 = position1;
        }

        public Location position2() {
            return position2;
        }

        public void position2(Location position2) {
            this.position2 = position2;
        }
    }

    private final Map<UUID, Selection> map = new HashMap<>();

    public Selection get(UUID id) {
        return map.computeIfAbsent(id, (ignored) -> new Selection());
    }

    public Selection peek(UUID id) {
        return map.get(id);
    }

    public void clear(UUID id) {
        map.remove(id);
    }
}
