package me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_19_r1.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

public class SetLookAndInteractPlayer extends Behavior<Villager> {

    private final int interactionRangeSqr;

    public SetLookAndInteractPlayer(int interactionRange) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT));
        this.interactionRangeSqr = interactionRange * interactionRange;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        // Don't interact if the NPC is expecting something.
        return !(villager instanceof VillagerNPC npc) || !npc.isExpecting();
    }

    public void start(ServerLevel level, Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();
        brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap((entities) -> entities.findClosest((nearest) -> isMatchingTarget(villager, nearest)))
                .ifPresent((nearest) -> {
                    brain.setMemory(MemoryModuleType.INTERACTION_TARGET, nearest);
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(nearest, true));
                });
    }

    private boolean isMatchingTarget(Villager villager, LivingEntity target) {
        return target.distanceToSqr(villager) <= interactionRangeSqr && target.getType().equals(EntityType.PLAYER);
    }
}