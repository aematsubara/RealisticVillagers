package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class GoToPotentialJobSite extends net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite {

    public GoToPotentialJobSite(float speedModifier) {
        super(speedModifier);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return super.checkExtraStartConditions(level, villager) && isDoingNothing(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return super.canStillUse(level, villager, time) && isDoingNothing(villager);
    }

    public static boolean isDoingNothing(Villager villager) {
        return !(villager instanceof VillagerNPC npc) || npc.isDoingNothing(ChangeItemType.SHOWING_TRADES, ChangeItemType.EATING);
    }
}