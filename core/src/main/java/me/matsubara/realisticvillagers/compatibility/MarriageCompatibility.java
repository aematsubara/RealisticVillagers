package me.matsubara.realisticvillagers.compatibility;

import at.pcgamingfreaks.MarriageMaster.API.MarriageMasterPlugin;

import at.pcgamingfreaks.MarriageMaster.API.MarriageManager;
import at.pcgamingfreaks.MarriageMaster.API.MarriagePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;


public class MarriageCompatibility implements Compatibility {

    public MarriageMasterPlugin getMarriageMaster() {
        Plugin bukkitPlugin = Bukkit.getPluginManager().getPlugin("MarriageMaster");
        return (MarriageMasterPlugin) bukkitPlugin;

    }
    public boolean marriedPlayer(@NotNull Player player) {

        UUID playerUUID = player.getUniqueId();
        MarriagePlayer data = getMarriageMaster().getPlayerData(playerUUID);
        return data.isMarried();
    }
    @Override
    public boolean shouldTrack(Villager villager) {return true;}
}

