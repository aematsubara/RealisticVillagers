package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerFoodLevelChangeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public class EatCake extends Behavior<Villager> {

    private BlockPos cakePos;
    private int tryAgain;

    private static final int SEARCH_RANGE = 5;
    private static final int TRY_AGAIN_COOLDOWN = 1200;

    public EatCake() {
        super(ImmutableMap.of(), 100);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (tryAgain > 0) {
            tryAgain--;
            return false;
        }

        boolean canStart = canStart(level, villager);
        if (!canStart) {
            // Try again in 15 seconds.
            tryAgain = TRY_AGAIN_COOLDOWN / 4;
        }
        return canStart;
    }

    private boolean canStart(ServerLevel level, @NotNull Villager villager) {
        if (villager.isSleeping()) return false;

        if (!(villager instanceof VillagerNPC npc)
                || !npc.checkCurrentActivity(Activity.IDLE)
                || !npc.isDoingNothing(true)
                || npc.getFoodLevel() >= 20) {
            return false;
        }

        if (cakePos != null) return true;

        BlockPos.MutableBlockPos mutable = villager.blockPosition().mutable();

        for (int x = -SEARCH_RANGE; x <= SEARCH_RANGE; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -SEARCH_RANGE; z <= SEARCH_RANGE; z++) {
                    mutable.set(villager.getX() + x, villager.getY() + y, villager.getZ() + z);
                    if (level.getBlockState(mutable).getBlock() instanceof CakeBlock) {
                        cakePos = new BlockPos(mutable);
                    }
                }
            }
        }

        return cakePos != null;
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        boolean canReach = LootChest.canReach(villager, time);
        if (!canReach) tryAgain = TRY_AGAIN_COOLDOWN;

        return checkExtraStartConditions(level, villager) && canReach;
    }

    @Override
    public void tick(ServerLevel level, @NotNull Villager villager, long time) {
        if (!cakePos.closerToCenterThan(villager.position(), 1.0d) || !(villager instanceof VillagerNPC npc)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, cakePos, VillagerNPC.WALK_SPEED.get(), 0);
            return;
        }

        BlockState state = level.getBlockState(cakePos);

        int oldFoodLevel = npc.getFoodLevel();

        VillagerFoodLevelChangeEvent event = new VillagerFoodLevelChangeEvent(npc, 2 + oldFoodLevel);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            npc.getFoodData().eat(event.getFoodLevel() - oldFoodLevel, 0.1f);
        }

        int bites;
        try {
            bites = state.getValue(CakeBlock.BITES);
        } catch (IllegalArgumentException exception) {
            // The cake may have been eaten or removed.
            cakePos = null;
            return;
        }

        level.gameEvent(villager, GameEvent.EAT, cakePos);
        if (bites < 6) {
            villager.swing(InteractionHand.MAIN_HAND);
            level.setBlock(cakePos, state.setValue(CakeBlock.BITES, bites + 1), 3);
        } else {
            level.removeBlock(cakePos, false);
            level.gameEvent(villager, GameEvent.BLOCK_DESTROY, cakePos);
        }
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        cakePos = null;
        tryAgain = 0;
    }
}