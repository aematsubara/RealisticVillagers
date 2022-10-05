package me.matsubara.realisticvillagers.entity.v1_19_r1.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_19_r1.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;

import java.util.function.Function;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class BackUpIfTooClose extends Behavior<Villager> {

    private final Function<VillagerNPC, Integer> tooCloseDistance;
    private final float strafeSpeed;

    public BackUpIfTooClose(Function<VillagerNPC, Integer> tooCloseDistance, float strafeSpeed) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.VALUE_PRESENT));
        this.tooCloseDistance = tooCloseDistance;
        this.strafeSpeed = strafeSpeed;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return isTargetVisible(villager) && isTargetTooClose(villager) && villager instanceof VillagerNPC;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(getTarget(villager), true));
        villager.getMoveControl().strafe(-strafeSpeed, 0.0f);
        villager.setYRot(Mth.rotateIfNecessary(villager.getYRot(), villager.yHeadRot, 0.0f));
    }

    private boolean isTargetVisible(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().contains(getTarget(villager));
    }

    private boolean isTargetTooClose(Villager villager) {
        return getTarget(villager).closerThan(villager, tooCloseDistance.apply((VillagerNPC) villager));
    }

    private LivingEntity getTarget(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }
}