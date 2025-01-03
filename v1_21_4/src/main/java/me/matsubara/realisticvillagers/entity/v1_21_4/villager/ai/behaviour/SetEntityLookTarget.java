package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Predicate;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "unused"})
public class SetEntityLookTarget extends Behavior<LivingEntity> {

    private final Predicate<LivingEntity> predicate;
    private final float maxDistSqr;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<LivingEntity> nearestEntityMatchingTest;

    public SetEntityLookTarget(TagKey<EntityType<?>> key, float maxDist) {
        this((living) -> living.getType().is(key), maxDist);
    }

    public SetEntityLookTarget(MobCategory category, float maxDist) {
        this((living) -> category.equals(living.getType().getCategory()), maxDist);
    }

    public SetEntityLookTarget(EntityType<?> type, float maxDist) {
        this((living) -> type.equals(living.getType()), maxDist);
    }

    public SetEntityLookTarget(float maxDist) {
        this((living) -> true, maxDist);
    }

    public SetEntityLookTarget(Predicate<LivingEntity> predicate, float maxDist) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT));
        this.nearestEntityMatchingTest = Optional.empty();
        this.predicate = predicate;
        this.maxDistSqr = maxDist * maxDist;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, LivingEntity living) {
        if (living instanceof VillagerNPC npc && npc.isLooting()) return false;

        NearestVisibleLivingEntities nearest = living.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get();
        nearestEntityMatchingTest = nearest.findClosest(predicate.and((near) -> living.distanceToSqr(near) <= (double) maxDistSqr
                && !living.hasPassenger(near)));
        return nearestEntityMatchingTest.isPresent();
    }

    @Override
    public void start(ServerLevel level, @NotNull LivingEntity living, long time) {
        living.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(nearestEntityMatchingTest.get(), true));
        nearestEntityMatchingTest = Optional.empty();
    }
}