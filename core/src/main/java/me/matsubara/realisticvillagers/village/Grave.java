package me.matsubara.realisticvillagers.village;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * One villager's grave: who they were, and how they died.
 * <p>
 * A settlement that loses people and shows nothing for it may as well be losing furniture. A
 * grave is the only record that a particular villager existed — the name, who its parents were,
 * and what killed it — and it is a record left in the world rather than in a menu, so it is
 * found by walking past rather than by going looking.
 * <p>
 * Only villagers who died <b>without a cross</b> are buried. A cross means they can be brought
 * back, and a grave for someone who is coming back is a headstone with nobody under it.
 *
 * @param parents a readable "child of X and Y", or empty when neither is known.
 */
public record Grave(
        @NotNull String name,
        @NotNull String parents,
        @NotNull String cause,
        long diedAt,
        @NotNull String world,
        int x,
        int y,
        int z) {

    public void save(@NotNull ConfigurationSection section) {
        section.set("name", name);
        section.set("parents", parents);
        section.set("cause", cause);
        section.set("died-at", diedAt);
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
    }

    public static @Nullable Grave load(@NotNull ConfigurationSection section) {
        String world = section.getString("world");
        if (world == null || world.isEmpty()) return null;

        return new Grave(
                section.getString("name", "Villager"),
                section.getString("parents", ""),
                section.getString("cause", ""),
                section.getLong("died-at"),
                world,
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"));
    }
}
