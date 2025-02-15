package me.matsubara.realisticvillagers.compatibility;

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

    public boolean shouldCancelMetadata(Player player) {
        return compatibilities.get("ViaVersion") instanceof ViaCompatibility via && via.cancelMetadata(player);
    }
    public boolean marriedPlayer(Player player) {
        return compatibilities.get("MarriageMaster") instanceof MarriageCompatibility marriage && marriage.marriedPlayer(player);
    }
}