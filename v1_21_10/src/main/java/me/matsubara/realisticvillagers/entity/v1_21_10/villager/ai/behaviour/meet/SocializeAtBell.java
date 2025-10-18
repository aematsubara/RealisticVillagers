package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.meet;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SocializeAtBell extends Behavior<Villager> {

    private static final float SPEED_MODIFIER = 0.3f;

    public SocializeAtBell() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public boolean checkExtraStartConditions(@NotNull ServerLevel level, @NotNull Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        Optional<GlobalPos> meetPos = brain.getMemory(MemoryModuleType.MEETING_POINT);

        return level.getRandom().nextInt(100) == 0
                && (!(villager instanceof VillagerNPC npc) || npc.isDoingNothing(true))
                && meetPos.isPresent()
                && level.dimension() == meetPos.get().dimension()
                && meetPos.get().pos().closerToCenterThan(villager.position(), 4.0d)
                && brain
                .getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .get()
                .contains((nearest) -> EntityType.VILLAGER.equals(nearest.getType()));
    }

    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();

        brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)
                .flatMap((nearest) -> nearest.findClosest((living) -> EntityType.VILLAGER.equals(living.getType())
                        && living.distanceToSqr(villager) <= 32.0d))
                .filter(nearest -> !(nearest instanceof VillagerNPC npc) || npc.isDoingNothing(true))
                .ifPresent((target) -> {
                    brain.setMemory(MemoryModuleType.INTERACTION_TARGET, target);
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
                    brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), SPEED_MODIFIER, 1));
                });
    }
}