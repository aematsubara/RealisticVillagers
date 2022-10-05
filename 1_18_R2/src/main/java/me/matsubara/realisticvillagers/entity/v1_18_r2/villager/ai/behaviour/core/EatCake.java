package me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerFoodLevelChangeEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.Bukkit;

public class EatCake extends Behavior<Villager> {

    private BlockPos cakePos;

    private final static int SEARCH_RANGE = 6;

    public EatCake() {
        super(ImmutableMap.of(), 100);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc) || !npc.isDoingNothing(true) || npc.getFoodLevel() >= 20) {
            return false;
        }

        if (cakePos != null) return true;

        BlockPos.MutableBlockPos mutable = villager.blockPosition().mutable();

        for (int x = -SEARCH_RANGE; x <= SEARCH_RANGE; x++) {
            for (int y = -SEARCH_RANGE; y <= SEARCH_RANGE; y++) {
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
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        if (cakePos.closerToCenterThan(villager.position(), 1.0d) && villager instanceof VillagerNPC npc) {
            BlockState state = level.getBlockState(cakePos);

            int oldFoodLevel = npc.getFoodLevel();

            VillagerFoodLevelChangeEvent event = new VillagerFoodLevelChangeEvent(npc, 2 + oldFoodLevel);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                npc.getFoodData().eat(event.getFoodLevel() - oldFoodLevel, 0.1f);
            }

            int bites = state.getValue(CakeBlock.BITES);
            level.gameEvent(villager, GameEvent.EAT, cakePos);
            if (bites < 6) {
                villager.swing(InteractionHand.MAIN_HAND);
                level.setBlock(cakePos, state.setValue(CakeBlock.BITES, bites + 1), 3);
            } else {
                level.removeBlock(cakePos, false);
                level.gameEvent(villager, GameEvent.BLOCK_DESTROY, cakePos);
            }
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, cakePos, Villager.SPEED_MODIFIER, 0);
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        cakePos = null;
    }
}