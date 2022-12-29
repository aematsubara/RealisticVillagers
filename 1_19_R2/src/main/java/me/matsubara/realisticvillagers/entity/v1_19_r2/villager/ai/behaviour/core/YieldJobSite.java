package me.matsubara.realisticvillagers.entity.v1_19_r2.villager.ai.behaviour.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Optional;

public class YieldJobSite {

    public static BehaviorControl<Villager> create(float speedModifier) {
        return BehaviorBuilder.create((behaviorBuilder) -> behaviorBuilder
                .group(
                        behaviorBuilder.present(MemoryModuleType.POTENTIAL_JOB_SITE),
                        behaviorBuilder.absent(MemoryModuleType.JOB_SITE),
                        behaviorBuilder.present(MemoryModuleType.NEAREST_LIVING_ENTITIES),
                        behaviorBuilder.registered(MemoryModuleType.WALK_TARGET),
                        behaviorBuilder.registered(MemoryModuleType.LOOK_TARGET))
                .apply(behaviorBuilder, (potentialSite, jobSite, nearestLiving, walkTarget, lookTarget) -> (level, villager, time) -> {
                    if (villager.isBaby()) return false;
                    if (villager.getVillagerData().getProfession() == VillagerProfession.NONE) return false;
                    if (!GoToPotentialJobSite.isDoingNothing(villager)) return false;

                    BlockPos pos = behaviorBuilder.get(potentialSite).pos();
                    Optional<Holder<PoiType>> possible = level.getPoiManager().getType(pos);
                    if (possible.isEmpty()) return true;

                    behaviorBuilder.get(nearestLiving).stream()
                            .filter((living) -> living instanceof Villager && living != villager)
                            .map((living) -> (Villager) living)
                            .filter(LivingEntity::isAlive)
                            .filter((living) -> nearbyWantsJobsite(possible.get(), living, pos))
                            .findFirst()
                            .ifPresent((living) -> {
                                walkTarget.erase();
                                lookTarget.erase();
                                potentialSite.erase();

                                if (living.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent()) return;

                                BehaviorUtils.setWalkAndLookTargetMemories(living, pos, speedModifier, 1);
                                living.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), pos));
                                DebugPackets.sendPoiTicketCountPacket(level, pos);

                            });
                    return true;
                }));
    }

    private static boolean nearbyWantsJobsite(Holder<PoiType> type, Villager villager, BlockPos pos) {
        if (villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isPresent()) {
            return false;
        }

        Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (villager.getVillagerData().getProfession().heldJobSite().test(type)) {
            return jobSite.isEmpty() ? canReachPos(villager, pos, type.value()) : jobSite.get().pos().equals(pos);
        }

        return false;
    }

    private static boolean canReachPos(PathfinderMob mob, BlockPos pos, PoiType type) {
        Path path = mob.getNavigation().createPath(pos, type.validRange());
        return path != null && path.canReach();
    }
}