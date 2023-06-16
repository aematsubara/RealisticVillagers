package me.matsubara.realisticvillagers.npc;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface SpawnCustomizer {

    void handleSpawn(@NotNull NPC npc, @NotNull Player player);
}