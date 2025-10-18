package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.work;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.Getter;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.data.Exchangeable;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEvent.Context;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_21_R6.block.CraftBlock;
import org.bukkit.craftbukkit.v1_21_R6.block.data.CraftBlockData;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class HarvestFarmland extends Behavior<Villager> implements Exchangeable {

    private int timeWorkedSoFar;
    private long nextOkStartTime;
    private @Nullable BlockPos aboveFarmlandPos;
    private @Getter ItemStack previousItem;
    private final List<BlockPos> validFarmlandAroundVillager = Lists.newArrayList();

    private static final int HARVEST_DURATION = 200;
    private static final int WATER_DETECTION_RADIUS = 2;
    private static final Block[] CROPS = {Blocks.WHEAT, Blocks.POTATOES, Blocks.CARROTS, Blocks.BEETROOTS, Blocks.TORCHFLOWER_CROP, Blocks.PITCHER_CROP};
    private static final Map<Item, Block> STEM_CROPS = ImmutableMap.of(
            Items.PUMPKIN_SEEDS, Blocks.PUMPKIN_STEM,
            Items.MELON_SEEDS, Blocks.MELON_STEM);
    private static final Vec3i[] DIRECTIONS = {
            new Vec3i(0, 0, -1),
            new Vec3i(0, 0, 1),
            new Vec3i(-1, 0, 0),
            new Vec3i(1, 0, 0),
            new Vec3i(1, 0, -1),
            new Vec3i(-1, 0, -1),
            new Vec3i(1, 0, 1),
            new Vec3i(-1, 0, 1)};
    private static final Map<Integer, Item> LEVEL_TO_HOE = ImmutableMap.of(
            1, Items.WOODEN_HOE,
            2, Items.STONE_HOE,
            3, Items.IRON_HOE,
            4, Items.GOLDEN_HOE,
            5, Items.DIAMOND_HOE);
    public static final Block[] DIRT = {Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT};

    public HarvestFarmland() {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.SECONDARY_JOB_SITE, MemoryStatus.VALUE_PRESENT));
    }

    @Override
    public boolean checkExtraStartConditions(@NotNull ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc) || !npc.isDoingNothing(ChangeItemType.USING_HOE)) return false;
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return false;
        if (!villager.getVillagerData().profession().is(VillagerProfession.FARMER)) return false;

        BlockPos.MutableBlockPos mutable = villager.blockPosition().mutable();
        validFarmlandAroundVillager.clear();

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    mutable.set(villager.getX() + (double) x, villager.getY() + (double) y, villager.getZ() + (double) z);
                    if (validPos(mutable, level)) validFarmlandAroundVillager.add(new BlockPos(mutable));
                }
            }
        }

        return (aboveFarmlandPos = getValidFarmland(level)) != null;
    }

    private @Nullable BlockPos getValidFarmland(ServerLevel level) {
        return validFarmlandAroundVillager.isEmpty() ? null : validFarmlandAroundVillager.get(level.getRandom().nextInt(validFarmlandAroundVillager.size()));
    }

    private boolean validPos(BlockPos pos, @NotNull ServerLevel level) {
        BlockState aboveState = level.getBlockState(pos);
        BlockState belowState;

        return isValidCrop(aboveState)
                || isValidFarmland(aboveState, (belowState = level.getBlockState(pos.below())))
                || isValidDirt(level, pos, aboveState, belowState);
    }

    private boolean isValidDirt(ServerLevel level, BlockPos pos, @NotNull BlockState aboveState, BlockState belowState) {
        // Only ROOTED_DIRT doesn't check for air above.
        if (!aboveState.isAir() && !belowState.is(Blocks.ROOTED_DIRT)) return false;

        // Check if is any type of valid dirt.
        boolean validDirt = false;
        for (Block dirt : DIRT) {
            if (belowState.is(dirt)) {
                validDirt = true;
                break;
            }
        }
        if (!validDirt) return false;

        // No need to check for water since these blocks will be converted to dirt, not farmland.
        if (belowState.is(Blocks.COARSE_DIRT) || belowState.is(Blocks.ROOTED_DIRT)) return true;

        // Below is a dirt block, check for water in a square radius.
        for (Vec3i direction : DIRECTIONS) {
            for (int radius = 1; radius <= WATER_DETECTION_RADIUS; radius++) {
                if (level.getBlockState(relative(pos.below(), direction, radius)).is(Blocks.WATER)) return true;
            }
        }

        return false;
    }

    private BlockPos relative(BlockPos pos, Vec3i offset, int multiplier) {
        return multiplier == 0 ? pos : new BlockPos(
                pos.getX() + offset.getX() * multiplier,
                pos.getY() + offset.getY() * multiplier,
                pos.getZ() + offset.getZ() * multiplier);
    }

    private boolean isValidCrop(@NotNull BlockState aboveState) {
        return aboveState.getBlock() instanceof CropBlock crop && crop.isMaxAge(aboveState);
    }

    private boolean isValidFarmland(@NotNull BlockState aboveState, BlockState belowState) {
        return aboveState.isAir() && belowState.getBlock() instanceof FarmBlock;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setUsingHoe(true);
            previousItem = npc.getMainHandItem();
        }

        if (time <= nextOkStartTime || aboveFarmlandPos == null) return;

        equipBasedOnBlock(level, villager);
        setTarget(villager);
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setUsingHoe(false);
            npc.setItemSlot(EquipmentSlot.MAINHAND, previousItem);
        }

        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        timeWorkedSoFar = 0;
        nextOkStartTime = time + 40L;
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        if (aboveFarmlandPos != null && !aboveFarmlandPos.closerToCenterThan(villager.position(), 1.0d)) return;

        if (time <= nextOkStartTime || aboveFarmlandPos == null) {
            timeWorkedSoFar++;
            return;
        }

        BlockState aboveState = level.getBlockState(aboveFarmlandPos);
        BlockState belowState;

        // Below is valid dirt, convert to specific block.
        if (isValidDirt(level, aboveFarmlandPos, aboveState, (belowState = level.getBlockState(aboveFarmlandPos.below())))) {
            boolean isRooted;
            if ((isRooted = belowState.is(Blocks.ROOTED_DIRT)) || belowState.is(Blocks.COARSE_DIRT)) {
                changeIntoState(level, villager, Blocks.DIRT.defaultBlockState());
                if (isRooted) Block.popResourceFromFace(
                        level,
                        aboveFarmlandPos.below(),
                        Direction.getRandom(villager.getRandom()),
                        Items.HANGING_ROOTS.getDefaultInstance());
                toTheNextOne(level, villager, time);
            } else changeIntoState(level, villager, Blocks.FARMLAND.defaultBlockState());
        }

        // Above is a grown crop, try to remove block.
        if (isValidCrop(aboveState) && !callEntityChangeBlockEvent(villager, aboveFarmlandPos, Blocks.AIR.defaultBlockState()).isCancelled()) {
            level.destroyBlock(aboveFarmlandPos, true, villager);
        }

        // Above is air and below is a farm block, try to plant seeds.
        Triple<Integer, ItemStack, BlockState> wantedSeed = getWantedSeed(villager, aboveState, belowState, true);
        if (wantedSeed != null) {
            ItemStack item = wantedSeed.getMiddle();
            BlockState newState = wantedSeed.getRight();

            level.setBlockAndUpdate(aboveFarmlandPos, newState);
            level.gameEvent(GameEvent.BLOCK_PLACE, aboveFarmlandPos, Context.of(villager, newState));
            level.playSound(
                    null,
                    aboveFarmlandPos.getX(),
                    aboveFarmlandPos.getY(),
                    aboveFarmlandPos.getZ(),
                    SoundEvents.CROP_PLANTED,
                    SoundSource.BLOCKS,
                    1.0f,
                    1.0f);
            item.shrink(1);
            if (item.isEmpty()) villager.getInventory().setItem(wantedSeed.getLeft(), ItemStack.EMPTY);
        }

        if (aboveState.getBlock() instanceof CropBlock crop && !crop.isMaxAge(aboveState)) {
            toTheNextOne(level, villager, time);
        }

        timeWorkedSoFar++;
    }

    private void toTheNextOne(ServerLevel level, Villager villager, long time) {
        validFarmlandAroundVillager.remove(aboveFarmlandPos);
        if ((aboveFarmlandPos = getValidFarmland(level)) == null) return;
        nextOkStartTime = time + 20L;
        equipBasedOnBlock(level, villager);
        setTarget(villager);
    }

    private void equipBasedOnBlock(ServerLevel level, Villager villager) {
        if (aboveFarmlandPos == null) return;

        BlockState aboveState = level.getBlockState(aboveFarmlandPos);
        BlockState belowState;

        // Remove crop with hands.
        if (isValidCrop(aboveState)) {
            villager.setItemInHand(InteractionHand.MAIN_HAND, Items.AIR.getDefaultInstance());
            return;
        }

        // Set planting seed in hand.
        if (isValidFarmland(aboveState, (belowState = level.getBlockState(aboveFarmlandPos.below())))) {
            Triple<Integer, ItemStack, BlockState> wantedSeed = getWantedSeed(villager, aboveState, belowState, false);
            if (wantedSeed != null) villager.setItemInHand(InteractionHand.MAIN_HAND, wantedSeed.getMiddle().copy());
            return;
        }

        // Use a hoe to harvest farmland.
        if (isValidDirt(level, aboveFarmlandPos, aboveState, belowState)) {
            Item hoe = LEVEL_TO_HOE.getOrDefault(villager.getVillagerData().level(), Items.NETHERITE_HOE);
            villager.setItemInHand(InteractionHand.MAIN_HAND, hoe.getDefaultInstance());
        }
    }

    private void changeIntoState(ServerLevel level, Villager villager, BlockState state) {
        if (aboveFarmlandPos == null) return;

        level.setBlock(aboveFarmlandPos.below(), state, 11);
        level.gameEvent(GameEvent.BLOCK_CHANGE, aboveFarmlandPos.below(), Context.of(villager, state));
    }

    private @Nullable BlockState getNewState(@NotNull ItemStack item) {
        Block special = STEM_CROPS.get(item.getItem());
        if (special != null) return special.defaultBlockState();

        for (Block block : CROPS) {
            if (item.is(block.asItem())) return block.defaultBlockState();
        }
        return null;
    }

    private void setTarget(Villager villager) {
        if (aboveFarmlandPos == null) return;
        BehaviorUtils.setWalkAndLookTargetMemories(villager, aboveFarmlandPos, VillagerNPC.WALK_SPEED.get(), 1);
    }

    private @Nullable Triple<Integer, ItemStack, BlockState> getWantedSeed(Villager villager, BlockState aboveState, BlockState belowState, boolean checkEvent) {
        if (aboveFarmlandPos == null) return null;
        if (!isValidFarmland(aboveState, belowState) || !villager.hasFarmSeeds()) return null;

        SimpleContainer inventory = villager.getInventory();

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item.isEmpty()) continue;

            BlockState newState = getNewState(item);
            if (newState == null || (checkEvent && callEntityChangeBlockEvent(villager, aboveFarmlandPos, newState).isCancelled())) {
                continue;
            }

            return Triple.of(i, item, newState);
        }

        return null;
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return timeWorkedSoFar < HARVEST_DURATION;
    }

    private static @NotNull EntityChangeBlockEvent callEntityChangeBlockEvent(@NotNull Entity entity, @NotNull BlockPos position, BlockState newBlock) {
        // Fixes "boolean cannot be dereferenced".
        EntityChangeBlockEvent event = new EntityChangeBlockEvent(entity.getBukkitEntity(), CraftBlock.at(entity.level(), position), CraftBlockData.fromData(newBlock));
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }
}