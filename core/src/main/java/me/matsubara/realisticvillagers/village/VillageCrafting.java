package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns what the settlement digs up into what it can build with.
 * <p>
 * Without this the economy is a dead end, and provably so: a village produced logs, cobblestone,
 * iron and food, while the simplest house in the blueprint file wants planks, stairs, a door,
 * glass and a fence. Two of the twelve materials a small house needs were ever gathered, so no
 * settlement could raise anything, ever — the stores just filled up with raw goods nobody could
 * spend.
 * <p>
 * A refining step is the honest fix rather than simply handing workers finished doors. A
 * lumberjack that fells trees and saws them into planks is doing the job its trade implies, and
 * it keeps the chain visible: fell a tree, get logs; have a lumberjack, get planks; have planks,
 * get doors. Every step is a thing a player can watch happen and reason about.
 */
public final class VillageCrafting {

    private final RealisticVillagers plugin;

    private List<Recipe> recipes = Collections.emptyList();

    /**
     * One conversion.
     *
     * @param role who can do it, or {@code null} for anyone working in the settlement.
     */
    public record Recipe(
            @NotNull Material result,
            int amount,
            @NotNull Map<Material, Integer> ingredients,
            @Nullable SecondaryProfession role) {}

    /**
     * How many times over an ingredient must be in stock before any of it is refined.
     * <p>
     * Stops a settlement turning every last log into planks the moment it has six of them. There
     * has to be a surplus first, so raw goods stay available for whatever else wants them.
     */
    private static final int RESERVE = 3;

    public VillageCrafting(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    public void load(@Nullable ConfigurationSection section) {
        List<Recipe> loaded = new ArrayList<>();

        if (section != null) {
            for (Object raw : section.getList("recipes", Collections.emptyList())) {
                Recipe recipe = read(raw);
                if (recipe != null) loaded.add(recipe);
            }
        }

        recipes = List.copyOf(loaded);
        plugin.getLogger().info("Loaded " + recipes.size() + " settlement crafting recipe(s).");
    }

    private @Nullable Recipe read(@Nullable Object raw) {
        if (!(raw instanceof Map<?, ?> map)) return null;

        Material result = Material.matchMaterial(String.valueOf(map.get("result")));
        if (result == null) return null;

        int amount = map.get("amount") instanceof Number number ? number.intValue() : 1;

        Map<Material, Integer> ingredients = new EnumMap<>(Material.class);
        if (map.get("ingredients") instanceof Map<?, ?> listed) {
            for (Map.Entry<?, ?> entry : listed.entrySet()) {
                Material material = Material.matchMaterial(String.valueOf(entry.getKey()));
                if (material == null) continue;
                if (!(entry.getValue() instanceof Number count)) continue;

                ingredients.put(material, Math.max(1, count.intValue()));
            }
        }

        if (ingredients.isEmpty()) return null;

        Object role = map.get("role");
        return new Recipe(result, Math.max(1, amount), Map.copyOf(ingredients),
                role == null ? null : SecondaryProfession.byKey(String.valueOf(role).toLowerCase(Locale.ROOT)));
    }

    /**
     * Runs every recipe the settlement has someone to work, once per round.
     * <p>
     * Once each rather than as often as the stores allow: a village should refine steadily, not
     * convert its entire stock the instant it crosses the threshold.
     *
     * @param working the roles that actually had someone at their post this round.
     */
    public void refine(@NotNull Village village, @NotNull Set<SecondaryProfession> working) {
        if (recipes.isEmpty()) return;

        VillageStorage storage = village.getStorage();

        for (Recipe recipe : recipes) {
            if (recipe.role() != null && !working.contains(recipe.role())) continue;

            boolean affordable = true;
            for (Map.Entry<Material, Integer> item : recipe.ingredients().entrySet()) {
                if (storage.count(item.getKey()) >= item.getValue() * RESERVE) continue;

                affordable = false;
                break;
            }

            if (!affordable) continue;

            for (Map.Entry<Material, Integer> item : recipe.ingredients().entrySet()) {
                storage.remove(item.getKey(), item.getValue());
            }

            storage.add(recipe.result(), recipe.amount());
        }
    }

    public int getRecipeCount() {
        return recipes.size();
    }
}
