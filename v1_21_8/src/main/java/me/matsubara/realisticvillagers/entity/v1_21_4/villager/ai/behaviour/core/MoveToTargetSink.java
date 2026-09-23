package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
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

/**
 * Core movement processor for villager brain AI.
 *
 * KEY FIX: Uses WalkTarget.getCloseEnoughDist() as the path reach parameter
 * instead of hardcoded 0. With reach=0, the pathfinder must reach the EXACT
 * target block — which fails in almost all natural terrain scenarios (slopes,
 * half-blocks, water, etc.), causing villagers to stand still.
 * With reach=closeEnoughDist (e.g. 1-2), pathfinder finds a valid nearby
 * endpoint, dramatically increasing path success rate.
 */
@SuppressWarnings("OptionalGetWithoutIsPresent")
public class MoveToTargetSink extends Behavior<Villager> {

    private int stuckCooldown;
    private @Nullable Path path;
    private @Nullable BlockPos lastTargetPos;
    private float speedModifier;

    private static final int MAX_STUCK_COOLDOWN = 40;

    public MoveToTargetSink() {
        super(ImmutableMap.of(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED,
                MemoryModuleType.PATH, MemoryStatus.REGISTERED,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (villager instanceof VillagerNPC npc && npc.isShakingHead()) return false;

        if (stuckCooldown > 0) {
            stuckCooldown--;
            return false;
        }

        Brain<Villager> brain = villager.getBrain();
        WalkTarget target = brain.getMemory(MemoryModuleType.WALK_TARGET).get();

        if (isCloseEnough(villager, target)) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            return false;
        }

        if (computePath(villager, target, level.getGameTime())) {
            lastTargetPos = target.getTarget().currentBlockPosition();
            return true;
        }

        // Path computation failed entirely. 
        // We don't immediately erase the target to avoid flickering.
        return false;
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        if (path == null || lastTargetPos == null) return false;

        Optional<WalkTarget> optTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        if (optTarget.isEmpty()) return false;

        WalkTarget target = optTarget.get();
        if (isWalkTargetSpectator(target)) return false;
        if (isCloseEnough(villager, target)) return false;

        return !getNavigation(villager).isDone();
    }

    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.PATH, path);

        AbstractHorse vehicle = getValidVehicle(villager);
        float bonus = vehicle != null ? 0.85f : 0.0f;
        getNavigation(villager).moveTo(path, speedModifier + bonus);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        PathNavigation nav = getNavigation(villager);
        Brain<Villager> brain = villager.getBrain();

        Path livePath = nav.getPath();
        if (this.path != livePath) {
            this.path = livePath;
            brain.setMemory(MemoryModuleType.PATH, livePath);
        }

        if (livePath == null || lastTargetPos == null) return;

        Optional<WalkTarget> optTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
        if (optTarget.isEmpty()) return;

        WalkTarget target = optTarget.get();
        BlockPos currentTargetPos = target.getTarget().currentBlockPosition();

        if (currentTargetPos.distSqr(lastTargetPos) > 4.0) {
            if (computePath(villager, target, level.getGameTime())) {
                lastTargetPos = currentTargetPos;
                start(level, villager, time);
            }
        }
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        PathNavigation nav = getNavigation(villager);
        Brain<Villager> brain = villager.getBrain();

        if (brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            WalkTarget t = brain.getMemory(MemoryModuleType.WALK_TARGET).get();
            if (!isCloseEnough(villager, t) && nav.isStuck()) {
                stuckCooldown = villager.getRandom().nextInt(MAX_STUCK_COOLDOWN) + 10;
            }
        }

        nav.stop();
        // Removed: brain.eraseMemory(MemoryModuleType.WALK_TARGET); 
        brain.eraseMemory(MemoryModuleType.PATH);
        path = null;
    }

    private boolean computePath(Villager villager, @NotNull WalkTarget target, long gameTime) {
        BlockPos targetPos = target.getTarget().currentBlockPosition();
        speedModifier = target.getSpeedModifier();
        Brain<Villager> brain = villager.getBrain();

        int reach = target.getCloseEnoughDist();
        path = getNavigation(villager).createPath(targetPos, reach);

        if (path != null && path.canReach()) {
            brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            return true;
        }

        if (!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            brain.setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, gameTime);
        }

        if (path != null) return true;

        Vec3 towards = DefaultRandomPos.getPosTowards(
                villager, 10, 7,
                Vec3.atBottomCenterOf(targetPos),
                Math.PI / 2.0);
        if (towards != null) {
            path = getNavigation(villager).createPath(towards.x, towards.y, towards.z, 0);
            if (path != null) return true;
        }

        return false;
    }

    private boolean isCloseEnough(@NotNull Villager villager, @NotNull WalkTarget target) {
        return target.getTarget().currentBlockPosition()
                .distManhattan(villager.blockPosition()) <= target.getCloseEnoughDist();
    }

    private static boolean isWalkTargetSpectator(@NotNull WalkTarget target) {
        return target.getTarget() instanceof EntityTracker tracker
                && tracker.getEntity().isSpectator();
    }

    private PathNavigation getNavigation(Villager villager) {
        AbstractHorse vehicle = getValidVehicle(villager);
        return vehicle != null ? vehicle.getNavigation() : villager.getNavigation();
    }

    private @Nullable AbstractHorse getValidVehicle(@NotNull Villager villager) {
        return villager.getVehicle() instanceof AbstractHorse horse
                && horse.isTamed()
                && horse.inventory.getItem(0).is(Items.SADDLE) ? horse : null;
    }
}