package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class LookAtTargetSink extends Behavior<Villager> {

    public LookAtTargetSink(int minDuration, int maxDuration) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_PRESENT), minDuration, maxDuration);
    }

    @Override
    public boolean canStillUse(ServerLevel level, @NotNull Villager villager, long time) {
        return villager.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                .filter((tracker) -> tracker.isVisibleBy(villager)).isPresent()
                && (!(villager instanceof VillagerNPC npc) || !npc.isShakingHead());
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    public void tick(ServerLevel level, @NotNull Villager villager, long time) {
        villager.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                .ifPresent((tracker) -> {
                    // Baby villagers as NPC look a bit higher than target location.
                    Vec3 position = tracker.currentPosition();

                    if (villager.isBaby() && !Config.DISABLE_SKINS.asBool()) {
                        position = position.subtract(0.0d, Config.BABY_LOOK_HEIGHT_OFFSET.asDouble(), 0.0d);
                    }

                    villager.getLookControl().setLookAt(position);
                });
    }
}