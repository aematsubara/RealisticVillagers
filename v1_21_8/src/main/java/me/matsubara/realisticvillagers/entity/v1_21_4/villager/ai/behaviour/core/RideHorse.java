package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.pet.horse.HorseEating;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class RideHorse extends Behavior<Villager> {

    private final float speedModifier;

    private static final int DISTANCE_TO_START_RIDING = 2;

    public RideHorse(int duration, float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT), duration);
        this.speedModifier = speedModifier;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return Config.TAME_HORSES.asBool()
                && villager instanceof VillagerNPC npc
                && npc.isDoingNothing(true)
                && !npc.isPassenger()
                && isHorsePetVisible(npc)
                && !npc.checkCurrentActivity(Activity.REST, Activity.WORK, Activity.MEET, VillagerNPC.STAY);
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long game) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        LivingEntity horse = getNearestHorsePet(villager).get();
        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, horse);
        BehaviorUtils.lookAtEntity(villager, horse);
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        LivingEntity horse = getNearestHorsePet(villager).get();
        BehaviorUtils.lookAtEntity(villager, horse);

        if (isWithinRideDistance(villager, horse)) {
            villager.startRiding(horse, true);
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, horse, speedModifier, 0);
        }
    }

    private boolean isHorsePetVisible(Villager villager) {
        return getNearestHorsePet(villager).isPresent();
    }

    private Optional<LivingEntity> getNearestHorsePet(@NotNull Villager villager) {
        Optional<NearestVisibleLivingEntities> nearest = villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (nearest.isEmpty()) return Optional.empty();

        return nearest.get().findClosest(living -> living instanceof OwnableEntity ownable
                && ownable.getOwnerReference() != null
                && villager.getUUID().equals(ownable.getOwnerReference().getUUID())
                && living instanceof HorseEating
                && living instanceof AbstractHorse horse
                && horse.isTamed()
                && horse.isSaddled());
    }

    private boolean isWithinRideDistance(@NotNull Villager villager, @NotNull LivingEntity horse) {
        return villager.blockPosition().closerThan(horse.blockPosition(), DISTANCE_TO_START_RIDING);
    }
}