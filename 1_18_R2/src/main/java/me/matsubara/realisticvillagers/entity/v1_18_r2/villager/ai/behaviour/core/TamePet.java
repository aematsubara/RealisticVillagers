package me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.entity.Pet;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class TamePet extends Behavior<Villager> {

    private ItemStack previousItem;

    private final int distanceToTame;
    private final float speedModifier;
    private final Predicate<LivingEntity> filter;
    private final Set<Item> tameItems;
    private Item selectedFood;

    @SuppressWarnings("ConstantConditions")
    public TamePet(int distanceToTame, float speedModifier, Predicate<LivingEntity> filter, Set<Item> tameItems) {
        super(ImmutableMap.of(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                VillagerNPC.HAS_TAMED_RECENTLY, MemoryStatus.VALUE_ABSENT));
        this.distanceToTame = distanceToTame;
        this.speedModifier = speedModifier;
        this.filter = filter;
        this.tameItems = tameItems;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return isPetVisible(villager)
                && villager.getInventory().hasAnyOf(tameItems)
                && villager instanceof VillagerNPC npc
                && npc.isDoingNothing(ChangeItemType.TAMING)
                && !villager.getBrain().hasMemoryValue(VillagerNPC.HAS_TAMED_RECENTLY)
                // Only tame if tame item isn't edible and villager isn't hungry.
                && (tameItems.stream().noneMatch(Consume::isSafeFood) || !npc.getFoodData().needsFood());
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long game) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) npc.setTaming(true);

        LivingEntity pet = getNearestUntamedPet(villager).get();
        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, pet);
        BehaviorUtils.lookAtEntity(villager, pet);

        // Save previous weapon (if any).
        if (villager.isHolding(item -> !item.isEmpty())) {
            previousItem = villager.getMainHandItem();
        } else {
            previousItem = ItemStack.EMPTY;
        }

        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack item = villager.getInventory().getItem(i);
            if (tameItems.contains(item.getItem())) {
                selectedFood = item.getItem();
                break;
            }
        }

        villager.setItemInHand(InteractionHand.MAIN_HAND, selectedFood.getDefaultInstance());
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        LivingEntity pet = getNearestUntamedPet(villager).get();
        BehaviorUtils.lookAtEntity(villager, pet);

        if (isWithinTamingDistance(villager, pet)) {
            if (villager instanceof VillagerNPC && pet instanceof Pet) {
                ((Pet) pet).tameByVillager(((VillagerNPC) villager));
            }

            // Time to try to tame again (in ticks).
            long cooldown = Config.TAME_COOLDOWN.asLong();
            if (cooldown > 0L) {
                villager.getBrain().setMemoryWithExpiry(VillagerNPC.HAS_TAMED_RECENTLY, true, cooldown);
            }

            villager.getInventory().removeItemType(selectedFood, 1);
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, pet, speedModifier, 0);
        }
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);

        villager.setItemSlot(EquipmentSlot.MAINHAND, previousItem);

        if (villager instanceof VillagerNPC npc) npc.setTaming(false);
    }

    private boolean isPetVisible(Villager villager) {
        return getNearestUntamedPet(villager).isPresent();
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    private Optional<LivingEntity> getNearestUntamedPet(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap(memory -> memory.findClosest(filter));
    }

    private boolean isWithinTamingDistance(Villager villager, LivingEntity pet) {
        return villager.blockPosition().closerThan(pet.blockPosition(), distanceToTame);
    }
}
