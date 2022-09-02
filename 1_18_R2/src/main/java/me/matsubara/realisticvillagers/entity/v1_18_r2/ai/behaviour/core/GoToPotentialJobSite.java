package me.matsubara.realisticvillagers.entity.v1_18_r2.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_18_r2.VillagerNPC;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class GoToPotentialJobSite extends Behavior<Villager> {

    private final static int TICKS_UNTIL_TIMEOUT = 1200;
    private final float speedModifier;

    public GoToPotentialJobSite(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.POTENTIAL_JOB_SITE, MemoryStatus.VALUE_PRESENT), TICKS_UNTIL_TIMEOUT);
        this.speedModifier = speedModifier;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return villager.getBrain().getActiveNonCoreActivity()
                .map((activity) -> activity == Activity.IDLE
                        || activity == Activity.WORK
                        || activity == Activity.PLAY)
                .orElse(true)
                && (!(villager instanceof VillagerNPC npc) || npc.isDoingNothing());
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE)
                && checkExtraStartConditions(level, villager);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        BehaviorUtils.setWalkAndLookTargetMemories(
                villager,
                villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).get().pos(),
                speedModifier,
                1);
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();

        brain.getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).ifPresent((globalPos) -> {
            BlockPos pos = globalPos.pos();
            ServerLevel jobSiteLevel = level.getServer().getLevel(globalPos.dimension());
            if (jobSiteLevel != null) {
                PoiManager poiManager = jobSiteLevel.getPoiManager();
                if (poiManager.exists(pos, (poi) -> true)) {
                    poiManager.release(pos);
                }
                DebugPackets.sendPoiTicketCountPacket(level, pos);
            }
        });

        brain.eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }
}