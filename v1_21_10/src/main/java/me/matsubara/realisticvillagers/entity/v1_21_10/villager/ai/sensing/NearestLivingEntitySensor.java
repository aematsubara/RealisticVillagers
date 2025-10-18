package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.sensing;

import com.google.common.collect.ImmutableSet;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class NearestLivingEntitySensor extends Sensor<LivingEntity> {

    @Override
    public void doTick(@NotNull ServerLevel level, @NotNull LivingEntity living) {
        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                living.getBoundingBox().inflate(16.0d, 16.0d, 16.0d),
                (near) -> near != living
                        && near.isAlive()
                        && (!(near instanceof VillagerNPC npc) || !npc.getPlugin().getTracker().isInvalid(npc.getBukkitEntity(), true)));
        entities.sort(Comparator.comparingDouble(living::distanceToSqr));

        Brain<?> brain = living.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, entities);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(level, living, entities));
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }
}