package me.matsubara.realisticvillagers.entity.v1_20_r2.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.data.TargetReason;
import me.matsubara.realisticvillagers.entity.v1_20_r2.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;

public class ReactTo extends Behavior<Villager> {

    private final MemoryModuleType<Long> memory;

    public ReactTo(MemoryModuleType<Long> memory) {
        super(ImmutableMap.of(memory, MemoryStatus.VALUE_PRESENT));
        this.memory = memory;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;
        Brain<Villager> brain = villager.getBrain();

        Raid raid = ((ServerLevel) villager.level()).getRaidAt(villager.blockPosition());

        npc.stopAllInteractions();

        if (memory.equals(VillagerNPC.HEARD_HORN_TIME) && npc.canAttack()) {
            // If HEARD_HORN_TIME is present, is the same for PLAYER_HORN.
            @SuppressWarnings("OptionalGetWithoutIsPresent") Player playerHorn = brain.getMemory(VillagerNPC.PLAYER_HORN).get();
            VillagerPanicTrigger.handleFightReaction(brain, playerHorn, TargetReason.HORN);
            brain.eraseMemory(memory);
            return;
        }

        // Villagers will hide for 15 seconds and then stop (if not in raid && can't attack).
        if (raid == null && !npc.canAttack()) {
            brain.setActiveActivityIfPossible(Activity.HIDE);
        }
    }
}