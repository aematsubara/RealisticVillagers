package me.matsubara.realisticvillagers.data;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public record LastKnownPosition(String world, double x, double y, double z) {

    public static final LastKnownPosition ZERO = new LastKnownPosition("Unknown", 0.0d, 0.0d, 0.0d);

    @Contract(" -> new")
    public @NotNull Location asLocation() {
        return new Location(Bukkit.getWorld(world), x, y, z);
    }
}