
package com.afkregions.cmd;

import com.afkregions.AFKRegionsPlugin;
import com.afkregions.model.Region;
import com.afkregions.selection.SelectionListener;
import com.afkregions.selection.SelectionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public class AFKRegionsCommand implements CommandExecutor, TabCompleter {
  private final AFKRegionsPlugin plugin;

  public AFKRegionsCommand(AFKRegionsPlugin plugin) { this.plugin = plugin; }

  @Override public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      s.sendMessage("§b/afkregions wand §7– Select region (izq=pos1, der=pos2)");
      s.sendMessage("§b/afkregions create <name> <duration_s> §7– Create a region with the selection");
      s.sendMessage("§b/afkregions reward list <region>");
      s.sendMessage("§b/afkregions reward add <region> <percentage%> <time_> <command...>");
      s.sendMessage("§b/afkregions reward remove <region> <index>");
      s.sendMessage("§b/afkregions reload");
      return true;
    }

    if (!s.hasPermission("afkregions.admin")) { s.sendMessage(plugin.messages().msg("no_perm")); return true; }

    String sub = args[0].toLowerCase(Locale.ROOT);

    // wand
    if (sub.equals("wand")) {
      if (!(s instanceof Player)) { s.sendMessage("§cJust players."); return true; }
      Player p = (Player)s;
      p.getInventory().addItem(SelectionListener.makeWand());
      p.sendMessage("§aYou got the wand. §7Left clic = Pos1, right clic= Pos2.");
      return true;
    }

    // create
    if (sub.equals("create")) {
      if (!(s instanceof Player) || args.length < 3) { s.sendMessage(plugin.messages().msg("invalid_args")); return true; }
      Player p = (Player)s; String name = args[1]; int dur = parseInt(args[2], 600);
      SelectionManager.Sel sel = plugin.selections().peek(p.getUniqueId());
      if (sel == null || sel.p1 == null || sel.p2 == null) {
        s.sendMessage("§cYou must select Pos1 and Pos2 first using §e/"+label+" wand§c.");
        return true;
      }
      if (!sel.p1.getWorld().equals(sel.p2.getWorld()) || !sel.p1.getWorld().equals(p.getWorld())) {
        s.sendMessage("§cPos1 and Pos2 must be in the same world as you.");
        return true;
      }
      Region r = new Region(name, p.getWorld().getName(), sel.p1.getBlockX(), sel.p1.getBlockY(), sel.p1.getBlockZ(),
                            sel.p2.getBlockX(), sel.p2.getBlockY(), sel.p2.getBlockZ(), dur, Collections.emptyList());
      plugin.regions().add(r);
      s.sendMessage(plugin.messages().msg("created_region").replace("{region}", name));
      plugin.selections().clear(p.getUniqueId());
      return true;
    }

// =========================
// NUEVO ÁRBOL: /afkregions reward <list|add|remove>
// =========================
    if (sub.equals("reward")) {
      if (!s.hasPermission("afkregions.admin")) {
        s.sendMessage(plugin.messages().msg("no_perm"));
        return true;
      }
      if (args.length < 2) {
        s.sendMessage("§eUso: /afkregions reward <list|add|remove> ...");
        return true;
      }

      String action = args[1].toLowerCase(java.util.Locale.ROOT);

      // ---- LIST ----
      if (action.equals("list")) {
        if (args.length < 3) { s.sendMessage("§cUse: /afkregions reward list <region>"); return true; }
        String regionName = args[2];
        com.afkregions.model.Region r = plugin.regions().get(regionName);
        if (r == null) { s.sendMessage(plugin.messages().msg("region_not_found").replace("{region}", regionName)); return true; }

        java.util.List<com.afkregions.model.RegionReward> L = r.rewards;
        if (L == null || L.isEmpty()) { s.sendMessage("§7There is no rewards in §e" + r.name + "§7."); return true; }
        s.sendMessage("§aRewards in §e" + r.name + "§a:");
        for (int i = 0; i < L.size(); i++) {
          com.afkregions.model.RegionReward rr = L.get(i);
          String when = rr.always ? "always" : (rr.atSeconds + "s");
          int chancePct = (int)Math.round(rr.chance * 100.0);
          s.sendMessage(" §8" + (i+1) + "§7) at=" + when + " §7chance=§e" + chancePct + "% §7cmd=§f" + rr.command);
        }
        return true;
      }

      // ---- ADD ----
      if (action.equals("add")) {
        if (args.length < 6) {
          s.sendMessage("§cUse: /afkregions reward add <region> <percentage%> <time_s> <command...>");
          return true;
        }
        String regionName = args[2];
        com.afkregions.model.Region r = plugin.regions().get(regionName);
        if (r == null) { s.sendMessage(plugin.messages().msg("region_not_found").replace("{region}", regionName)); return true; }

        // porcentaje%
        String percentTok = args[3];
        if (!percentTok.endsWith("%")) { s.sendMessage("§cThe percentage must finish with % (ex: 100%)."); return true; }
        double chance;
        try {
          chance = Math.max(0, Math.min(1, Double.parseDouble(percentTok.substring(0, percentTok.length()-1)) / 100.0));
        } catch (Exception ex) {
          s.sendMessage("§cInvalid percentage."); return true;
        }

        // tiempo en segundos: 10s
        String timeTok = args[4].toLowerCase(java.util.Locale.ROOT);
        if (!timeTok.endsWith("s")) { s.sendMessage("§cThe time must ends with 's' (seconds), ex: 10s."); return true; }
        int at;
        try { at = Integer.parseInt(timeTok.substring(0, timeTok.length()-1)); }
        catch (Exception ex) { s.sendMessage("§cInvalid time."); return true; }
        if (at < 0) { s.sendMessage("§cThe time must be greather than 0 (seconds)."); return true; }

        // comando
        String command = join(args, 5);
//        if (command.startsWith("bc ")) command = "broadcast " + command.substring(3);
//        if (command.equalsIgnoreCase("bc")) { s.sendMessage("§cEspecifica el texto de broadcast luego de 'bc'."); return true; }

        // agregar en sitio (lista mutable)
        r.rewards.add(new com.afkregions.model.RegionReward(false, at, chance, command));

        // persistir y aplicar en caliente
        plugin.regions().persist(r);
        plugin.tracker().refreshRegionRef(r.name);

        s.sendMessage(plugin.messages().msg("added_reward")
                .replace("{region}", r.name)
                .replace("{spec}", "at="+at+"s, chance="+(int)Math.round(chance*100)+"%, cmd="+command));
        return true;
      }

      // ---- REMOVE ----
      if (action.equals("remove")) {
        if (args.length < 4) { s.sendMessage("§cUse: /afkregions reward remove <region> <index>"); return true; }
        String regionName = args[2];
        com.afkregions.model.Region r = plugin.regions().get(regionName);
        if (r == null) { s.sendMessage(plugin.messages().msg("region_not_found").replace("{region}", regionName)); return true; }

        int index1;
        try { index1 = Integer.parseInt(args[3]); } catch (Exception e) {
          s.sendMessage("§cInvalid index."); return true;
        }
        int idx = index1 - 1;
        if (idx < 0 || idx >= r.rewards.size()) {
          s.sendMessage("§cIndex out of bounds. Use §e/afkregions reward list " + r.name);
          return true;
        }

        com.afkregions.model.RegionReward removed = r.rewards.remove(idx);
        plugin.regions().persist(r);
        plugin.tracker().refreshRegionRef(r.name);

        String when = removed.always ? "always" : (removed.atSeconds + "s");
        int chancePct = (int)Math.round(removed.chance * 100.0);
        s.sendMessage("§aDeleted reward §8#" + index1 + " §7(at=" + when + ", chance=" + chancePct + "%).");
        return true;
      }

      s.sendMessage("§cInvalid subcommand. Use: list, add, remove.");
      return true;
    }


    // list
    if (sub.equals("list")) {
      Collection<Region> all = plugin.regions().all();
      s.sendMessage(plugin.messages().raw("list_header").replace("{count}", String.valueOf(all.size())));
      for (Region r : all) {
        s.sendMessage(plugin.messages().raw("list_item")
          .replace("{name}", r.name)
          .replace("{world}", r.world)
          .replace("{min}", r.minX+","+r.minY+","+r.minZ)
          .replace("{max}", r.maxX+","+r.maxY+","+r.maxZ)
          .replace("{duration}", String.valueOf(r.durationSeconds))
          .replace("{rewards}", String.valueOf(r.rewards.size())));
      }
      return true;
    }

    // remove
    if (sub.equals("remove")) {
      if (args.length < 2) { s.sendMessage(plugin.messages().msg("invalid_args")); return true; }
      String name = args[1];
      boolean ok = plugin.regions().remove(name);
      s.sendMessage(ok ? plugin.messages().msg("removed_region").replace("{region}", name)
                       : plugin.messages().msg("region_not_found").replace("{region}", name));
      return true;
    }

    // reload
    if (sub.equals("reload")) { plugin.reloadAll(); s.sendMessage(plugin.messages().msg("reloaded")); return true; }

    s.sendMessage(plugin.messages().msg("invalid_args"));
    return true;
  }

  @Override
  public java.util.List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
    java.util.List<String> out = new java.util.ArrayList<>();
    if (!s.hasPermission("afkregions.admin")) return out;

    java.util.function.BiPredicate<String,String> starts = (opt, pref) ->
            pref == null || pref.isEmpty() || opt.toLowerCase(java.util.Locale.ROOT).startsWith(pref.toLowerCase(java.util.Locale.ROOT));

    if (args.length == 1) {
      String p = args[0];
      for (String o : new String[]{"wand","create","reward","reload","remove","list"})
        if (starts.test(o, p)) out.add(o);
      return out;
    }

    // /afkregions reward ...
    if (args.length >= 2 && args[0].equalsIgnoreCase("reward")) {
      if (args.length == 2) {
        String p = args[1];
        for (String o : new String[]{"list","add","remove"}) if (starts.test(o, p)) out.add(o);
        return out;
      }

      // /afkregions reward list <region>
      if (args.length == 3 && args[1].equalsIgnoreCase("list")) {
        String p = args[2];
        for (com.afkregions.model.Region r : plugin.regions().all())
          if (starts.test(r.name, p)) out.add(r.name);
        return out;
      }

      // /afkregions reward add <region> <porcentaje%> <tiempo_s> <comando...>
      if (args[1].equalsIgnoreCase("add")) {
        if (args.length == 3) {
          String p = args[2];
          for (com.afkregions.model.Region r : plugin.regions().all())
            if (starts.test(r.name, p)) out.add(r.name);
          return out;
        }
        if (args.length == 4) {
          String p = args[3];
          for (String o : new String[]{"100%","75%","50%","25%","10%"})
            if (starts.test(o, p)) out.add(o);
          return out;
        }
        if (args.length == 5) {
          String p = args[4];
          for (String o : new String[]{"10s","30s","60s","300s"})
            if (starts.test(o, p)) out.add(o);
          return out;
        }
        if (args.length >= 6) {
          String p = args[5];
          for (String o : new String[]{"bc","broadcast","say","give","lp","eco"})
            if (starts.test(o, p)) out.add(o);
          return out;
        }
      }

      // /afkregions reward remove <region> <indice>
      if (args[1].equalsIgnoreCase("remove")) {
        if (args.length == 3) {
          String p = args[2];
          for (com.afkregions.model.Region r : plugin.regions().all())
            if (starts.test(r.name, p)) out.add(r.name);
          return out;
        }
        if (args.length == 4) {
          String regionName = args[2];
          com.afkregions.model.Region r = plugin.regions().get(regionName);
          if (r != null && r.rewards != null && !r.rewards.isEmpty()) {
            String p = args[3];
            for (int i = 1; i <= r.rewards.size(); i++) {
              String idx = String.valueOf(i);
              if (starts.test(idx, p)) out.add(idx);
            }
          }
          return out;
        }
      }

      return out;
    }

    // otros subcomandos tuyos (wand/create/remove/list/reload) mantienen su autocompletado previo
    return out;
  }


  // utils
  private static int parseInt(String s, int d){ try { return Integer.parseInt(s); } catch(Exception e){ return d; } }
  private static String join(String[] a,int from){ StringBuilder sb=new StringBuilder(); for(int i=from;i<a.length;i++){ if(i>from) sb.append(' '); sb.append(a[i]); } return sb.toString(); }
}
