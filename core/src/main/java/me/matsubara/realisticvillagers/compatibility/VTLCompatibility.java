package me.matsubara.realisticvillagers.compatibility;

import com.pretzel.dev.villagertradelimiter.VillagerTradeLimiter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;

public class VTLCompatibility implements Compatibility {

    public boolean handle(Plugin vtl, Player player, Villager villager) {
        // Returns true if couldn't open the merchant GUI, knowning that we should close the current inventory to prevent issues.
        ((VillagerTradeLimiter) vtl).getPlayerListener().onPlayerBeginTrading(new PlayerInteractEntityEvent(player, villager, EquipmentSlot.HAND));
        return player.getOpenInventory().getTopInventory().getType() != InventoryType.MERCHANT;
    }

    @Override
    public boolean shouldTrack(Villager villager) {
        return true;
    }
}