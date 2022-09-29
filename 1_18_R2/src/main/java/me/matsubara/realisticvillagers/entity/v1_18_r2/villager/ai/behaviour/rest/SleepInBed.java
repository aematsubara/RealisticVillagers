package me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.rest;

import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

public class SleepInBed extends net.minecraft.world.entity.ai.behavior.SleepInBed {

    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return super.checkExtraStartConditions(level, villager) && (!(villager instanceof VillagerNPC npc) || npc.isDoingNothing(true));
    }
}