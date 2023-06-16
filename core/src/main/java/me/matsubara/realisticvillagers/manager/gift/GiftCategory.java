package me.matsubara.realisticvillagers.manager.gift;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public record GiftCategory(String name, int reputation, Set<Gift> gifts) {

    public boolean applies(@NotNull IVillagerNPC npc, @NotNull ItemStack item) {
        return appliesToVillager(gifts, npc, item, false) != null;
    }

    public static @Nullable Gift appliesToVillager(@NotNull Set<Gift> gifts, @NotNull IVillagerNPC npc, @NotNull ItemStack item, boolean isItemPickup) {
        for (Gift gift : gifts) {
            if (!gift.is(item.getType())) continue;
            if (isItemPickup && gift.isInventoryLootOnly()) continue;
            if (!(gift instanceof Gift.GiftWithCondition condition) || condition.test(npc)) return gift;
        }
        return null;
    }
}