
package com.afkregions.selection;

import com.afkregions.AFKRegionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SelectionListener implements Listener {
    public static final String WAND_NAME = ChatColor.translateAlternateColorCodes('&', "&6AFKRegions &eWand");
    private final AFKRegionsPlugin plugin;
    private final SelectionManager selections;

    public SelectionListener(AFKRegionsPlugin plugin, SelectionManager selections) {
        this.plugin = plugin;
        this.selections = selections;
    }

    public static boolean isWand(ItemStack it) {
        if (it == null) return false;
        final String t = it.getType().name();
        if (!t.equals("GOLDEN_AXE") && !t.equals("GOLD_AXE")) return false; // compatible 1.8 y 1.13+
        ItemMeta m = it.getItemMeta();
        return m != null && WAND_NAME.equals(m.getDisplayName());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(PlayerInteractEvent e) {
        if (!isWand(e.getItem())) return;
        if (e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        switch (e.getAction()) {
            case LEFT_CLICK_BLOCK: {
                selections.get(p.getUniqueId()).p1 = e.getClickedBlock().getLocation();
                p.sendMessage("§aPos1 §7= §f" + fmt(e.getClickedBlock().getLocation()));
                e.setCancelled(true);
                break;
            }
            case RIGHT_CLICK_BLOCK: {
                selections.get(p.getUniqueId()).p2 = e.getClickedBlock().getLocation();
                p.sendMessage("§aPos2 §7= §f" + fmt(e.getClickedBlock().getLocation()));
                e.setCancelled(true);
                break;
            }
            default:
                break;
        }
    }

    private static String fmt(org.bukkit.Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    public static ItemStack makeWand() {
        ItemStack it;
        try {
            it = new ItemStack(Material.valueOf("GOLDEN_AXE"));
        } catch (IllegalArgumentException ex) {
            it = new ItemStack(Material.valueOf("GOLD_AXE"));
        }
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(WAND_NAME);
        it.setItemMeta(meta);
        return it;
    }
}
