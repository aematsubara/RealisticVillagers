package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.idle;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;

import java.util.Optional;

public class InteractWithBreed extends Behavior<Villager> {

    private final int interactionRangeSqr;
    private final float speedModifier;
    private final int maxDist;

    public InteractWithBreed(int interactionRange, float speedModifier, int maxDist) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT));
        this.interactionRangeSqr = interactionRange * interactionRange;
        this.speedModifier = speedModifier;
        this.maxDist = maxDist;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc) || npc.isLooting()) return;

        Brain<Villager> brain = npc.getBrain();

        Optional<NearestVisibleLivingEntities> nearestEntities = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (nearestEntities.isEmpty()) return;

        NearestVisibleLivingEntities nearest = nearestEntities.get();

        Optional<LivingEntity> possibleBreedTarget = nearest.findClosest(near -> {
            if (!(near instanceof VillagerNPC target)) return false;
            return target.distanceToSqr(npc) <= (double) interactionRangeSqr && npc.canBreedWith(target);
        });

        if (possibleBreedTarget.isEmpty()) return;

        AgeableMob breed = (AgeableMob) possibleBreedTarget.get();
        brain.setMemory(MemoryModuleType.BREED_TARGET, breed);
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(breed, true));
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(breed, false), speedModifier, maxDist));
    }
}