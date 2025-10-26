package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.rest;

import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

public class SleepInBed extends net.minecraft.world.entity.ai.behavior.SleepInBed {

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, LivingEntity living) {
        return super.checkExtraStartConditions(level, living) && (!(living instanceof VillagerNPC npc) || npc.isDoingNothing(true));
    }
}