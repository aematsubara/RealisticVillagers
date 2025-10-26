package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_21_R5.inventory.CraftItemStack;

public class CheckInventory extends Behavior<Villager> {

    private int tryAgain;
    private static final int TRY_AGAIN_COOLDOWN = 100;

    public CheckInventory() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (tryAgain > 0) {
            tryAgain--;
            return false;
        }

        return villager instanceof VillagerNPC npc
                && !npc.is(VillagerProfession.FISHERMAN)
                && !npc.isSleeping()
                && !npc.checkCurrentActivity(Activity.WORK)
                && (!npc.isHoldingWeapon() || needsArmor(npc))
                && npc.isDoingNothing(true);
    }

    private boolean needsArmor(VillagerNPC npc) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HAND) continue;
            if (npc.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        SimpleContainer inventory = npc.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);

            CraftItemStack bukkitItem = CraftItemStack.asCraftMirror(item);
            if (!ItemStackUtils.isWeapon(bukkitItem)
                    && ItemStackUtils.getSlotByItem(bukkitItem) == null
                    && bukkitItem.getType() != Material.SHIELD) continue;

            // Equip armor/weapon.
            if (ItemStackUtils.setBetterWeaponInMaindHand(
                    npc.bukkit(),
                    bukkitItem,
                    false,
                    ItemStackUtils.isMeleeWeapon(bukkitItem) && !npc.isHoldingRangeWeapon())
                    || ItemStackUtils.setArmorItem(npc.bukkit(), bukkitItem, false)) {
                item.shrink(1);
            }
        }

        // Only try again in 5 seconds if villager isn't fightning.
        if (!npc.isFighting()) {
            tryAgain = TRY_AGAIN_COOLDOWN;
        }
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return false;
    }
}