package me.matsubara.realisticvillagers.entity.v1_18_r2.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public class LookAtTargetSink extends Behavior<Villager> {

    public LookAtTargetSink(int minDuration, int maxDuration) {
        super(ImmutableMap.of(
                        MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_PRESENT),
                minDuration,
                maxDuration);
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return villager.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                .filter((tracker) -> tracker.isVisibleBy(villager))
                .isPresent();
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        villager.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                .ifPresent((tracker) -> {
                    // Baby villagers as NPC look a bit higher than target location.
                    Vec3 position = tracker.currentPosition();

                    if (villager.isBaby()) position = position.subtract(0.0d, 1.0d, 0.0d);
                    villager.getLookControl().setLookAt(position);
                });
    }
}