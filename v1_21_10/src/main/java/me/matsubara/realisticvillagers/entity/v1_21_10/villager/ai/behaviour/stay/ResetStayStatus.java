package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.stay;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.NotNull;

public class ResetStayStatus extends Behavior<Villager> {

    public ResetStayStatus() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(@NotNull ServerLevel level, Villager villager) {
        return level.random.nextInt(20) == 0;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long game) {
        if (!(villager instanceof VillagerNPC npc)) return;
        if (!npc.isStayingInPlace()) return;
        if (level.getPlayerByUUID(npc.getInteractingWith()) != null) return;

        npc.stopInteracting();

        Brain<Villager> brain = npc.getBrain();
        brain.eraseMemory(VillagerNPC.STAY_PLACE);
        brain.setDefaultActivity(Activity.IDLE);
        brain.updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }
}