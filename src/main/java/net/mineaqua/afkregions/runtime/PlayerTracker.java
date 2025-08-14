
package net.mineaqua.afkregions.runtime;

import me.clip.placeholderapi.PlaceholderAPI;
import net.mineaqua.afkregions.AFKRegionsPlugin;
import net.mineaqua.afkregions.model.Region;
import net.mineaqua.afkregions.model.RegionReward;
import net.mineaqua.afkregions.version.VersionAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
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
    private int taskId = -1;

    private boolean resetOnExit;
    private boolean debug;

    private void debug(String text) {
        if (debug) plugin.getLogger().info("[DEBUG] " + text);
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

        for (State state : states.values()) {
            adapter.clearUI(state.player());
        }
        states.clear();
    }

    private void tick() {
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
                        player.sendMessage(plugin.messages().msg("reward_given").replace("{command}", cmd));
                    }
                }

                state.firedSeconds.add(atSec);
            }

            double progress = Math.max(0.0, Math.min(1.0, (double) currSec / (double) state.region().durationSeconds()));
            adapter.updateProgress(player, state.region(), currSec, progress);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()
                && event.getFrom().getWorld().getUID().equals(event.getTo().getWorld().getUID())
        ) return;

        onBlockChange(event.getPlayer(), event.getTo());
    }

    private void onBlockChange(Player player, Location location) {
        int blockX = location.getBlockX(), blockY = location.getBlockY(), blockZ = location.getBlockZ();
        String world = location.getWorld().getName();
        Region current = null;

        for (Region region : plugin.regions().candidates(world, blockX, blockZ)) {
            if (region.contains(world, blockX, blockY, blockZ)) {
                current = region;
                break;
            }
        }

        State state = states.computeIfAbsent(player.getUniqueId(), k -> new State(player));
        Region previous = state.region();

        if (!Objects.equals(previous, current)) {
            if (previous != null) {
                adapter.onExit(player, previous);
                if (resetOnExit) {
                    state.elapsed(0);
                }

                state.firedSeconds().clear();
                player.sendMessage(plugin.messages().msg("left_region").replace("{region}", previous.name()));
            }

            state.region(current);

            if (current != null) {
                state.elapsed(0);
                state.firedSeconds().clear();
                adapter.onEnter(player, current);
                player.sendMessage(plugin.messages().msg("entered_region").replace("{region}", current.name()));
            }
        }
    }

    public String placeholder(UUID id, String key) {
        State state = states.get(id);
        if (state == null || state.region() == null) {
            return "";
        }
        int elapsedSec = state.elapsed() / 20;

        switch (key) {
            case "region":
                return state.region().name();
            case "elapsed":
                return String.valueOf(elapsedSec);

            case "duration":
                return String.valueOf(durationSeconds(id));
            case "progress":
            case "progress_percent":
                return String.valueOf(progressPercent(id));
            case "progress_bar":
                return progressBar(id);
            case "time_left":
                return String.valueOf(timeLeftSeconds(id));

            default:
                return "";
        }
    }

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
