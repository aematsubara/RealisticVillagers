package me.matsubara.realisticvillagers.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;

public record LastKnownPosition(String world, double x, double y, double z) {

    public static final LastKnownPosition ZERO = new LastKnownPosition("Unknown", 0.0d, 0.0d, 0.0d);

    public Location asLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z);
    }
}