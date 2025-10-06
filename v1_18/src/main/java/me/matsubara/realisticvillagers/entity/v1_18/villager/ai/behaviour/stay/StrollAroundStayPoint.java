package me.matsubara.realisticvillagers.entity.v1_18.villager.ai.behaviour.stay;

import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StrollAroundStayPoint extends StrollAroundPoi {

    public StrollAroundStayPoint(MemoryModuleType<GlobalPos> memory, float speed, int distance) {
        super(memory, speed, distance);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, PathfinderMob mob) {
        return Config.STAY_STROLL_AROUND.asBool() && super.checkExtraStartConditions(level, mob);
    }
}