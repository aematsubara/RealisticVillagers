package me.matsubara.realisticvillagers.entity.v1_20_r2.villager.ai.behaviour.core;

import me.matsubara.realisticvillagers.entity.v1_20_r2.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

public class MoveToTargetSink extends net.minecraft.world.entity.ai.behavior.MoveToTargetSink {

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Mob mob) {
        return (!(mob instanceof VillagerNPC npc) || !npc.isShakingHead()) && super.checkExtraStartConditions(level, mob);
    }
}