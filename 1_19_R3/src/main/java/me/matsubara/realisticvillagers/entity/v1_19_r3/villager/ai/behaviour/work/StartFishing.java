package me.matsubara.realisticvillagers.entity.v1_19_r3.villager.ai.behaviour.work;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_19_r3.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Iterator;

public class StartFishing extends Behavior<Villager> {

    private static final int RANGE = 16;

    private @Nullable BlockPos waterPos;

    @SuppressWarnings("ConstantConditions")
    public StartFishing() {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                VillagerNPC.HAS_FISHED_RECENTLY, MemoryStatus.VALUE_ABSENT));
    }

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (Config.DISABLE_SKINS.asBool()) return false;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        if (!villager.getMainHandItem().is(Items.FISHING_ROD)) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.FISHERMAN) return false;
        if (fishedRecently(villager)) return false;
        if (!(villager instanceof VillagerNPC npc) || !npc.isDoingNothing(true)) return false;

        BlockPos villagerPos = villager.blockPosition();

        Iterator<BlockPos> iterator = BlockPos.withinManhattan(villagerPos, RANGE, RANGE, RANGE).iterator();
        while (iterator.hasNext()) {
            BlockPos pos = iterator.next();
            if (pos.getX() == villagerPos.getX() && pos.getZ() == villagerPos.getZ()) continue;

            BlockState aboveState = villager.level.getBlockState(pos.above());
            BlockState posState = villager.level.getBlockState(pos);
            if (!posState.is(Blocks.WATER) && !aboveState.isAir()) continue;

            waterPos = pos.immutable();
            break;
        }

        return waterPos != null;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (waterPos != null) lookAtWithMemory(villager, waterPos);
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        waterPos = null;
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        if (waterPos == null) return;

        if (!(villager instanceof VillagerNPC npc)) return;

        if (!npc.isFishing()) {
            lookAtWithMemory(npc, waterPos);
            npc.toggleFishing();
        }
    }

    private void lookAtWithMemory(Villager villager, BlockPos at) {
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(at));
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return !fishedRecently(villager);
    }

    private boolean fishedRecently(Villager villager) {
        return villager.getBrain().hasMemoryValue(VillagerNPC.HAS_FISHED_RECENTLY);
    }
}