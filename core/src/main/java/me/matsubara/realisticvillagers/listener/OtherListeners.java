package me.matsubara.realisticvillagers.listener;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.LightningStrikeEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;

public class OtherListeners implements Listener {

    private final RealisticVillagers plugin;

    public OtherListeners(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onLightningStrike(@NotNull LightningStrikeEvent event) {
        event.getLightning().setMetadata("Cause", new FixedMetadataValue(plugin, event.getCause()));
    }
}