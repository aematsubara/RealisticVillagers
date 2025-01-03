package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.sensing;

import com.google.common.collect.ImmutableSet;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class NearestItemSensor extends Sensor<Mob> {

    private static final long ITEM_RANGE = 32L;
    private static final long TNT_RANGE = 8L;

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(VillagerNPC.NEAREST_WANTED_ITEM, VillagerNPC.NEAREST_PRIMED_TNT);
    }

    @Override
    public void doTick(@NotNull ServerLevel level, Mob mob) {
        provideNearest(mob,
                level.getEntitiesOfClass(
                        ItemEntity.class,
                        mob.getBoundingBox().inflate(ITEM_RANGE, (double) ITEM_RANGE / 2, ITEM_RANGE)),
                ITEM_RANGE,
                VillagerNPC.NEAREST_WANTED_ITEM,
                (entity) -> {
                    if (!(entity instanceof ItemEntity item)) return false;
                    return (!(mob instanceof VillagerNPC npc)
                            || !item.getBukkitEntity().getPersistentDataContainer().has(npc.getPlugin().getIgnoreItemKey(), PersistentDataType.INTEGER))
                            && mob.wantsToPickUp(level, item.getItem());
                });

        provideNearest(mob,
                level.getEntitiesOfClass(
                        PrimedTnt.class,
                        mob.getBoundingBox().inflate(TNT_RANGE)),
                TNT_RANGE,
                VillagerNPC.NEAREST_PRIMED_TNT,
                entity -> true);
    }

    private <T extends Entity> void provideNearest(Mob mob, List<T> entities, double closerThan, MemoryModuleType<T> memory, Predicate<Entity> filter) {
        if (entities == null) return;

        entities.sort(Comparator.comparingDouble(mob::distanceToSqr));
        Stream<T> stream = entities.stream().filter(filter).filter((entity) -> entity.closerThan(mob, closerThan));
        mob.getBrain().setMemory(memory, stream.findFirst());
    }
}