package me.matsubara.realisticvillagers.util;

import com.google.common.base.Strings;
import me.matsubara.realisticvillagers.RealisticVillagers;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.List;

public final class Shape {

    private final RealisticVillagers plugin;

    private final String name;
    private final boolean shaped;
    private final List<String> ingredients;
    private final List<String> shape;

    private Recipe recipe;

    public Shape(RealisticVillagers plugin, String name, boolean shaped, List<String> ingredients, List<String> shape, ItemStack result) {
        this.plugin = plugin;
        this.name = name;
        this.shaped = shaped;
        this.ingredients = ingredients;
        this.shape = shape;
        register(result);
    }

    public void register(ItemStack item) {
        NamespacedKey nKey = new NamespacedKey(plugin, name);
        recipe = shaped ? new ShapedRecipe(nKey, item) : new ShapelessRecipe(nKey, item);

        // Set shaped recipe.
        if (shaped) ((ShapedRecipe) recipe).shape(shape.toArray(new String[0]));

        for (String ingredient : ingredients) {
            if (Strings.isNullOrEmpty(ingredient) || ingredient.equalsIgnoreCase("none")) continue;
            String[] split = StringUtils.split(StringUtils.deleteWhitespace(ingredient), ',');
            if (split.length == 0) split = StringUtils.split(ingredient, ' ');

            Material type = Material.valueOf(split[0]);

            char key = ' ';

            if (split.length > 1) {
                key = split[1].charAt(0);
            }

            if (shaped) {
                // Empty space are used for AIR.
                if (key == ' ') continue;
                ((ShapedRecipe) recipe).setIngredient(key, type);
            } else {
                ((ShapelessRecipe) recipe).addIngredient(type);
            }
        }

        if (!Bukkit.addRecipe(recipe)) {
            plugin.getLogger().warning("The recipe couldn't be created for {" + name + "}!");
        }
    }

    public Recipe getRecipe() {
        return recipe;
    }
}