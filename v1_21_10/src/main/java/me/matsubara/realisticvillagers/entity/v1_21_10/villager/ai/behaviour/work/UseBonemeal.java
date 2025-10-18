package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.work;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.data.Exchangeable;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class UseBonemeal extends Behavior<Villager> implements Exchangeable {

    private long nextWorkCycleTime;
    private long lastBonemealingSession;
    private int timeWorkedSoFar;
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<BlockPos> cropPos = Optional.empty();

    private @Getter ItemStack previousItem;

    private static final int BONEMEALING_DURATION = 80;

    public UseBonemeal() {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc) || !npc.isDoingNothing(ChangeItemType.USING_BONE_MEAL)) return false;
        if (villager.tickCount % 10 != 0) return false;
        if (lastBonemealingSession != 0L && lastBonemealingSession + 160L > villager.tickCount) return false;

        if (!villager.getVillagerData().profession().is(VillagerProfession.FARMER)) return false;
        if (villager.getInventory().countItem(Items.BONE_MEAL) <= 0) return false;

        cropPos = pickNextTarget(level, villager);
        return cropPos.isPresent();
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return timeWorkedSoFar < BONEMEALING_DURATION && cropPos.isPresent();
    }

    private Optional<BlockPos> pickNextTarget(ServerLevel level, Villager villager) {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        Optional<BlockPos> pos = Optional.empty();

        int count = 0;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    mutable.setWithOffset(villager.blockPosition(), x, y, z);
                    if (!validPos(mutable, level)) continue;

                    ++count;
                    if (level.random.nextInt(count) == 0) {
                        pos = Optional.of(mutable.immutable());
                    }
                }
            }
        }

        return pos;
    }

    private boolean validPos(BlockPos pos, @NotNull ServerLevel level) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();

        // For normal crops.
        if (block instanceof CropBlock crop && !crop.isMaxAge(state)) return true;

        // For other bonemealables.
        return block instanceof BonemealableBlock bonemealable && bonemealable.isValidBonemealTarget(level, pos, state);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setUsingBoneMeal(true);
            previousItem = npc.getMainHandItem();
        }

        setCurrentCropAsTarget(villager);
        villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BONE_MEAL));
        nextWorkCycleTime = time;
        timeWorkedSoFar = 0;
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setUsingBoneMeal(false);
            npc.setItemSlot(EquipmentSlot.MAINHAND, previousItem);
        }
        lastBonemealingSession = villager.tickCount;
    }

    private void setCurrentCropAsTarget(Villager villager) {
        cropPos.ifPresent((pos) -> {
            BlockPosTracker time = new BlockPosTracker(pos);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, time);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(time, 0.5f, 1));
        });
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        BlockPos pos = cropPos.get();

        if (time < nextWorkCycleTime || !pos.closerToCenterThan(villager.position(), 1.0d)) return;

        ItemStack boneMeal = ItemStack.EMPTY;

        SimpleContainer inventory = villager.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item.is(Items.BONE_MEAL)) {
                boneMeal = item;
                break;
            }
        }

        if (!boneMeal.isEmpty() && BoneMealItem.growCrop(boneMeal, level, pos)) {
            level.levelEvent(1505, pos, 0);
            cropPos = pickNextTarget(level, villager);
            setCurrentCropAsTarget(villager);
            nextWorkCycleTime = time + 40L;
        }

        ++timeWorkedSoFar;
    }
}