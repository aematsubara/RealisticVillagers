package me.matsubara.realisticvillagers.entity.v1_19_r1.villager.ai.behaviour.core;

import me.matsubara.realisticvillagers.entity.v1_19_r1.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class GoToPotentialJobSite extends net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite {

    public GoToPotentialJobSite(float speedModifier) {
        super(speedModifier);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return super.checkExtraStartConditions(level, villager) && (!(villager instanceof VillagerNPC npc) || npc.isDoingNothing(true));
    }
}