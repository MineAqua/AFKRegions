package net.mineaqua.afkregions.runtime;

import me.clip.placeholderapi.PlaceholderAPI;
import net.mineaqua.afkregions.AFKRegionsPlugin;
import net.mineaqua.afkregions.model.Region;
import net.mineaqua.afkregions.model.RegionReward;
import net.mineaqua.afkregions.version.VersionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class PlayerTracker implements Listener {
    private final Map<UUID, State> states = new HashMap<>();
    private final AFKRegionsPlugin plugin;
    private final VersionAdapter adapter;

    private int tickInterval;
    private int regionCheckInterval; // Nueva variable para el intervalo de verificación de regiones
    private int rewardTaskId = -1;
    private int regionCheckTaskId = -1; // Nueva tarea para verificar regiones

    private boolean resetOnExit;
    private boolean debug;
    private boolean rewardMsgEnabled;

    private void debug(String text) {
        if (debug) plugin.getLogger().info("[DEBUG] " + text);
    }

    public void reloadSettings() {
        this.tickInterval = Math.max(1, plugin.getConfig().getInt("detection.tick_interval", 20));
        // Nuevo: intervalo para verificar regiones (por defecto cada 10 ticks = 0.5 segundos)
        this.regionCheckInterval = Math.max(1, plugin.getConfig().getInt("detection.region_check_interval", 10));
        this.resetOnExit = plugin.getConfig().getBoolean("general.reset_on_exit", true);
        this.rewardMsgEnabled = plugin.getConfig().getBoolean("rewards.message_enabled", true);
    }

    public PlayerTracker(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
        this.adapter = plugin.adapter();

        reloadSettings();

        this.debug = plugin.getConfig().getBoolean("general.debug", false);
    }

    public void start() {
        if (rewardTaskId != -1 || regionCheckTaskId != -1) return;

        // Tarea para manejar recompensas y progreso
        rewardTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                tickRewards();
            }
        }.runTaskTimer(plugin, tickInterval, tickInterval).getTaskId();

        // Nueva tarea optimizada para verificar regiones
        regionCheckTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                checkPlayerRegions();
            }
        }.runTaskTimer(plugin, regionCheckInterval, regionCheckInterval).getTaskId();
    }

    public void stop() {
        if (rewardTaskId != -1) {
            Bukkit.getScheduler().cancelTask(rewardTaskId);
            rewardTaskId = -1;
        }

        if (regionCheckTaskId != -1) {
            Bukkit.getScheduler().cancelTask(regionCheckTaskId);
            regionCheckTaskId = -1;
        }

        for (State state : states.values()) {
            adapter.clearUI(state.player());
        }
        states.clear();
    }

    // Nueva función optimizada para verificar regiones de todos los jugadores
    private void checkPlayerRegions() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;

            Region currentRegion = findRegionAt(player);

            State state = states.computeIfAbsent(player.getUniqueId(), k -> new State(player));
            Region previousRegion = state.region();

            if (!Objects.equals(previousRegion, currentRegion)) {
                handleRegionChange(player, state, previousRegion, currentRegion);
            }
        }
    }

    // Función optimizada para encontrar una región en una posición específica
    private Region findRegionAt(Player player) {
        for (Region region : plugin.regions().candidates(player.getWorld().getName(), player.getLocation().getBlockX(), player.getLocation().getBlockZ())) {
            if (region.contains(player.getWorld().getName(), player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
                return region;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerExit(PlayerQuitEvent event) {
        if (!plugin.statistics().isEnabled()) {
            return;
        }

        Player player = event.getPlayer();
        State state = states.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        Region region = state.region();
        if (region == null) {
            return;
        }

        int secondsInRegion = state.elapsed() / 20;
        if (secondsInRegion > 0) {
            plugin.statistics().addAFKTime(player.getUniqueId(), player.getName(), secondsInRegion);
            if (debug) debug("Saved " + secondsInRegion + " AFK seconds for player " + player.getName());
        }

        states.remove(player.getUniqueId());
    }

    // Función separada para manejar cambios de región
    private void handleRegionChange(Player player, State state, Region previousRegion, Region currentRegion) {
        if (previousRegion != null) {
            // Guardar el tiempo AFK antes de salir de la región
            if (plugin.statistics().isEnabled()) {
                int secondsInRegion = state.elapsed() / 20;
                if (secondsInRegion > 0) {
                    plugin.statistics().addAFKTime(player.getUniqueId(), player.getName(), secondsInRegion);
                    if (debug) debug("Saved " + secondsInRegion + " AFK seconds for player " + player.getName());
                }
            }

            adapter.onExit(player, previousRegion);
            if (resetOnExit) {
                state.elapsed(0);
            }

            state.firedSeconds().clear();
            player.sendMessage(plugin.messages().msg("left_region").replace("{region}", previousRegion.name()));

            if (debug) debug("Player " + player.getName() + " left region: " + previousRegion.name());
        }

        state.region(currentRegion);

        if (currentRegion != null) {
            state.elapsed(0);
            state.firedSeconds().clear();
            adapter.onEnter(player, currentRegion);
            player.sendMessage(plugin.messages().msg("entered_region").replace("{region}", currentRegion.name()));

            if (debug) debug("Player " + player.getName() + " entered region: " + currentRegion.name());
        }
    }

    // Función renombrada para claridad - solo maneja recompensas y progreso
    private void tickRewards() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            State state = states.get(player.getUniqueId());
            if (state == null || state.region() == null) {
                continue;
            }

            int totalTicks = state.region().durationSeconds() * 20;

            int prev = state.elapsed();
            int prevSec = prev / 20;

            state.elapsed((state.elapsed() + tickInterval) % totalTicks);
            int curr = state.elapsed();
            int currSec = curr / 20;

            boolean wrapped = curr < prev;
            if (wrapped) {
                state.firedSeconds().clear();

                adapter.onEnter(player, state.region());
                adapter.updateProgress(player, state.region(), 0, 0.0);
            }

            if (debug) debug("tick " + player.getName() + " region=" + state.region().name() +
                    " prev=" + prev + " (" + prevSec + "s) curr=" + curr + " (" + currSec + "s) wrapped=" + wrapped);

            if (debug) debug("  rewards=" + state.region().rewards().size());

            Set<Integer> crossedAts = new LinkedHashSet<>();
            for (RegionReward reward : state.region().rewards()) {
                if (reward.atSeconds() < 0) {
                    continue;
                }

                boolean crossed;
                if (!wrapped) {
                    crossed = (reward.atSeconds() > prevSec && reward.atSeconds() <= currSec);
                } else {
                    crossed = (reward.atSeconds() > prevSec) || (reward.atSeconds() <= currSec);
                }

                if (crossed) {
                    crossedAts.add(reward.atSeconds());
                }
            }
            if (debug) debug("  crossed=" + crossedAts);

            for (int atSec : crossedAts) {
                if (state.firedSeconds().contains(atSec)) continue;

                for (RegionReward reward : state.region().rewards()) {
                    if (reward.atSeconds() == atSec && reward.roll()) {
                        String cmd = reward.command().replace("{player}", player.getName());

                        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                            try {
                                cmd = PlaceholderAPI.setPlaceholders(player, cmd);
                            } catch (Throwable ignored) {
                            }
                        }

                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                        if (debug) debug("  fired at=" + atSec + "s cmd=" + cmd);
                        if (rewardMsgEnabled) {
                            player.sendMessage(plugin.messages().msg("reward_given").replace("{command}", cmd));
                        }
                    }
                }

                state.firedSeconds.add(atSec);
            }

            double progress = Math.max(0.0, Math.min(1.0, (double) currSec / (double) state.region().durationSeconds()));
            adapter.updateProgress(player, state.region(), currSec, progress);
        }
    }

    // Remover el EventHandler para PlayerMoveEvent ya que ahora usamos una tarea programada
    // @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    // public void onMove(PlayerMoveEvent event) { ... }

    public void refreshRegionRef(String regionName) {
        for (State state : states.values()) {
            if (state != null && state.region() != null && state.region().name().equalsIgnoreCase(regionName)) {
                Region region = plugin.regions().get(regionName);

                if (region != null) {
                    state.region(region);
                    state.firedSeconds().clear();
                    state.elapsed(0);
                }
            }
        }
    }

    public boolean isAfk(UUID id) {
        State state = states.get(id);

        return state != null && state.region != null;
    }

    public String regionName(UUID id) {
        State state = states.get(id);

        return state != null && state.region() != null ? state.region().name() : "";
    }

    public int elapsedSeconds(UUID id) {
        State state = states.get(id);

        return state != null && state.region() != null ? (state.elapsed() / 20) : 0;
    }

    public int durationSeconds(UUID id) {
        State state = states.get(id);

        return (state != null && state.region() != null) ? state.region().durationSeconds() : 0;
    }

    public int progressPercent(UUID id) {
        int seconds = durationSeconds(id);
        int elapsed = elapsedSeconds(id);

        if (seconds <= 0) {
            return 0;
        }

        return (int) Math.round(100.0 * elapsed / seconds);
    }

    public int timeLeftSeconds(UUID id) {
        int seconds = durationSeconds(id);
        int elapsed = elapsedSeconds(id);

        if (seconds <= 0) {
            return 0;
        }

        return Math.max(0, seconds - elapsed);
    }

    public String progressBar(UUID id) {
        return progressBar(id, 20, '|', '.');
    }

    public String progressBar(UUID id, int segments, char filled, char empty) {
        int percentage = progressPercent(id);
        int fill = (int) Math.round((segments * percentage) / 100.0);

        StringBuilder builder = new StringBuilder(segments);
        for (int i = 0; i < segments; i++) {
            builder.append(i < fill ? filled : empty);
        }

        return builder.toString();
    }

    private static class State {
        private final Player player;
        private Region region;
        private int elapsed;

        private final Set<Integer> firedSeconds = new HashSet<>();

        State(Player player) {
            this.player = player;
            this.region = null;
            this.elapsed = 0;
        }

        public Player player() {
            return player;
        }

        public Region region() {
            return region;
        }

        public void region(Region region) {
            this.region = region;
        }

        public int elapsed() {
            return elapsed;
        }

        public void elapsed(int elapsed) {
            this.elapsed = elapsed;
        }

        public Set<Integer> firedSeconds() {
            return firedSeconds;
        }
    }
}
