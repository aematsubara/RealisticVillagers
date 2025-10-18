package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour;

import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class GiveGiftToHero extends net.minecraft.world.entity.ai.behavior.GiveGiftToHero {

    public GiveGiftToHero(int duration) {
        super(duration);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return (!(villager instanceof VillagerNPC npc) || npc.isDoingNothing(true)) && super.checkExtraStartConditions(level, villager);
    }
}