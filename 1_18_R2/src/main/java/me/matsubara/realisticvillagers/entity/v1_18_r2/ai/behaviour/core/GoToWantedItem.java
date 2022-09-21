package me.matsubara.realisticvillagers.entity.v1_18_r2.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_18_r2.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;

public class GoToWantedItem extends Behavior<Villager> {

    private final float speedModifier;
    private final int maxDistToWalk;

    public GoToWantedItem(float speedModifier, int maxDistToWalk) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, MemoryStatus.VALUE_PRESENT));
        this.speedModifier = speedModifier;
        this.maxDistToWalk = maxDistToWalk;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return false;
        ItemEntity closest = getClosestLovedItem(villager);

        // If item is a gift or has been fished by this villager, go to the item regardless of distance and cooldown.
        if (npc.fished(closest.getItem()) || npc.isExpectingGiftFrom(closest.getThrower())) return true;

        return closest.closerThan(villager, maxDistToWalk) && !villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        BehaviorUtils.setWalkAndLookTargetMemories(villager, getClosestLovedItem(villager), speedModifier, 0);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private ItemEntity getClosestLovedItem(Villager level) {
        return level.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).get();
    }
}