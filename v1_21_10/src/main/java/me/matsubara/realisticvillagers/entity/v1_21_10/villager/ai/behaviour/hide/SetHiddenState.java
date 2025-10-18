package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.hide;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class SetHiddenState extends Behavior<Villager> {

    private static final int HIDE_TIMEOUT = 300;

    private final int stayHiddenTicks;
    private final int closeEnoughDist;
    private final MemoryModuleType<Long> heardMemory;
    private int ticksHidden;

    public SetHiddenState(int hiddenSeconds, int closeEnoughDist, MemoryModuleType<Long> heardMemory) {
        super(ImmutableMap.of(
                MemoryModuleType.HIDING_PLACE, MemoryStatus.VALUE_PRESENT,
                heardMemory, MemoryStatus.VALUE_PRESENT));
        this.stayHiddenTicks = hiddenSeconds * 20;
        this.closeEnoughDist = closeEnoughDist;
        this.heardMemory = heardMemory;
        this.ticksHidden = 0;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long game) {
        Brain<Villager> brain = villager.getBrain();

        Optional<Long> heardTime = brain.getMemory(heardMemory);
        boolean timeout = heardTime.get() + HIDE_TIMEOUT <= game;

        if (ticksHidden <= stayHiddenTicks && !timeout) {
            BlockPos hidePlace = brain.getMemory(MemoryModuleType.HIDING_PLACE).get().pos();
            if (hidePlace.closerThan(villager.blockPosition(), closeEnoughDist)) {
                ++ticksHidden;
            }
            return;
        }

        brain.eraseMemory(heardMemory);
        if (heardMemory.equals(VillagerNPC.HEARD_HORN_TIME)) brain.eraseMemory(VillagerNPC.PLAYER_HORN);
        brain.eraseMemory(MemoryModuleType.HIDING_PLACE);
        brain.updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
        ticksHidden = 0;
    }
}