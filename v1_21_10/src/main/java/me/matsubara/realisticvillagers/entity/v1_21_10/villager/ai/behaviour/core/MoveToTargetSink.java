package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Optional;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class MoveToTargetSink extends Behavior<Villager> {

    private int remainingCooldown;
    private @Nullable Path path;
    private @Nullable BlockPos lastTargetPos;
    private float speedModifier;

    private static final int MAX_COOLDOWN_BEFORE_RETRYING = 40;

    public MoveToTargetSink() {
        this(150, 250);
    }

    public MoveToTargetSink(int minDuration, int maxDuration) {
        super(ImmutableMap.of(
                        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                        MemoryModuleType.PATH, MemoryStatus.VALUE_ABSENT,
                        MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT),
                minDuration,
                maxDuration);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (villager instanceof VillagerNPC npc && npc.isShakingHead()) return false;

        if (remainingCooldown > 0) {
            --remainingCooldown;
            return false;
        }

        Brain<Villager> brain = villager.getBrain();
        WalkTarget target = brain.getMemory(MemoryModuleType.WALK_TARGET).get();

        boolean reached = reachedTarget(villager, target);
        if (!reached && tryComputePath(villager, target, level.getGameTime())) {
            lastTargetPos = target.getTarget().currentBlockPosition();
            return true;
        }

        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        if (reached) brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        return false;
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        if (path == null || lastTargetPos == null) return false;

        Optional<WalkTarget> target = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        boolean isSpectator = target.map(MoveToTargetSink::isWalkTargetSpectator).orElse(false);

        return !getNavigation(villager).isDone()
                && target.isPresent()
                && !reachedTarget(villager, target.get())
                && !isSpectator;
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)
                && !reachedTarget(villager, brain.getMemory(MemoryModuleType.WALK_TARGET).get())
                && getNavigation(villager).isStuck()) {
            remainingCooldown = level.getRandom().nextInt(MAX_COOLDOWN_BEFORE_RETRYING);
        }

        getNavigation(villager).stop();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        path = null;
    }

    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);
        AbstractHorse vehicle = getValidVehicle(villager);
        float extra = vehicle != null ? 0.85f : 0.0f;
        getNavigation(villager).moveTo(path, speedModifier + extra);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        Path path = getNavigation(villager).getPath();
        Brain<Villager> brain = villager.getBrain();

        if (this.path != path) {
            this.path = path;
            brain.setMemory(MemoryModuleType.PATH, path);
        }

        if (path == null || lastTargetPos == null) return;

        WalkTarget target = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
        if (target.getTarget().currentBlockPosition().distSqr(lastTargetPos) > 4.0d && tryComputePath(villager, target, level.getGameTime())) {
            lastTargetPos = target.getTarget().currentBlockPosition();
            start(level, villager, time);
        }
    }

    private boolean tryComputePath(Villager villager, @NotNull WalkTarget target, long time) {
        BlockPos pos = target.getTarget().currentBlockPosition();
        path = getNavigation(villager).createPath(pos, 0);
        speedModifier = target.getSpeedModifier();

        Brain<Villager> brain = villager.getBrain();
        if (reachedTarget(villager, target)) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            return false;
        }

        boolean canReach = path != null && path.canReach();
        if (canReach) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        } else if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, time);
        }

        if (path != null) return true;

        Vec3 random = DefaultRandomPos.getPosTowards(villager, 10, 7, Vec3.atBottomCenterOf(pos), 1.5707963705062866d);
        if (random != null) {
            path = getNavigation(villager).createPath(random.x, random.y, random.z, 0);
            return path != null;
        }

        return false;
    }

    private boolean reachedTarget(@NotNull Villager villager, @NotNull WalkTarget target) {
        return target.getTarget().currentBlockPosition().distManhattan(villager.blockPosition()) <= target.getCloseEnoughDist();
    }

    private static boolean isWalkTargetSpectator(@NotNull WalkTarget target) {
        return target.getTarget() instanceof EntityTracker tracker && tracker.getEntity().isSpectator();
    }

    private PathNavigation getNavigation(Villager villager) {
        AbstractHorse vehicle = getValidVehicle(villager);
        return vehicle != null ? vehicle.getNavigation() : villager.getNavigation();
    }

    private @Nullable AbstractHorse getValidVehicle(@NotNull Villager villager) {
        return villager.getVehicle() instanceof AbstractHorse horse && horse.isTamed() && horse.inventory.getItem(0).is(Items.SADDLE) ? horse : null;
    }
}