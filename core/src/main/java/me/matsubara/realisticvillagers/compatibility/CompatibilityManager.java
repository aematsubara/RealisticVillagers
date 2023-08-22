package me.matsubara.realisticvillagers.compatibility;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompatibilityManager {

    private final List<Compatibility> compatibilities = new ArrayList<>();
    private final Map<String, Compatibility> external = new HashMap<>();

    public void addCompatibility(Compatibility compatibility) {
        compatibilities.add(compatibility);
    }

    public boolean shouldTrack(Villager villager) {
        for (Compatibility compatibility : compatibilities) {
            if (!compatibility.shouldTrack(villager)) return false;
        }
        return true;
    }

    public boolean handleVTL(Plugin vtl, Player player, Villager villager) {
        return ((VTLCompatibility) external.computeIfAbsent("VillagerTradeLimiter", name -> new VTLCompatibility())).handle(vtl, player, villager);
    }

    public boolean shouldCancelMetadata(Player player) {
        return ((ViaCompatibility) external.computeIfAbsent("ViaVersion", name -> new ViaCompatibility())).cancelMetadata(player);
    }
}