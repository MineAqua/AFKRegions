
package net.mineaqua.afkregions.selection;

import org.bukkit.ChatColor;
import org.bukkit.Location;
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
    private final SelectionManager selections;

    public SelectionListener(SelectionManager selections) {
        this.selections = selections;
    }

    public static boolean isWand(ItemStack stack) {
        if (stack == null) {
            return false;
        }

        String type = stack.getType().name();
        if (!type.equals("GOLDEN_AXE") && !type.equals("GOLD_AXE")) {
            return false; // compatible 1.8 y 1.13+
        }

        ItemMeta meta = stack.getItemMeta();
        return meta != null && WAND_NAME.equals(meta.getDisplayName());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onClick(PlayerInteractEvent event) {
        if (!isWand(event.getItem())) {
            return;
        }

        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();

        switch (event.getAction()) {
            case LEFT_CLICK_BLOCK: {
                selections.get(player.getUniqueId()).position1(event.getClickedBlock().getLocation());
                player.sendMessage("§aPos1 §7= §f" + format(event.getClickedBlock().getLocation()));
                event.setCancelled(true);
                break;
            }

            case RIGHT_CLICK_BLOCK: {
                selections.get(player.getUniqueId()).position2(event.getClickedBlock().getLocation());
                player.sendMessage("§aPos2 §7= §f" + format(event.getClickedBlock().getLocation()));
                event.setCancelled(true);
                break;
            }
        }
    }

    private static String format(Location location) {
        return location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    public static ItemStack makeWand() {
        ItemStack stack;

        try {
            stack = new ItemStack(Material.valueOf("GOLDEN_AXE"));
        } catch (IllegalArgumentException ex) {
            stack = new ItemStack(Material.valueOf("GOLD_AXE"));
        }

        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(WAND_NAME);
        stack.setItemMeta(meta);

        return stack;
    }
}
