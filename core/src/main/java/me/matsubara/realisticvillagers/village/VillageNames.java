package me.matsubara.realisticvillagers.village;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Names a settlement after the land it stands on.
 * <p>
 * A village called "Village 214, -88" tells a player nothing they cannot read off their own
 * coordinates. A name drawn from the biome does the one job a name has: it makes a place
 * memorable and tells the two settlements on either side of a river apart.
 * <p>
 * The name is generated once, when the village is first detected, and stored on it — so it never
 * changes underneath a player, survives restarts, and can be overwritten from the mayor's menu
 * later without this class having any say in it.
 * <p>
 * Generation is seeded by the village's own id, so the same settlement would be named the same
 * way twice. That is not required for correctness — the name is saved — but it makes the
 * generator reproducible, which is the difference between a bug that can be chased and one that
 * cannot.
 */
public final class VillageNames {

    private VillageNames() {
    }

    /** Word pairs by terrain, chosen so that any first + second reads as a plausible place. */
    private record Palette(List<String> first, List<String> second) {}

    /**
     * Palettes loaded from config, keyed by terrain, or {@code null} until they are read.
     * <p>
     * The built-in lists below stay as the fallback for every group the file doesn't define, so
     * deleting one entry from the config costs you that group's words and nothing else.
     */
    private static @Nullable java.util.Map<String, Palette> configured;

    /**
     * Loads the name palettes from {@code village.names} in config.yml.
     * <p>
     * Called on load and on reload. A group with no {@code first} or no {@code second} list is
     * skipped rather than half-applied, since a palette missing one half can only produce half a
     * name.
     */
    public static void load(@Nullable org.bukkit.configuration.ConfigurationSection section) {
        if (section == null) {
            configured = null;
            return;
        }

        java.util.Map<String, Palette> loaded = new java.util.HashMap<>();
        for (String group : section.getKeys(false)) {
            List<String> first = section.getStringList(group + ".first");
            List<String> second = section.getStringList(group + ".second");
            if (first.isEmpty() || second.isEmpty()) continue;

            loaded.put(group.toLowerCase(Locale.ROOT), new Palette(List.copyOf(first), List.copyOf(second)));
        }

        configured = loaded.isEmpty() ? null : loaded;
    }

    /** The configured palette for this group, or the built-in one. */
    private static @NotNull Palette palette(@NotNull String group, @NotNull Palette fallback) {
        java.util.Map<String, Palette> loaded = configured;
        if (loaded == null) return fallback;

        Palette custom = loaded.get(group);
        return custom != null ? custom : fallback;
    }

    private static final Palette DESERT = new Palette(
            List.of("Sun", "Dust", "Amber", "Scorch", "Mirage", "Gold", "Ash", "Dune"),
            List.of("reach", "hollow", "rest", "gate", "spire", "crest", "haven", "watch"));

    private static final Palette SNOW = new Palette(
            List.of("Frost", "White", "Pale", "Winter", "Ice", "Still", "Hollow", "North"),
            List.of("hold", "fell", "reach", "barrow", "crown", "watch", "rest", "mere"));

    private static final Palette SAVANNA = new Palette(
            List.of("Acacia", "Ember", "Wide", "Long", "Bright", "Dry", "Sun", "Red"),
            List.of("plain", "run", "field", "stead", "ridge", "reach", "gate", "vale"));

    private static final Palette TAIGA = new Palette(
            List.of("Pine", "Elk", "Deep", "Green", "Wolf", "Moss", "Fern", "Grey"),
            List.of("wood", "hollow", "lodge", "fall", "grove", "hearth", "marsh", "burn"));

    private static final Palette JUNGLE = new Palette(
            List.of("Vine", "Emerald", "Deep", "Cloud", "Parrot", "Fern", "Green", "Rain"),
            List.of("canopy", "hollow", "temple", "reach", "shade", "spring", "root", "veil"));

    private static final Palette SWAMP = new Palette(
            List.of("Mire", "Black", "Willow", "Fog", "Reed", "Damp", "Toad", "Murk"),
            List.of("water", "marsh", "hollow", "bank", "fen", "rest", "crossing", "bog"));

    private static final Palette PLAINS = new Palette(
            List.of("Green", "Oak", "Fair", "Mill", "Bright", "Wheat", "Stone", "River"),
            List.of("field", "brook", "stead", "ford", "meadow", "hill", "bridge", "crossing"));

    /**
     * A name for the settlement at {@code center}, or a plain fallback if the world is gone.
     * <p>
     * The biome is read from the centre block. One sample is enough: a village sits in one
     * landscape, and sampling more would only average away the character being named after.
     */
    public static @NotNull String generate(@Nullable Location center, @NotNull java.util.UUID id) {
        Palette palette = paletteFor(center);
        Random random = new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits());

        String first = palette.first().get(random.nextInt(palette.first().size()));
        String second = palette.second().get(random.nextInt(palette.second().size()));

        return first + second;
    }

    /**
     * Which terrain group this place belongs to.
     * <p>
     * Shared with the building styles, so a village named after its desert is also built out of
     * the desert — one answer to "what kind of place is this", used everywhere it matters.
     */
    public static @NotNull String groupFor(@Nullable Location center) {
        if (center == null) return "plains";

        World world = center.getWorld();
        if (world == null) return "plains";

        String biome;
        try {
            biome = world.getBiome(center.getBlockX(), center.getBlockY(), center.getBlockZ())
                    .toString()
                    .toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            return "plains";
        }

        if (contains(biome, "desert", "badlands", "mesa")) return "desert";
        if (contains(biome, "snow", "frozen", "ice", "cold")) return "snow";
        if (contains(biome, "savanna")) return "savanna";
        if (contains(biome, "taiga", "grove", "pine", "spruce")) return "taiga";
        if (contains(biome, "jungle", "bamboo")) return "jungle";
        if (contains(biome, "swamp", "mangrove")) return "swamp";

        return "plains";
    }

    private static @NotNull Palette paletteFor(@Nullable Location center) {
        if (center == null) return palette("plains", PLAINS);

        World world = center.getWorld();
        if (world == null) return palette("plains", PLAINS);

        String biome;
        try {
            biome = world.getBiome(center.getBlockX(), center.getBlockY(), center.getBlockZ())
                    .toString()
                    .toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {
            // Biome lookups have moved between versions and the registry can refuse a key it
            // doesn't know. A village keeps a name either way.
            return palette("plains", PLAINS);
        }

        // Matched on substrings rather than exact keys, so biome variants a version adds
        // (snowy_taiga, windswept_savanna, sparse_jungle …) land in the right palette without
        // this list having to know every one of them.
        if (contains(biome, "desert", "badlands", "mesa")) return palette("desert", DESERT);
        if (contains(biome, "snow", "frozen", "ice", "cold")) return palette("snow", SNOW);
        if (contains(biome, "savanna")) return palette("savanna", SAVANNA);
        if (contains(biome, "taiga", "grove", "pine", "spruce")) return palette("taiga", TAIGA);
        if (contains(biome, "jungle", "bamboo")) return palette("jungle", JUNGLE);
        if (contains(biome, "swamp", "mangrove")) return palette("swamp", SWAMP);

        return palette("plains", PLAINS);
    }

    private static boolean contains(@NotNull String biome, String @NotNull ... needles) {
        for (String needle : needles) {
            if (biome.contains(needle)) return true;
        }
        return false;
    }
}
