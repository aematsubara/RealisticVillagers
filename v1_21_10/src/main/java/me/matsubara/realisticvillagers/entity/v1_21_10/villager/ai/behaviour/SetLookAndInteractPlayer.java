package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

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
        // Don't interact if the NPC is expecting something or looting.
        return !(villager instanceof VillagerNPC npc) || (!npc.isExpecting() && !npc.isLooting());
    }

    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();

        Optional<NearestVisibleLivingEntities> nearestEntities = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (nearestEntities.isEmpty()) return;

        Optional<LivingEntity> closest = nearestEntities.get().findClosest((nearest) -> isMatchingTarget(villager, nearest));
        if (closest.isEmpty()) return;

        LivingEntity living = closest.get();
        brain.setMemory(MemoryModuleType.INTERACTION_TARGET, living);
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(living, true));
    }

    private boolean isMatchingTarget(Villager villager, @NotNull LivingEntity target) {
        return target.distanceToSqr(villager) <= interactionRangeSqr && target.getType().equals(EntityType.PLAYER);
    }
}