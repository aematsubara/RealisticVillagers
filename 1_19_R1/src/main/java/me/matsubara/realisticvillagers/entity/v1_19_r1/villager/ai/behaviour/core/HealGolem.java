package me.matsubara.realisticvillagers.entity.v1_19_r1.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.entity.v1_19_r1.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Optional;
import java.util.Set;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class HealGolem extends Behavior<Villager> {

    private static final int DISTANCE_TO_HEAL = 2;
    private static final int IRON_INGOT_HEAL_AMOUNT = 25;

    private final float speedModifier;
    private ItemStack previousItem;

    private static final Set<Item> HEAL_ITEM = Sets.newHashSet(Items.IRON_INGOT);

    @SuppressWarnings("ConstantConditions")
    public HealGolem(int duration, float speedModifier) {
        super(ImmutableMap.of(
                        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                        VillagerNPC.HAS_HEALED_GOLEM_RECENTLY, MemoryStatus.VALUE_ABSENT),
                duration);
        this.speedModifier = speedModifier;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return isGolemVisible(villager)
                && villager instanceof VillagerNPC npc
                && npc.isDoingNothing(ChangeItemType.HEALING_GOLEM)
                && villager.getInventory().hasAnyOf(HEAL_ITEM)
                && !villager.getBrain().hasMemoryValue(VillagerNPC.HAS_HEALED_GOLEM_RECENTLY)
                && !villager.isSleeping()
                && Config.VILLAGER_FIX_IRON_GOLEM_WITH_IRON.asBool();
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long game) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) npc.setHealingGolem(true);
        IronGolem golem = getNearestHurtGolem(villager).get();
        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, golem);
        BehaviorUtils.lookAtEntity(villager, golem);

        // Save previous weapon (if any).
        if (villager.isHolding(item -> !item.isEmpty())) {
            previousItem = villager.getMainHandItem();
        } else {
            previousItem = ItemStack.EMPTY;
        }

        villager.setItemInHand(InteractionHand.MAIN_HAND, Items.IRON_INGOT.getDefaultInstance());
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        IronGolem golem = getNearestHurtGolem(villager).get();
        BehaviorUtils.lookAtEntity(villager, golem);

        if (isWithinHealingDistance(villager, golem)) {
            float health = golem.getHealth();

            golem.heal(IRON_INGOT_HEAL_AMOUNT);
            if (golem.getHealth() == health) return;

            float pitch = 1.0f + (level.random.nextFloat() - level.random.nextFloat()) * 0.2f;
            golem.playSound(SoundEvents.IRON_GOLEM_REPAIR, 1.0f, pitch);

            // Swing hand.
            villager.swing(InteractionHand.MAIN_HAND);

            // Time to try to fix again (in ticks).
            long cooldown = Config.VILLAGER_FIX_IRON_GOLEM_COOLDOWN.asLong();
            if (cooldown > 0L) {
                villager.getBrain().setMemoryWithExpiry(VillagerNPC.HAS_HEALED_GOLEM_RECENTLY, true, cooldown);
            }

            // Remove iron ingot.
            villager.getInventory().removeItemType(Items.IRON_INGOT, 1);
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, golem, speedModifier, 0);
        }
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);

        villager.setItemSlot(EquipmentSlot.MAINHAND, previousItem);

        if (villager instanceof VillagerNPC npc) npc.setHealingGolem(false);
    }

    private boolean isGolemVisible(Villager villager) {
        return getNearestHurtGolem(villager).isPresent();
    }

    private Optional<IronGolem> getNearestHurtGolem(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .get()
                .findClosest(living -> living instanceof IronGolem golem && golem.getCrackiness() != IronGolem.Crackiness.NONE)
                .map(living -> (IronGolem) living);
    }

    private boolean isWithinHealingDistance(Villager villager, IronGolem golem) {
        return villager.blockPosition().closerThan(golem.blockPosition(), DISTANCE_TO_HEAL);
    }
}