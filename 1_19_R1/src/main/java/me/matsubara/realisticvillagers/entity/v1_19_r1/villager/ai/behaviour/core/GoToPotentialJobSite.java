package me.matsubara.realisticvillagers.entity.v1_19_r1.villager.ai.behaviour.core;

import me.matsubara.realisticvillagers.entity.v1_19_r1.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

import java.util.function.Predicate;

public class GoToPotentialJobSite extends net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite {

    public GoToPotentialJobSite(float speedModifier) {
        super(speedModifier);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return super.checkExtraStartConditions(level, villager) && test(villager, npc -> npc.isDoingNothing(true));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return super.canStillUse(level, villager, time) && test(villager, npc -> !npc.isExpecting());
    }

    private boolean test(Villager villager, Predicate<VillagerNPC> predicate) {
        return !(villager instanceof VillagerNPC npc) || predicate.test(npc);
    }
}