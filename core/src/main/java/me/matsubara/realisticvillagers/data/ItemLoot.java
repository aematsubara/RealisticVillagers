package me.matsubara.realisticvillagers.data;

import org.bukkit.inventory.ItemStack;

import java.util.function.Supplier;

public record ItemLoot(
        Supplier<ItemStack> item,
        double chance,
        boolean bow,
        boolean crossbow,
        boolean randomVanillaEnchantments,
        boolean offHandIfPossible) {

    public boolean forRange() {
        return bow && crossbow;
    }

    public ItemStack getItem() {
        return item.get();
    }
}
