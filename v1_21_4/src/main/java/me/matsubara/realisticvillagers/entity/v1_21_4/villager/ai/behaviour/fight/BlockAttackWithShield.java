package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.NotNull;

public class BlockAttackWithShield extends Behavior<Villager> {

    private int delay;
    private int delayToUseAgain;

    public BlockAttackWithShield() {
        super(
                ImmutableMap.of(
                        MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                        MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, @NotNull Villager villager) {
        return villager.getOffhandItem().getItem() instanceof ShieldItem;
    }

    @Override
    public boolean canStillUse(ServerLevel level, @NotNull Villager villager, long time) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && checkExtraStartConditions(level, villager);
    }

    @Override
    public void tick(@NotNull ServerLevel level, @NotNull Villager villager, long time) {
        RandomSource random = level.getRandom();
        if (villager.isUsingItem() && villager.getUsedItemHand() == InteractionHand.OFF_HAND) {
            if (--delay <= 0) {
                stopBlocking(villager);

                // Delay to use again between 1 & 2s.
                delayToUseAgain = random.nextInt(20, 40);
            }
        } else if (random.nextFloat() < 0.25f && --delayToUseAgain <= 0) {
            // Start using shield.
            villager.startUsingItem(InteractionHand.OFF_HAND);

            // Random delay of use between 1.5s & 2s.
            delay = random.nextInt(30, 40);
        }
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        stopBlocking(villager);
    }

    @Override
    public boolean timedOut(long time) {
        return false;
    }

    private void stopBlocking(@NotNull Villager villager) {
        if (villager.isUsingItem() && villager.getUsedItemHand() == InteractionHand.OFF_HAND)
            villager.releaseUsingItem();
    }

    public static boolean notUsingShield(@NotNull Villager villager) {
        return !(villager.getOffhandItem().getItem() instanceof ShieldItem)
                || !villager.isUsingItem()
                || villager.getUsedItemHand() != InteractionHand.OFF_HAND;
    }
}