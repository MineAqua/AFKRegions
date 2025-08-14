
package com.afkregions.runtime;

import com.afkregions.AFKRegionsPlugin;
import com.afkregions.model.Region;
import com.afkregions.model.RegionReward;
import com.afkregions.version.VersionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class PlayerTracker implements Listener {
    private final AFKRegionsPlugin plugin;
    private final VersionAdapter adapter;
    private final Map<UUID, State> states = new HashMap<>();
    private int taskId = -1;
    private int tickInterval;
    private boolean resetOnExit;
    private boolean debug;

    private void debug(String s) {
        if (debug) plugin.getLogger().info("[DEBUG] " + s);
    }

    public PlayerTracker(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
        this.adapter = plugin.adapter();
        reloadSettings();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.debug = plugin.getConfig().getBoolean("settings.debug", false);
    }

    public void reloadSettings() {
        this.tickInterval = Math.max(1, plugin.getConfig().getInt("settings.tick_interval", 20));
        this.resetOnExit = plugin.getConfig().getBoolean("settings.reset_on_exit", true);
    }

    public void start() {
        if (taskId != -1) return;
        taskId = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, tickInterval, tickInterval).getTaskId();
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
        for (State s : states.values()) adapter.clearUI(s.player);
        states.clear();
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            State st = states.get(p.getUniqueId());
            if (st == null || st.region == null) continue;

            int totalTicks = st.region.durationSeconds * 20;

            int prev = st.elapsed;
            int prevSec = prev / 20;

            st.elapsed = (st.elapsed + tickInterval) % totalTicks; // ← SIEMPRE modular
            int curr = st.elapsed;
            int currSec = curr / 20;

            boolean wrapped = curr < prev; // se reinició el ciclo
            if (wrapped) {
                st.firedSeconds.clear();

                // 🔹 Volvemos a mostrar el título como si entraras de nuevo
                adapter.onEnter(p, st.region);

                // 🔹 Reiniciamos la bossbar al 0% para el nuevo ciclo
                adapter.updateProgress(p, st.region, 0, 0.0);
            }

            if (debug) debug("tick " + p.getName() + " region=" + st.region.name +
                    " prev=" + prev + " (" + prevSec + "s) curr=" + curr + " (" + currSec + "s) wrapped=" + wrapped);

// Disparo robusto: cruza de prevSec→currSec (con o sin wrap) soportando múltiples rewards en el MISMO segundo
            if (debug) debug("  rewards=" + st.region.rewards.size());

// 1) calcula todos los segundos "at" cruzados en este tick
            java.util.Set<Integer> crossedAts = new java.util.LinkedHashSet<>();
            for (RegionReward rr : st.region.rewards) {
                if (rr.atSeconds < 0) continue;

                boolean crossed;
                if (!wrapped) {
                    // avance normal: prevSec < at <= currSec
                    crossed = (rr.atSeconds > prevSec && rr.atSeconds <= currSec);
                } else {
                    // hubo wrap: (at > prevSec) o (at <= currSec)
                    crossed = (rr.atSeconds > prevSec) || (rr.atSeconds <= currSec);
                }
                if (crossed) crossedAts.add(rr.atSeconds);
            }
            if (debug) debug("  crossed=" + crossedAts);

            // 2) por cada segundo cruzado que aún no estaba “fired”, ejecuta TODAS las rewards de ese segundo
            for (int atSec : crossedAts) {
                if (st.firedSeconds.contains(atSec)) continue;

                for (RegionReward rr : st.region.rewards) {
                    if (rr.atSeconds == atSec && rr.roll()) {
                        String cmd = rr.command.replace("{player}", p.getName());
                        // Parseo de PlaceholderAPI (si está instalado)
                        if (org.bukkit.Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                            try {
                                cmd = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, cmd);
                            } catch (Throwable ignored) {
                            }
                        }

                        org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), cmd);
                        if (debug) debug("  fired at=" + atSec + "s cmd=" + cmd);
                        p.sendMessage(plugin.messages().msg("reward_given").replace("{command}", cmd));
                    }
                }
                // marca ese segundo como consumido para no repetir en este ciclo
                st.firedSeconds.add(atSec);
            }

            double progress = Math.max(0.0, Math.min(1.0, (double) currSec / (double) st.region.durationSeconds));
            adapter.updateProgress(p, st.region, currSec, progress);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location f = e.getFrom(), t = e.getTo();
        if (t == null) return;
        if (f.getWorld() != t.getWorld()) {
            onBlockChange(e.getPlayer(), t);
            return;
        }
        if (f.getBlockX() == t.getBlockX() && f.getBlockY() == t.getBlockY() && f.getBlockZ() == t.getBlockZ()) return;
        onBlockChange(e.getPlayer(), t);
    }

    private void onBlockChange(Player p, Location to) {
        int bx = to.getBlockX(), by = to.getBlockY(), bz = to.getBlockZ();
        String world = to.getWorld().getName();
        Region current = null;
        for (Region r : plugin.regions().candidates(world, bx, bz)) {
            if (r.contains(world, bx, by, bz)) {
                current = r;
                break;
            }
        }

        State st = states.computeIfAbsent(p.getUniqueId(), k -> new State(p));
        Region previous = st.region;
        if (!Objects.equals(previous, current)) {
            if (previous != null) {
                adapter.onExit(p, previous);
                if (resetOnExit) st.elapsed = 0;
                st.firedSeconds.clear(); // ← NUEVO
                p.sendMessage(plugin.messages().msg("left_region").replace("{region}", previous.name));
            }

            st.region = current;
            if (current != null) {
                st.elapsed = 0;           // ← NUEVO
                st.firedSeconds.clear();  // ← NUEVO
                adapter.onEnter(p, current);
                p.sendMessage(plugin.messages().msg("entered_region").replace("{region}", current.name));
            }
        }
    }

    public String placeholder(UUID id, String key) {
        State st = states.get(id);
        if (st == null || st.region == null) return "";
        int elapsedSec = st.elapsed / 20;

        switch (key) {
            case "region":
                return st.region.name;
            case "elapsed":
                return String.valueOf(elapsedSec);

            // NUEVOS que pide PAPI (por si alguien llama vía fallback):
            case "duration":
                return String.valueOf(durationSeconds(id));
            case "progress":
                return String.valueOf(progressPercent(id));
            case "progress_bar":
                return progressBar(id);
            case "time_left":
                return String.valueOf(timeLeftSeconds(id));

            // alias opcional (puedes borrarlo si no lo usas)
            case "progress_percent":
                return String.valueOf(progressPercent(id));

            default:
                return "";
        }
    }

    public void refreshRegionRef(String regionName) {
        for (State st : states.values()) {
            if (st != null && st.region != null && st.region.name.equalsIgnoreCase(regionName)) {
                com.afkregions.model.Region nr = plugin.regions().get(regionName);
                if (nr != null) {
                    st.region = nr;           // usar Region actualizada (con nuevas rewards)
                    st.firedSeconds.clear();  // reiniciar disparos por seguridad
                    st.elapsed = 0;           // opcional: reinicia el contador visual/interno
                }
            }
        }
    }


    public boolean isAfk(java.util.UUID id) {
        State st = states.get(id);
        return st != null && st.region != null;
    }

    public String regionName(java.util.UUID id) {
        State st = states.get(id);
        return st != null && st.region != null ? st.region.name : "";
    }

    public int elapsedSeconds(java.util.UUID id) {
        State st = states.get(id);
        return st != null && st.region != null ? (st.elapsed / 20) : 0;
    }

    // === NUEVO: duración total de la región actual (segundos)
    public int durationSeconds(java.util.UUID id) {
        State st = states.get(id);
        return (st != null && st.region != null) ? st.region.durationSeconds : 0;
    }

    // === NUEVO: porcentaje de progreso 0..100
    public int progressPercent(java.util.UUID id) {
        int e = elapsedSeconds(id);
        int m = durationSeconds(id);
        if (m <= 0) return 0;
        return (int) Math.round(100.0 * e / m);
    }

    // === NUEVO: tiempo restante (segundos) hasta completar el ciclo
    public int timeLeftSeconds(java.util.UUID id) {
        int m = durationSeconds(id);
        int e = elapsedSeconds(id);
        if (m <= 0) return 0;
        return Math.max(0, m - e);
    }

    // === NUEVO: barra de progreso en texto (20 segmentos por defecto)
    public String progressBar(java.util.UUID id) {
        return progressBar(id, 20, '|', '.');
    }

    public String progressBar(java.util.UUID id, int segments, char filled, char empty) {
        int pct = progressPercent(id);
        int fill = (int) Math.round((segments * pct) / 100.0);
        StringBuilder sb = new StringBuilder(segments);
        for (int i = 0; i < segments; i++) sb.append(i < fill ? filled : empty);
        return sb.toString();
    }

    private static class State {
        final Player player;
        Region region;
        int elapsed;
        java.util.Set<Integer> firedSeconds = new java.util.HashSet<>();

        State(Player p) {
            this.player = p;
            this.region = null;
            this.elapsed = 0;
        }
    }
}
