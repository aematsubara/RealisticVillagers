package me.matsubara.realisticvillagers.hologram;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

public final class HologramListener implements Listener {

    private final RealisticVillagers plugin;

    public HologramListener(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    /**
     * Main click handler — no Interaction entities needed.
     * We raycast from the player's eye to the nearest clickable TextDisplay.
     * Because TextDisplay uses Billboard.VERTICAL it always faces the player,
     * so the raycast naturally aligns with the visible text from every angle.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR  && action != Action.RIGHT_CLICK_BLOCK
         && action != Action.LEFT_CLICK_AIR   && action != Action.LEFT_CLICK_BLOCK) return;

        HologramMenu menu = plugin.getHologramManager().getMenuForPlayer(event.getPlayer().getUniqueId());
        if (menu == null) return;

        MenuAction menuAction = menu.getHoveredAction();
        if (menuAction == null) {
            // Nothing in the text menu was aimed at — the click may still land on a building
            // drawn on the radar panel.
            if (menu.clickBuilding()) event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        menu.handleAction(menuAction);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        plugin.getHologramManager().closeMenu(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        plugin.getHologramManager().closeMenuForVillager(event.getEntity().getUniqueId());
    }
}
