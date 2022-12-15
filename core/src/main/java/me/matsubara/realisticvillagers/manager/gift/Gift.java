package me.matsubara.realisticvillagers.manager.gift;

import lombok.Getter;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Material;

import java.util.function.Predicate;

@Getter
public class Gift {

    private final int amount;
    private final Material type;
    private final boolean inventoryLootOnly;

    public Gift(int amount, Material type, boolean inventoryLootOnly) {
        this.amount = amount;
        this.type = type;
        this.inventoryLootOnly = inventoryLootOnly;
    }

    public boolean is(Material type) {
        return this.type == type;
    }

    public static class GiftWithCondition extends Gift {

        private final Predicate<IVillagerNPC> predicate;

        public GiftWithCondition(int amount, Material type, boolean inventoryLootOnly, Predicate<IVillagerNPC> predicate) {
            super(amount, type, inventoryLootOnly);
            this.predicate = predicate;
        }

        public boolean test(IVillagerNPC npc) {
            return predicate.test(npc);
        }
    }
}