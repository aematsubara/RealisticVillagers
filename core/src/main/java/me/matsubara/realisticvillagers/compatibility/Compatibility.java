package me.matsubara.realisticvillagers.compatibility;

import org.bukkit.entity.Villager;

public interface Compatibility {

    boolean shouldTrack(Villager villager);
}