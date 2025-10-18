package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.NotNull;

public class SetRaidStatus extends Behavior<Villager> {

    public SetRaidStatus() {
        super(ImmutableMap.of());
    }

    @Override
    public boolean checkExtraStartConditions(@NotNull ServerLevel level, Villager villager) {
        return level.random.nextInt(20) == 0;
    }

    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        Brain<Villager> rain = villager.getBrain();

        if (villager instanceof VillagerNPC npc && npc.isFighting()) return;

        Raid raid = level.getRaidAt(villager.blockPosition());
        if (raid == null) return;

        if (raid.hasFirstWaveSpawned() && !raid.isBetweenWaves()) {
            rain.setDefaultActivity(Activity.RAID);
            rain.setActiveActivityIfPossible(Activity.RAID);
        } else {
            rain.setDefaultActivity(Activity.PRE_RAID);
            rain.setActiveActivityIfPossible(Activity.PRE_RAID);
        }
    }
}