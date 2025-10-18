package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class EquipTotem extends Behavior<Villager> {

    private static final Set<Item> TOTEM = ImmutableSet.of(Items.TOTEM_OF_UNDYING);

    public EquipTotem() {
        super(ImmutableMap.of(), 100);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return false;

        // Less than 4 hearts, has a totem to equip and isn't holding one.
        return npc.getHealth() < 8.0f && npc.getInventory().hasAnyOf(TOTEM) && !npc.isHolding(Items.TOTEM_OF_UNDYING);
    }

    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        SimpleContainer inventory = villager.getInventory();

        ItemStack offHandItem = villager.getOffhandItem();
        if (!offHandItem.isEmpty()) {
            if (inventory.canAddItem(offHandItem)) {
                inventory.addItem(offHandItem);
            } else {
                ((VillagerNPC) villager).drop(offHandItem);
            }
        }

        inventory.removeItemType(Items.TOTEM_OF_UNDYING, 1);
        villager.setItemInHand(InteractionHand.OFF_HAND, Items.TOTEM_OF_UNDYING.getDefaultInstance());
    }
}