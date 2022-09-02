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
        ItemEntity closest = getClosestLovedItem(villager);

        boolean forcePickup = villager instanceof VillagerNPC npc && npc.isExpectingGiftFrom(closest.getThrower());
        return closest.closerThan(villager, maxDistToWalk)
                && (forcePickup || !villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET));
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