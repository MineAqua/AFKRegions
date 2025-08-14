
package com.afkregions.version;

import com.afkregions.AFKRegionsPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.*;

public class ModernAdapter implements VersionAdapter {
    private final AFKRegionsPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final boolean titlesEnabled;
    private final boolean bossbarEnabled;
    private final String barTitleFmt;
    private final BarColor barColor;
    private final BarStyle barStyle;
    private final Map<UUID, Integer> lastTitleSecond = new HashMap<>();
    private final int titleEvery; // segundos; 0 = desactivado


    public ModernAdapter(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
        this.titlesEnabled = plugin.getConfig().getBoolean("settings.titles_enabled", true);
        this.bossbarEnabled = plugin.getConfig().getBoolean("settings.bossbar_enabled", true);
        this.barTitleFmt = plugin.getConfig().getString("settings.bossbar_title", "{region} {elapsed}/{max} {progress%}%");
        this.barColor = parseColor(plugin.getConfig().getString("settings.bossbar_color", "BLUE"));
        this.barStyle = parseStyle(plugin.getConfig().getString("settings.bossbar_style", "SEGMENTED_10"));
        this.titleEvery = Math.max(0, plugin.getConfig().getInt("settings.title_every_seconds", 0));
    }

    @Override
    public void onEnter(org.bukkit.entity.Player p, com.afkregions.model.Region r) {
        if (bossbarEnabled) {
            org.bukkit.boss.BossBar bar = bars.computeIfAbsent(
                    p.getUniqueId(), id -> org.bukkit.Bukkit.createBossBar("", barColor, barStyle));
            bar.addPlayer(p);
            bar.setVisible(true);
            // inicializa barra visible
            updateProgress(p, r, 0, 0.0);
        }

        if (titlesEnabled) {
            // 🔹 resetea rate-limit de títulos para asegurar que el de “entrada” salga siempre
            lastTitleSecond.remove(p.getUniqueId());
            try {
                p.resetTitle();
            } catch (Throwable ignored) {
            }

            com.afkregions.util.Messages.TitleSpec ts = plugin.messages().title("enter_title");
            String t = com.afkregions.util.Messages.color(applyPAPI(p, ts.title.replace("{region}", r.name)));
            String s = com.afkregions.util.Messages.color(applyPAPI(p, ts.subtitle.replace("{region}", r.name)));
            p.sendTitle(t, s, ts.fi, ts.st, ts.fo);
        }
    }


    @Override
    public void onExit(org.bukkit.entity.Player p, com.afkregions.model.Region r) {
        if (bossbarEnabled) {
            org.bukkit.boss.BossBar bar = bars.get(p.getUniqueId());
            if (bar != null) bar.removePlayer(p);
        }
        if (titlesEnabled) {
            // 🔹 limpia rate-limit y resetea título al salir
            lastTitleSecond.remove(p.getUniqueId());
            try {
                p.resetTitle();
            } catch (Throwable ignored) {
            }

            com.afkregions.util.Messages.TitleSpec ts = plugin.messages().title("exit_title");
            p.sendTitle(
                    com.afkregions.util.Messages.color(ts.title.replace("{region}", r.name)),
                    com.afkregions.util.Messages.color(ts.subtitle.replace("{region}", r.name)),
                    ts.fi, ts.st, ts.fo
            );
        }
    }


    @Override
    public void updateProgress(org.bukkit.entity.Player p, com.afkregions.model.Region r, int elapsedSeconds, double progress) {
// ====== TITLES desde lang.yml (progress_title) + PAPI ======
        if (titlesEnabled && titleEvery > 0) {
            Integer last = lastTitleSecond.get(p.getUniqueId());
            if (last == null || (elapsedSeconds - last) >= titleEvery) {
                lastTitleSecond.put(p.getUniqueId(), elapsedSeconds);

                // lee la sección completa (title, subtitle, fi/st/fo) desde lang.yml
                com.afkregions.util.Messages.TitleSpec ts = plugin.messages().title("progress_title");

                String t = ts.title
                        .replace("{region}", r.name)
                        .replace("{elapsed}", String.valueOf(elapsedSeconds))
                        .replace("{max}", String.valueOf(r.durationSeconds))
                        .replace("{progress%}", String.valueOf((int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 100)));

                String s2 = ts.subtitle
                        .replace("{region}", r.name)
                        .replace("{elapsed}", String.valueOf(elapsedSeconds))
                        .replace("{max}", String.valueOf(r.durationSeconds))
                        .replace("{progress%}", String.valueOf((int) Math.round(Math.max(0.0, Math.min(1.0, progress)) * 100)));

                // PAPI + color
                t = com.afkregions.util.Messages.color(applyPAPI(p, t));
                s2 = com.afkregions.util.Messages.color(applyPAPI(p, s2));

                p.sendTitle(t, s2, ts.fi, ts.st, ts.fo);
            }
        }


        // ====== BOSSBAR (añadir esto) ======
        if (bossbarEnabled) {
            org.bukkit.boss.BossBar bar = bars.get(p.getUniqueId());
            if (bar == null) {
                // crea la barra si no existe todavía
                bar = org.bukkit.Bukkit.createBossBar("", barColor, barStyle);
                bars.put(p.getUniqueId(), bar);
                bar.addPlayer(p);
                bar.setVisible(true);
            }

            // clamp para evitar NaN o valores fuera de rango que "vacían" la barra
            double prog = Math.max(0.0, Math.min(1.0, progress));
            int pct = (int) Math.round(prog * 100.0);

            // usa el formato del config (settings.bossbar_title)
            String title = barTitleFmt
                    .replace("{region}", r.name)
                    .replace("{elapsed}", String.valueOf(elapsedSeconds))
                    .replace("{max}", String.valueOf(r.durationSeconds))
                    .replace("{progress%}", String.valueOf(pct));

            bar.setTitle(com.afkregions.util.Messages.color(applyPAPI(p, title)));
            bar.setProgress(prog);
            bar.setVisible(true); // por si algún otro plugin la oculta
        }
    }


    @Override
    public void clearUI(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    @Override
    public void shutdown() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }

    private static BarColor parseColor(String s) {
        try {
            return BarColor.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BarColor.BLUE;
        }
    }

    private static BarStyle parseStyle(String s) {
        try {
            return BarStyle.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BarStyle.SOLID;
        }
    }

    private static String applyPAPI(Player p, String s) {
        if (s == null) return "";
        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, s);
            } catch (Throwable ignored) {
            }
        }
        return s;
    }
}
