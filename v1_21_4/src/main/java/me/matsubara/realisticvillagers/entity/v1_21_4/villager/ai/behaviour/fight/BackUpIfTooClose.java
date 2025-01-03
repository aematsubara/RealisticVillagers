package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

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
    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(getTarget(villager), true));
        villager.getMoveControl().strafe(-strafeSpeed, 0.0f);
        villager.setYRot(Mth.rotateIfNecessary(villager.getYRot(), villager.yHeadRot, 0.0f));

        // Random jump to be more "realistic".
        if (villager.getRandom().nextFloat() < Config.BACK_UP_JUMP_CHANCE.asFloat()) {
            villager.getJumpControl().jump();
        }
    }

    private boolean isTargetVisible(@NotNull Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().contains(getTarget(villager));
    }

    private boolean isTargetTooClose(Villager villager) {
        return getTarget(villager).closerThan(villager, tooCloseDistance.apply((VillagerNPC) villager));
    }

    private @NotNull LivingEntity getTarget(@NotNull Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }
}