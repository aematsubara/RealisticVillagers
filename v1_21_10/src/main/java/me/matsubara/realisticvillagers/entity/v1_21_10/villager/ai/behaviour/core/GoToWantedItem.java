package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

public class GoToWantedItem extends Behavior<Villager> {

    private final float speedModifier;
    private final int maxDistToWalk;

    public GoToWantedItem(float speedModifier, int maxDistToWalk) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, // By default, this should be absent, but we need it to be registered for gifting.
                VillagerNPC.NEAREST_WANTED_ITEM, MemoryStatus.VALUE_PRESENT));
        this.speedModifier = speedModifier;
        this.maxDistToWalk = maxDistToWalk;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return false;

        // If the item is a gift or has been fished by this villager, go to the item regardless of distance and cooldown.
        ItemEntity closest = getClosestLovedItem(npc);
        if (force(npc, closest)) return true;

        return closest.closerThan(npc, maxDistToWalk)
                && villager.canPickUpLoot()
                && level.getWorldBorder().isWithinBounds(closest.blockPosition())
                && !isOnPickupCooldown(npc)
                && !npc.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET);
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return villager instanceof VillagerNPC npc
                && villager.getBrain().hasMemoryValue(VillagerNPC.NEAREST_WANTED_ITEM)
                && force(npc, getClosestLovedItem(villager));
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        tick(level, villager, time);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        BehaviorUtils.setWalkAndLookTargetMemories(villager, getClosestLovedItem(villager), speedModifier, 0);
    }

    private boolean force(@NotNull VillagerNPC npc, @NotNull ItemEntity closest) {
        return npc.fished(closest.getItem()) || (closest.thrower != null && npc.isExpectingGiftFrom(closest.thrower.getUUID()));
    }

    private boolean isOnPickupCooldown(@NotNull Villager level) {
        return level.getBrain().checkMemory(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS, MemoryStatus.VALUE_PRESENT);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private @NotNull ItemEntity getClosestLovedItem(@NotNull Villager level) {
        return level.getBrain().getMemory(VillagerNPC.NEAREST_WANTED_ITEM).get();
    }
}