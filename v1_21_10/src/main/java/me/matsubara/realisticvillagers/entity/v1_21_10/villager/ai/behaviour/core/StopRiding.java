package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.NotNull;

public class StopRiding extends Behavior<Villager> {

    public StopRiding() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(@NotNull ServerLevel level, Villager villager) {
        return level.random.nextInt(20) == 0;
    }

    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long game) {
        if (!villager.isPassenger()) return;
        if (!(villager instanceof VillagerNPC npc)) return;

        if (npc.checkCurrentActivity(Activity.REST, Activity.WORK, VillagerNPC.STAY)
                || (npc.checkCurrentActivity(Activity.MEET) && !npc.isFollowing())) {
            villager.stopRiding();
        }
    }
}