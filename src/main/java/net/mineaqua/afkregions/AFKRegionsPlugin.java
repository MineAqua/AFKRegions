
package net.mineaqua.afkregions;

import net.mineaqua.afkregions.papi.AFKRegionsExpansion;
import net.mineaqua.afkregions.region.RegionManager;
import net.mineaqua.afkregions.runtime.PlayerTracker;
import net.mineaqua.afkregions.util.Messages;
import net.mineaqua.afkregions.version.LegacyAdapter;
import net.mineaqua.afkregions.version.ModernAdapter;
import net.mineaqua.afkregions.version.VersionAdapter;
import net.mineaqua.afkregions.cmd.AFKRegionsCommand;
import net.mineaqua.afkregions.selection.SelectionManager;
import net.mineaqua.afkregions.selection.SelectionListener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class AFKRegionsPlugin extends JavaPlugin {
    private static AFKRegionsPlugin instance;

    private Messages messages;

    private RegionManager regionManager;

    private SelectionManager selections;
    private VersionAdapter adapter;

    private PlayerTracker tracker;

    public static AFKRegionsPlugin get() {
        return instance;
    }

    public Messages messages() {
        return messages;
    }

    public RegionManager regions() {
        return regionManager;
    }

    public PlayerTracker tracker() {
        return tracker;
    }

    public VersionAdapter adapter() {
        return adapter;
    }

    public SelectionManager selections() {
        return selections;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("lang.yml", false);
        saveResource("regions.yml", false);

        this.adapter = hasClass("org.bukkit.boss.BossBar") ? new ModernAdapter(this) : new LegacyAdapter(this);

        this.messages = new Messages(this);
        this.messages.reload();

        this.tracker = new PlayerTracker(this);

        this.regionManager = new RegionManager(this);
        this.selections = new SelectionManager();

        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new SelectionListener(selections), this);

        AFKRegionsCommand cmd = new AFKRegionsCommand(this);
        getCommand("afkregions").setExecutor(cmd);
        getCommand("afkregions").setTabCompleter(cmd);

        if (pluginManager.isPluginEnabled("PlaceholderAPI")) {
            new AFKRegionsExpansion(this).register();
            getLogger().info("PlaceholderAPI detected — registering placeholders.");
        }

        tracker.start();
        getLogger().info("AFKRegions enabled.");
    }

    @Override
    public void onDisable() {
        if (tracker != null) {
            tracker.stop();
        }

        if (adapter != null) {
            adapter.shutdown();
        }
    }

    public void reloadAll() {
        reloadConfig();

        messages.reload();
        regionManager.reload();

        tracker.reloadSettings();
    }

    private boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}