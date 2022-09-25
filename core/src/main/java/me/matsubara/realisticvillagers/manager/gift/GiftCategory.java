package me.matsubara.realisticvillagers.manager.gift;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public record GiftCategory(String name, int reputation, Set<Gift> gifts) {

    public boolean applies(@NotNull IVillagerNPC npc, @NotNull ItemStack item) {
        for (Gift gift : gifts) {
            if (!gift.is(item.getType())) continue;
            if (!(gift instanceof Gift.GiftWithCondition condition) || condition.test(npc)) return true;
        }
        return false;
    }
}