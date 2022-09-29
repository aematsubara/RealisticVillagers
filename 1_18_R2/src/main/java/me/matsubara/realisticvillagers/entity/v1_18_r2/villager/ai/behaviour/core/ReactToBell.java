package me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;

public class ReactToBell extends Behavior<Villager> {

    public ReactToBell() {
        super(ImmutableMap.of(MemoryModuleType.HEARD_BELL_TIME, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;
        Brain<Villager> brain = villager.getBrain();

        Raid raid = ((ServerLevel) villager.level).getRaidAt(villager.blockPosition());

        // Villagers will hide for 15 seconds and then stop (if not in raid && cann't attack).
        if (raid == null && !npc.canAttack()) {
            brain.setActiveActivityIfPossible(Activity.HIDE);
        }
    }
}