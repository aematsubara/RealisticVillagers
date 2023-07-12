package me.matsubara.realisticvillagers.compatibility;

import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

public class CompatibilityManager {

    private final List<Compatibility> compatibilities = new ArrayList<>();

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
        return new VTLCompatibility().handle(vtl, player, villager);
    }
}