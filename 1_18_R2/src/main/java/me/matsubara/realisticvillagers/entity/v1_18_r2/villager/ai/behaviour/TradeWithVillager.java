package me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour;

import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.meet.SocializeAtBell;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.npc.Villager;

import java.util.Optional;

public class TradeWithVillager extends net.minecraft.world.entity.ai.behavior.TradeWithVillager {

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        return brain.getMemory(MemoryModuleType.INTERACTION_TARGET)
                .filter(living -> living.getType() == EntityType.VILLAGER
                        && entityIsVisible(brain, living)
                        && (!(living instanceof VillagerNPC npc) || SocializeAtBell.isValidSocializeTarget(npc)))
                .isPresent();
    }

    private boolean entityIsVisible(Brain<Villager> brain, LivingEntity living) {
        Optional<NearestVisibleLivingEntities> optional = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        return optional.isPresent() && optional.get().contains(living);
    }
}