package me.matsubara.realisticvillagers.entity.v1_18_r2.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_18_r2.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Optional;

public class BackToStay extends Behavior<Villager> {

    public BackToStay() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return level.random.nextInt(20) == 0;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long game) {
        if (!(villager instanceof VillagerNPC npc) || npc.isStayingInPlace()) return;

        Brain<Villager> brain = npc.getBrain();

        Optional<Activity> activity = brain.getActiveNonCoreActivity();
        if (activity.isPresent() && is(activity.get(), Activity.RAID, Activity.PRE_RAID, Activity.FIGHT)) return;

        if (!brain.hasMemoryValue(VillagerNPC.STAY_PLACE)) return;

        brain.setDefaultActivity(VillagerNPC.STAY);
        brain.setActiveActivityIfPossible(VillagerNPC.STAY);
    }

    private boolean is(Activity toCheck, Activity... activities) {
        for (Activity activity : activities) {
            if (toCheck.equals(activity)) return true;
        }
        return false;
    }
}