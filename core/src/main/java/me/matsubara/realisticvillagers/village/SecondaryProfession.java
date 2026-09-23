package me.matsubara.realisticvillagers.village;

import org.bukkit.Material;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The functional role a working settler takes on top of its vanilla profession.
 * <p>
 * The vanilla profession still owns the villager's looks, workstation and trades; this
 * only adds what the settler contributes to the settlement. Every mapping is derived from
 * the base profession, so a villager that changes job automatically changes role too.
 * <p>
 * Unemployed villagers deliberately have no role here — they are the reserve pool the
 * election recruits candidates from, and giving them a job would defeat that.
 * <p>
 * <b>Why the mapping is lazy:</b> {@link Villager.Profession} is registry-backed on newer
 * server versions, so touching a constant like {@code FLETCHER} needs a running server.
 * Resolving them on first use (instead of in the enum constructor) keeps this class safe to
 * load at any point, and lets a profession that a given Minecraft version doesn't have drop
 * out of the mapping instead of failing the whole plugin.
 */
public enum SecondaryProfession {

    /** Fells trees in the settlement's zone and replants a sapling in their place. */
    LUMBERJACK("lumberjack", "OAK_LOG", "SPRUCE_LOG", "BIRCH_LOG", "STICK"),

    /** Works the settlement mine, producing stone, cobblestone and ores. */
    MINER("miner", "COBBLESTONE", "STONE", "COAL", "RAW_IRON", "SAND"),

    /** Turns mined ore into tools and gear for the settlement and its builders. */
    BLACKSMITH("blacksmith", "IRON_INGOT", "IRON_NUGGET", "COAL"),

    /** Produces and maintains the food pool the whole settlement lives on. */
    FOOD_SUPPLIER("food-supplier", "WHEAT", "CARROT", "POTATO", "BREAD"),

    /** Works the water for the settlement's table. */
    FISHER("fisher", "COD", "SALMON", "STRING"),

    /** Shears the flock and spins what comes off it. */
    WEAVER("weaver", "WHITE_WOOL", "STRING"),

    /**
     * Turns hides into leather, and leather into tack.
     * <p>
     * The saddles are the point: without one nobody in the settlement can ride the horses or
     * camels it keeps, and a saddle is not something a village can otherwise make for itself.
     */
    TANNER("tanner", "LEATHER", "RABBIT_HIDE", "SADDLE"),

    /** Keeps the settlement in paper, books and charts. */
    SCHOLAR("scholar", "PAPER", "BOOK", "SUGAR_CANE"),

    /** Gathers what the settlement's brewing and enchanting depends on. */
    APOTHECARY("apothecary", "REDSTONE", "GLOWSTONE_DUST", "LAPIS_LAZULI");

    private final String key;

    /**
     * What this role puts into the settlement's storage, by material name.
     * <p>
     * Held as names rather than {@link org.bukkit.Material} constants so a material a given
     * Minecraft version has never heard of drops quietly out of the list instead of failing the
     * class to load — the same reason the profession mapping below is resolved lazily.
     */
    private final List<String> yieldNames;

    private volatile @Nullable List<Material> resolvedYield;

    private static volatile Map<Villager.Profession, SecondaryProfession> byBase;

    SecondaryProfession(String key, String... yieldNames) {
        this.key = key;
        this.yieldNames = List.of(yieldNames);
    }

    /**
     * The goods this role contributes, resolved against the running server.
     * <p>
     * Empty for a role whose every material is unknown here, which simply means it produces
     * nothing rather than that the settlement breaks.
     */
    public @NotNull List<Material> getYield() {
        List<Material> resolved = resolvedYield;
        if (resolved != null) return resolved;

        synchronized (this) {
            if (resolvedYield != null) return resolvedYield;

            List<Material> built = new ArrayList<>(yieldNames.size());
            for (String name : yieldNames) {
                Material material = Material.matchMaterial(name);
                if (material != null) built.add(material);
            }
            return resolvedYield = List.copyOf(built);
        }
    }

    /** Config/message key for this role, e.g. {@code food-supplier}. */
    public String getKey() {
        return key;
    }

    private static @NotNull Map<Villager.Profession, SecondaryProfession> byBase() {
        Map<Villager.Profession, SecondaryProfession> map = byBase;
        if (map != null) return map;

        synchronized (SecondaryProfession.class) {
            if (byBase != null) return byBase;

            Map<Villager.Profession, SecondaryProfession> built = new HashMap<>();
            // Every working profession the game has, so nobody stands around contributing
            // nothing. Where two professions plausibly do the same job for the settlement they
            // share a role — an armourer and a mason both want ore and stone — but no profession
            // is left out, which is what made most of a village dead weight before.
            register(built, () -> Villager.Profession.FLETCHER, LUMBERJACK);
            register(built, () -> Villager.Profession.MASON, MINER);
            register(built, () -> Villager.Profession.ARMORER, MINER);
            register(built, () -> Villager.Profession.TOOLSMITH, BLACKSMITH);
            register(built, () -> Villager.Profession.WEAPONSMITH, BLACKSMITH);
            register(built, () -> Villager.Profession.FARMER, FOOD_SUPPLIER);
            register(built, () -> Villager.Profession.BUTCHER, FOOD_SUPPLIER);
            register(built, () -> Villager.Profession.FISHERMAN, FISHER);
            register(built, () -> Villager.Profession.SHEPHERD, WEAVER);
            register(built, () -> Villager.Profession.LEATHERWORKER, TANNER);
            register(built, () -> Villager.Profession.LIBRARIAN, SCHOLAR);
            register(built, () -> Villager.Profession.CARTOGRAPHER, SCHOLAR);
            register(built, () -> Villager.Profession.CLERIC, APOTHECARY);

            return byBase = Collections.unmodifiableMap(built);
        }
    }

    private static void register(
            Map<Villager.Profession, SecondaryProfession> map,
            @NotNull Supplier<Villager.Profession> base,
            SecondaryProfession profession) {
        try {
            Villager.Profession resolved = base.get();
            if (resolved != null) map.put(resolved, profession);
        } catch (Throwable ignored) {
            // This Minecraft version doesn't know that profession — the rest still map fine.
        }
    }

    /** How many vanilla professions resolved to a role here. Zero means the economy is dead. */
    public static int getMappedProfessionCount() {
        return byBase().size();
    }

    /** The vanilla professions that lead to this role on the running server. */
    public @NotNull Set<Villager.Profession> getBaseProfessions() {
        Set<Villager.Profession> professions = new LinkedHashSet<>();
        for (Map.Entry<Villager.Profession, SecondaryProfession> entry : byBase().entrySet()) {
            if (entry.getValue() == this) professions.add(entry.getKey());
        }
        return professions;
    }

    /** The role that follows from {@code base}, or {@code null} if that profession has none. */
    public static @Nullable SecondaryProfession fromBase(@Nullable Villager.Profession base) {
        return base == null ? null : byBase().get(base);
    }

    public static @Nullable SecondaryProfession of(@Nullable Villager villager) {
        return villager == null ? null : fromBase(villager.getProfession());
    }

    /**
     * Whether this villager belongs to the reserve pool elections recruit from.
     * <p>
     * Only {@link Villager.Profession#NONE} counts: NITWIT is what the mayor and the
     * election candidates wear, so treating it as unemployed would let the plugin
     * recruit the sitting mayor as a candidate against itself.
     */
    public static boolean isUnemployed(@Nullable Villager villager) {
        return villager != null && villager.getProfession() == Villager.Profession.NONE;
    }

    /** Whether this villager wears the nitwit look used by mayors and election candidates. */
    public static boolean isNitwit(@Nullable Villager villager) {
        return villager != null && villager.getProfession() == Villager.Profession.NITWIT;
    }

    public static @Nullable SecondaryProfession byKey(@Nullable String key) {
        if (key == null) return null;
        for (SecondaryProfession profession : values()) {
            if (profession.key.equalsIgnoreCase(key)) return profession;
        }
        return null;
    }
}
