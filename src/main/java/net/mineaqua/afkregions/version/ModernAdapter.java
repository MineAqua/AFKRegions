package net.mineaqua.afkregions.version;

import me.clip.placeholderapi.PlaceholderAPI;
import net.mineaqua.afkregions.AFKRegionsPlugin;
import net.mineaqua.afkregions.model.Region;
import net.mineaqua.afkregions.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ModernAdapter implements VersionAdapter {
    private final AFKRegionsPlugin plugin;

    private final boolean titlesEnabled;
    private final int titleEvery;

    private final boolean bossbarEnabled;
    private final String barTitleFormat;
    private final BarColor barColor;
    private final BarStyle barStyle;

    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> lastTitleSecond = new HashMap<>();

    public ModernAdapter(AFKRegionsPlugin plugin) {
        this.plugin = plugin;

        FileConfiguration config = plugin.getConfig();
        this.titlesEnabled = config.getBoolean("ui.titles_enabled", true);
        this.titleEvery = Math.max(0, config.getInt("ui.title_every_seconds", 0));

        this.bossbarEnabled = config.getBoolean("ui.bossbar_enabled", true);
        this.barTitleFormat = config.getString("ui.bossbar_title", "{region} {elapsed}/{max} {progress%}%");
        this.barColor = parseBarColor(config.getString("ui.bossbar_color", "BLUE"));
        this.barStyle = parseBarStyle(config.getString("ui.bossbar_style", "SEGMENTED_10"));
    }

    @Override
    public void onEnter(Player player, Region region) {
        if (bossbarEnabled) {
            BossBar bar = bars.computeIfAbsent(player.getUniqueId(), id -> Bukkit.createBossBar("", barColor, barStyle));
            bar.addPlayer(player);
            bar.setVisible(true);

            updateProgress(player, region, 0, 0.0);
        }

        if (titlesEnabled) {
            handlePlayerTitles(player, region, "enter_title");
        }
    }

    @Override
    public void onExit(Player player, Region region) {
        if (bossbarEnabled) {
            BossBar bar = bars.get(player.getUniqueId());

            if (bar != null) {
                bar.removePlayer(player);
            }
        }

        if (titlesEnabled) {
            handlePlayerTitles(player, region, "exit_title");
        }
    }

    private void handlePlayerTitles(Player player, Region region, String messageKey) {
        lastTitleSecond.remove(player.getUniqueId());

        try {
            player.resetTitle();
        } catch (Throwable ignored) {
        }

        Messages.TitleData titleData = plugin.messages().title(messageKey);
        String title = Messages.color(applyPAPI(player, titleData.title().replace("{region}", region.name())));
        String subtitle = Messages.color(applyPAPI(player, titleData.subtitle().replace("{region}", region.name())));

        player.sendTitle(title, subtitle, titleData.fadeIn(), titleData.stay(), titleData.fadeOut());
    }

    @Override
    public void updateProgress(Player player, Region region, int elapsedSeconds, double progress) {
        if (titlesEnabled && titleEvery > 0) {
            Integer last = lastTitleSecond.get(player.getUniqueId());
            if (last == null || (elapsedSeconds - last) >= titleEvery) {
                lastTitleSecond.put(player.getUniqueId(), elapsedSeconds);

                Messages.TitleData titleData = plugin.messages().title("progress_title");

                long percentage = Math.round(Math.max(0.0, Math.min(1.0, progress)) * 100);
                String title = titleData.title()
                        .replace("{region}", region.name())
                        .replace("{elapsed}", String.valueOf(elapsedSeconds))
                        .replace("{max}", String.valueOf(region.durationSeconds()))
                        .replace("{progress%}", String.valueOf((int) percentage));

                String subtitle = titleData.subtitle()
                        .replace("{region}", region.name())
                        .replace("{elapsed}", String.valueOf(elapsedSeconds))
                        .replace("{max}", String.valueOf(region.durationSeconds()))
                        .replace("{progress%}", String.valueOf((int) percentage));

                title = Messages.color(applyPAPI(player, title));
                subtitle = Messages.color(applyPAPI(player, subtitle));

                player.sendTitle(title, subtitle, titleData.fadeIn(), titleData.stay(), titleData.fadeOut());
            }
        }

        if (bossbarEnabled) {
            BossBar bar = bars.get(player.getUniqueId());
            if (bar == null) {
                bar = Bukkit.createBossBar("", barColor, barStyle);
                bar.addPlayer(player);
                bar.setVisible(true);

                bars.put(player.getUniqueId(), bar);
            }

            double prog = Math.max(0.0, Math.min(1.0, progress));
            int pct = (int) Math.round(prog * 100.0);

            String title = barTitleFormat
                    .replace("{region}", region.name())
                    .replace("{elapsed}", String.valueOf(elapsedSeconds))
                    .replace("{max}", String.valueOf(region.durationSeconds()))
                    .replace("{progress%}", String.valueOf(pct));

            bar.setTitle(Messages.color(applyPAPI(player, title)));
            bar.setProgress(prog);
            bar.setVisible(true);
        }
    }

    @Override
    public void clearUI(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());

        if (bar != null) {
            bar.removeAll();
        }
    }

    @Override
    public void shutdown() {
        for (BossBar b : bars.values()) {
            b.removeAll();
        }

        bars.clear();
    }

    private static BarColor parseBarColor(String text) {
        try {
            return BarColor.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BarColor.BLUE;
        }
    }

    private static BarStyle parseBarStyle(String text) {
        try {
            return BarStyle.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BarStyle.SOLID;
        }
    }

    private static String applyPAPI(Player player, String text) {
        if (text == null) {
            return "";
        }

        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                return PlaceholderAPI.setPlaceholders(player, text);
            } catch (Throwable ignored) {
            }
        }
        
        return text;
    }
}
