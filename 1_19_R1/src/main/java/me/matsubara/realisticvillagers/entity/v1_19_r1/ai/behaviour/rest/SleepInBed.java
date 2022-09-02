package me.matsubara.realisticvillagers.entity.v1_19_r1.ai.behaviour.rest;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_19_r1.VillagerNPC;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class SleepInBed extends Behavior<Villager> {

    public static final int COOLDOWN_AFTER_BEING_WOKEN = 100;
    private long nextOkStartTime;

    public SleepInBed() {
        super(ImmutableMap.of(
                MemoryModuleType.HOME, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.LAST_WOKEN, MemoryStatus.REGISTERED));
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (villager instanceof VillagerNPC npc && !npc.isDoingNothing()) {
            return false;
        }

        if (villager.isPassenger()) return false;

        Brain<Villager> brain = villager.getBrain();

        GlobalPos pos = brain.getMemory(MemoryModuleType.HOME).get();
        if (level.dimension() != pos.dimension()) return false;

        Optional<Long> lastWoken = brain.getMemory(MemoryModuleType.LAST_WOKEN);
        if (lastWoken.isPresent()) {
            long elapsed = level.getGameTime() - lastWoken.get();
            if (elapsed > 0L && elapsed < COOLDOWN_AFTER_BEING_WOKEN) return false;
        }

        BlockState state = level.getBlockState(pos.pos());
        return pos.pos().closerToCenterThan(villager.position(), 2.0d)
                && state.is(BlockTags.BEDS)
                && !(Boolean) state.getValue(BedBlock.OCCUPIED);
    }

    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty()) return false;

        BlockPos pos = home.get().pos();
        return villager.getBrain().isActive(Activity.REST)
                && villager.getY() > (double) pos.getY() + 0.4d
                && pos.closerToCenterThan(villager.position(), 1.14d);
    }

    protected void start(ServerLevel level, Villager villager, long time) {
        if (time > nextOkStartTime) {
            InteractWithDoor.closeDoorsThatIHaveOpenedOrPassedThrough(level, villager, null, null);
            villager.startSleeping(villager.getBrain().getMemory(MemoryModuleType.HOME).get().pos());
        }
    }

    protected boolean timedOut(long time) {
        return false;
    }

    protected void stop(ServerLevel level, Villager villager, long time) {
        if (villager.isSleeping()) {
            villager.stopSleeping();
            nextOkStartTime = time + 40L;
        }
    }
}