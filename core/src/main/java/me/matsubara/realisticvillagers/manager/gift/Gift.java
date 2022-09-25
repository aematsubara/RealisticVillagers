package me.matsubara.realisticvillagers.manager.gift;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Material;

import java.util.function.Predicate;

public class Gift {

    private final Material type;

    public Gift(Material type) {
        this.type = type;
    }

    public Material getType() {
        return type;
    }

    public boolean is(Material type) {
        return this.type == type;
    }

    public static class GiftWithCondition extends Gift {

        private final Predicate<IVillagerNPC> predicate;

        public GiftWithCondition(Material material, Predicate<IVillagerNPC> predicate) {
            super(material);
            this.predicate = predicate;
        }

        public boolean test(IVillagerNPC npc) {
            return predicate.test(npc);
        }
    }
}