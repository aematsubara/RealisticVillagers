package me.matsubara.realisticvillagers.util;

import com.google.common.base.Strings;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
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
    private final @Getter ItemStack result;
    private final @Getter NamespacedKey key;

    public Shape(RealisticVillagers plugin, String name, boolean shaped, List<String> ingredients, List<String> shape, ItemStack result) {
        this.plugin = plugin;
        this.name = name;
        this.shaped = shaped;
        this.ingredients = ingredients;
        this.shape = shape;
        this.result = result;
        this.key = new NamespacedKey(plugin, name);
        if (!ingredients.isEmpty() && (!shaped || !shape.isEmpty())) {
            register(result);
        }
    }

    public void register(ItemStack item) {
        Recipe recipe = shaped ? new ShapedRecipe(key, item) : new ShapelessRecipe(key, item);

        // Set shaped recipe.
        if (shaped) ((ShapedRecipe) recipe).shape(shape.toArray(new String[0]));

        for (String ingredient : ingredients) {
            if (Strings.isNullOrEmpty(ingredient)) continue;
            String[] split = PluginUtils.splitData(ingredient);

            Material type = Material.valueOf(split[0]);
            char key = split.length > 1 ? split[1].charAt(0) : ' ';

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
}