package net.mineaqua.afkregions.cmd;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import net.mineaqua.afkregions.model.Region;
import net.mineaqua.afkregions.model.RegionReward;
import net.mineaqua.afkregions.selection.SelectionListener;
import net.mineaqua.afkregions.selection.SelectionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

public class AFKRegionsCommand implements CommandExecutor, TabCompleter {
    private final AFKRegionsPlugin plugin;

    public AFKRegionsCommand(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(plugin.messages().msg("help_wand"));
            sender.sendMessage(plugin.messages().msg("help_create"));
            sender.sendMessage(plugin.messages().msg("help_reward_list"));
            sender.sendMessage(plugin.messages().msg("help_reward_add"));
            sender.sendMessage(plugin.messages().msg("help_reward_remove"));
            sender.sendMessage(plugin.messages().msg("help_reload"));

            return true;
        }

        if (!sender.hasPermission("afkregions.admin")) {
            sender.sendMessage(plugin.messages().msg("no_perm"));
            return true;
        }

        String sub = args[0].toLowerCase(java.util.Locale.ROOT);

        // ── wand ─────────────────────────────────────────────────────────────
        if (sub.equals("wand")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.messages().msg("wand_only_players"));
                return true;
            }
            Player p = (Player) sender;
            p.getInventory().addItem(SelectionListener.makeWand());
            p.sendMessage(plugin.messages().msg("wand_given"));
            return true;
        }

        // ── create ───────────────────────────────────────────────────────────
        if (sub.equals("create")) {
            if (!(sender instanceof Player) || args.length < 3) {
                sender.sendMessage(plugin.messages().msg("invalid_args"));
                return true;
            }
            Player p = (Player) sender;
            String name = args[1];
            int dur = parseInt(args[2], 600);

            SelectionManager.Selection selection = plugin.selections().peek(p.getUniqueId());
            if (selection == null || selection.position1() == null || selection.position2() == null) {
                sender.sendMessage(plugin.messages().msg("selection_needed").replace("{label}", label));
                return true;
            }
            if (!selection.position1().getWorld().equals(selection.position2().getWorld()) || !selection.position1().getWorld().equals(p.getWorld())) {
                sender.sendMessage(plugin.messages().msg("selection_world_mismatch"));
                return true;
            }

            Region r = new Region(
                    name,
                    p.getWorld().getName(),
                    selection.position1().getBlockX(), selection.position1().getBlockY(), selection.position1().getBlockZ(),
                    selection.position2().getBlockX(), selection.position2().getBlockY(), selection.position2().getBlockZ(),
                    dur,
                    new java.util.ArrayList<>() // lista mutable
            );
            plugin.regions().add(r);
            sender.sendMessage(plugin.messages().msg("created_region").replace("{region}", name));
            plugin.selections().clear(p.getUniqueId());
            return true;
        }

        // ── reward <list|add|remove> ─────────────────────────────────────────
        if (sub.equals("reward")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.messages().msg("reward_usage_main"));
                return true;
            }
            String action = args[1].toLowerCase(java.util.Locale.ROOT);

            // list
            if (action.equals("list")) {
                if (args.length < 3) {
                    sender.sendMessage(plugin.messages().msg("reward_list_usage"));
                    return true;
                }
                String regionName = args[2];
                Region r = plugin.regions().get(regionName);
                if (r == null) {
                    sender.sendMessage(plugin.messages().msg("region_not_found").replace("{region}", regionName));
                    return true;
                }

                java.util.List<RegionReward> L = r.rewards();
                if (L == null || L.isEmpty()) {
                    sender.sendMessage(plugin.messages().msg("reward_list_empty").replace("{region}", r.name()));
                    return true;
                }
                sender.sendMessage(plugin.messages().msg("rewards_header").replace("{region}", r.name()));
                for (int i = 0; i < L.size(); i++) {
                    RegionReward rr = L.get(i);
                    String when = rr.always() ? "always" : (rr.atSeconds() + "s");
                    int chancePct = (int) Math.round(rr.chance() * 100.0);
                    sender.sendMessage(plugin.messages().msg("rewards_item")
                            .replace("{index}", String.valueOf(i + 1))
                            .replace("{at}", when)
                            .replace("{chance}", String.valueOf(chancePct))
                            .replace("{command}", rr.command()));
                }
                return true;
            }

            // add
            if (action.equals("add")) {
                if (args.length < 6) {
                    sender.sendMessage(plugin.messages().msg("reward_add_usage"));
                    return true;
                }
                String regionName = args[2];
                Region r = plugin.regions().get(regionName);
                if (r == null) {
                    sender.sendMessage(plugin.messages().msg("region_not_found").replace("{region}", regionName));
                    return true;
                }

                // porcentaje%
                String percentTok = args[3];
                if (!percentTok.endsWith("%")) {
                    sender.sendMessage(plugin.messages().msg("percentage_end_percent"));
                    return true;
                }
                double chance;
                try {
                    chance = Math.max(0, Math.min(1, Double.parseDouble(percentTok.substring(0, percentTok.length() - 1)) / 100.0));
                } catch (Exception ex) {
                    sender.sendMessage(plugin.messages().msg("percentage_invalid"));
                    return true;
                }

                // tiempo en segundos: 10s
                String timeTok = args[4].toLowerCase(java.util.Locale.ROOT);
                if (!timeTok.endsWith("s")) {
                    sender.sendMessage(plugin.messages().msg("time_end_s"));
                    return true;
                }
                int at;
                try {
                    at = Integer.parseInt(timeTok.substring(0, timeTok.length() - 1));
                } catch (Exception ex) {
                    sender.sendMessage(plugin.messages().msg("time_invalid"));
                    return true;
                }
                if (at < 0) {
                    sender.sendMessage(plugin.messages().msg("time_must_be_ge_zero"));
                    return true;
                }

                // comando
                String command = join(args, 5);

                r.rewards().add(new RegionReward(false, at, chance, command));
                plugin.regions().persist(r);
                plugin.tracker().refreshRegionRef(r.name());

                sender.sendMessage(plugin.messages().msg("added_reward")
                        .replace("{region}", r.name())
                        .replace("{spec}", "at=" + at + "s, chance=" + (int) Math.round(chance * 100) + "%, cmd=" + command));
                return true;
            }

            // remove
            if (action.equals("remove")) {
                if (args.length < 4) {
                    sender.sendMessage(plugin.messages().msg("reward_remove_usage"));
                    return true;
                }
                String regionName = args[2];
                Region r = plugin.regions().get(regionName);
                if (r == null) {
                    sender.sendMessage(plugin.messages().msg("region_not_found").replace("{region}", regionName));
                    return true;
                }

                int index1;
                try {
                    index1 = Integer.parseInt(args[3]);
                } catch (Exception e) {
                    sender.sendMessage(plugin.messages().msg("index_invalid"));
                    return true;
                }
                int idx = index1 - 1;
                if (idx < 0 || idx >= r.rewards().size()) {
                    sender.sendMessage(plugin.messages().msg("index_oob").replace("{region}", r.name()));
                    return true;
                }

                RegionReward removed = r.rewards().remove(idx);
                plugin.regions().persist(r);
                plugin.tracker().refreshRegionRef(r.name());

                String when = removed.always() ? "always" : (removed.atSeconds() + "s");
                int chancePct = (int) Math.round(removed.chance() * 100.0);
                sender.sendMessage(plugin.messages().msg("removed_reward")
                        .replace("{index}", String.valueOf(index1))
                        .replace("{at}", when)
                        .replace("{chance}", String.valueOf(chancePct)));
                return true;
            }

            sender.sendMessage(plugin.messages().msg("subcommand_invalid"));
            return true;
        }

        // ── list ─────────────────────────────────────────────────────────────
        if (sub.equals("list")) {
            Collection<Region> all = plugin.regions().all();
            sender.sendMessage(plugin.messages().raw("list_header").replace("{count}", String.valueOf(all.size())));
            for (Region r : all) {
                sender.sendMessage(plugin.messages().raw("list_item")
                        .replace("{name}", r.name())
                        .replace("{world}", r.world())
                        .replace("{min}", r.minX() + "," + r.minY() + "," + r.minZ())
                        .replace("{max}", r.maxX() + "," + r.maxY() + "," + r.maxZ())
                        .replace("{duration}", String.valueOf(r.durationSeconds()))
                        .replace("{rewards}", String.valueOf(r.rewards().size())));
            }
            return true;
        }

        // ── remove ───────────────────────────────────────────────────────────
        if (sub.equals("remove")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.messages().msg("invalid_args"));
                return true;
            }
            String name = args[1];
            boolean ok = plugin.regions().remove(name);
            sender.sendMessage(ok
                    ? plugin.messages().msg("removed_region").replace("{region}", name)
                    : plugin.messages().msg("region_not_found").replace("{region}", name));
            return true;
        }

        // ── reload ───────────────────────────────────────────────────────────
        if (sub.equals("reload")) {
            plugin.reloadAll();
            sender.sendMessage(plugin.messages().msg("reloaded"));
            return true;
        }

        sender.sendMessage(plugin.messages().msg("invalid_args"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("afkregions.admin")) return out;

        BiPredicate<String, String> starts = (opt, pref) ->
                pref == null || pref.isEmpty() || opt.toLowerCase(Locale.ROOT).startsWith(pref.toLowerCase(Locale.ROOT));

        if (args.length == 1) {
            String p = args[0];
            for (String o : new String[]{"wand", "create", "reward", "reload", "remove", "list"})
                if (starts.test(o, p)) out.add(o);
            return out;
        }

        // /afkregions reward ...
        if (args.length >= 2 && args[0].equalsIgnoreCase("reward")) {
            if (args.length == 2) {
                String p = args[1];
                for (String o : new String[]{"list", "add", "remove"}) if (starts.test(o, p)) out.add(o);
                return out;
            }

            if (args.length == 3 && args[1].equalsIgnoreCase("list")) {
                String p = args[2];
                for (Region r : plugin.regions().all())
                    if (starts.test(r.name(), p)) out.add(r.name());
                return out;
            }

            if (args[1].equalsIgnoreCase("add")) {
                if (args.length == 3) {
                    String p = args[2];
                    for (Region r : plugin.regions().all())
                        if (starts.test(r.name(), p)) out.add(r.name());
                    return out;
                }
                if (args.length == 4) {
                    String p = args[3];
                    for (String o : new String[]{"100%", "75%", "50%", "25%", "10%"})
                        if (starts.test(o, p)) out.add(o);
                    return out;
                }
                if (args.length == 5) {
                    String p = args[4];
                    for (String o : new String[]{"10s", "30s", "60s", "300s"})
                        if (starts.test(o, p)) out.add(o);
                    return out;
                }
                if (args.length >= 6) {
                    String p = args[5];
                    for (String o : new String[]{"bc", "broadcast", "say", "give", "lp", "eco"})
                        if (starts.test(o, p)) out.add(o);
                    return out;
                }
            }

            if (args[1].equalsIgnoreCase("remove")) {
                if (args.length == 3) {
                    String p = args[2];
                    for (Region r : plugin.regions().all())
                        if (starts.test(r.name(), p)) out.add(r.name());
                    return out;
                }
                if (args.length == 4) {
                    String regionName = args[2];
                    Region r = plugin.regions().get(regionName);
                    if (r != null && r.rewards() != null && !r.rewards().isEmpty()) {
                        String p = args[3];
                        for (int i = 1; i <= r.rewards().size(); i++) {
                            String idx = String.valueOf(i);
                            if (starts.test(idx, p)) out.add(idx);
                        }
                    }
                    return out;
                }
            }
            return out;
        }

        return out;
    }

    private int parseInt(String rawNumber, int def) {
        try {
            return Integer.parseInt(rawNumber);
        } catch (Exception e) {
            return def;
        }
    }

    private String join(String[] array, int from) {
        StringBuilder builder = new StringBuilder();

        for (int i = from; i < array.length; i++) {
            if (i > from) {
                builder.append(' ');
            }

            builder.append(array[i]);
        }

        return builder.toString();
    }
}
