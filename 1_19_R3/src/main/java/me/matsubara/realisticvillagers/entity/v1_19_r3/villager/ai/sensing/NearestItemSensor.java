package me.matsubara.realisticvillagers.entity.v1_19_r3.villager.ai.sensing;

import com.google.common.collect.ImmutableSet;
import me.matsubara.realisticvillagers.entity.v1_19_r3.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class NearestItemSensor extends Sensor<Mob> {

    private static final long XZ_RANGE = 32L;
    private static final long Y_RANGE = 16L;
    private static final int MAX_DISTANCE_TO_WANTED_ITEM = 32;

    public NearestItemSensor() {
    }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(VillagerNPC.NEAREST_WANTED_ITEM);
    }

    @Override
    public void doTick(ServerLevel level, Mob mob) {
        Brain<?> brain = mob.getBrain();

        List<ItemEntity> items = level.getEntitiesOfClass(
                ItemEntity.class,
                mob.getBoundingBox().inflate(XZ_RANGE, Y_RANGE, XZ_RANGE),
                (item) -> true);
        if (items == null) return;

        items.sort(Comparator.comparingDouble(mob::distanceToSqr));

        Stream<ItemEntity> stream = items.stream()
                .filter((item) -> mob.wantsToPickUp(item.getItem()))
                .filter((item) -> item.closerThan(mob, MAX_DISTANCE_TO_WANTED_ITEM));

        brain.setMemory(VillagerNPC.NEAREST_WANTED_ITEM, stream.findFirst());
    }
}