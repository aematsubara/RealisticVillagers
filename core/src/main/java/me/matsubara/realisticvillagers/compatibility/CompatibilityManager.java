package me.matsubara.realisticvillagers.compatibility;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CompatibilityManager {

    private final Map<String, Compatibility> compatibilities = new ConcurrentHashMap<>();

    public void addCompatibility(String name, Compatibility compatibility) {
        compatibilities.put(name, compatibility);
    }

    public boolean shouldTrack(Villager villager) {
        for (Compatibility compatibility : compatibilities.values()) {
            if (!compatibility.shouldTrack(villager)) return false;
        }
        return true;
    }

    public boolean handleVTL(Plugin plugin, Player player, Villager villager) {
        return compatibilities.get("VillagerTradeLimiter") instanceof VTLCompatibility vtl && vtl.handle(plugin, player, villager);
    }

    /**
     * Opens ValhallaMMO's trading interface for this villager in place of the plugin's own.
     *
     * @return {@code false} when ValhallaMMO is not installed, or when it was but declined this
     * villager (see {@link ValhallaCompatibility#openTrade} for why that is not a fault).
     */
    public boolean handleValhallaTrade(RealisticVillagers plugin, Plugin valhalla, Player player, AbstractVillager villager) {
        return compatibilities.get("ValhallaMMO") instanceof ValhallaCompatibility valhallaCompat
                && valhallaCompat.openTrade(plugin, valhalla, player, villager);
    }

    public boolean shouldCancelMetadata(Player player) {
        return compatibilities.get("ViaVersion") instanceof ViaCompatibility via && via.cancelMetadata(player);
    }
}