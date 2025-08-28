package net.mineaqua.afkregions.listeners;

import net.mineaqua.afkregions.AFKRegionsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

public class ItemPickupListener implements Listener {
    private final AFKRegionsPlugin plugin;
    private boolean disableItemInteraction;

    public ItemPickupListener(AFKRegionsPlugin plugin) {
        this.plugin = plugin;
        reloadSettings();
    }

    public void reloadSettings() {
        this.disableItemInteraction = plugin.getConfig().getBoolean("general.disable_item_interaction", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        // Solo procesar si es un jugador
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        // Solo procesar si la funcionalidad está habilitada
        if (!disableItemInteraction) {
            return;
        }

        Player player = (Player) event.getEntity();

        // Verificar si el jugador está en una región AFK
        if (plugin.tracker().isAfk(player.getUniqueId())) {
            event.setCancelled(true);

            // Opcional: enviar mensaje al jugador (descomentado para evitar spam)
            // player.sendMessage(plugin.messages().msg("item_pickup_disabled"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        // Solo procesar si la funcionalidad está habilitada
        if (!disableItemInteraction) {
            return;
        }

        Player player = event.getPlayer();

        // Verificar si el jugador está en una región AFK
        if (plugin.tracker().isAfk(player.getUniqueId())) {
            event.setCancelled(true);

            // Opcional: enviar mensaje al jugador (descomentado para evitar spam)
            // player.sendMessage(plugin.messages().msg("item_drop_disabled"));
        }
    }
}
