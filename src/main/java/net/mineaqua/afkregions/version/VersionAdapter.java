
package net.mineaqua.afkregions.version;

import net.mineaqua.afkregions.model.Region;
import org.bukkit.entity.Player;

public interface VersionAdapter {
    void onEnter(Player player, Region region);

    void onExit(Player player, Region region);

    void updateProgress(Player player, Region region, int elapsedSeconds, double playerrogress);

    void clearUI(Player player);

    void shutdown();
}
