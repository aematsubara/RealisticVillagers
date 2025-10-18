package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class BackToStay extends Behavior<Villager> {

    public BackToStay() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(@NotNull ServerLevel level, Villager villager) {
        return level.random.nextInt(20) == 0;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long game) {
        if (!(villager instanceof VillagerNPC npc) || npc.isStayingInPlace()) return;

        Brain<Villager> brain = npc.getBrain();

        Optional<Activity> activity = brain.getActiveNonCoreActivity();
        if (activity.isPresent() && npc.checkCurrentActivity(Activity.RAID, Activity.PRE_RAID, Activity.FIGHT)) {
            return;
        }

        if (!brain.hasMemoryValue(VillagerNPC.STAY_PLACE)) return;

        brain.setDefaultActivity(VillagerNPC.STAY);
        brain.setActiveActivityIfPossible(VillagerNPC.STAY);
    }
}