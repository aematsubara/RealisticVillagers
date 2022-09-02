package me.matsubara.realisticvillagers.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public record GiftCategory(String name, int reputation, Set<Material> items) {

    public boolean applies(@NotNull ItemStack item) {
        return items.contains(item.getType());
    }
}