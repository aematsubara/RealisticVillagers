package me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.manager.ChestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.v1_18_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.*;

public class LootChest extends Behavior<Villager> {

    private BlockPos chestPos;
    private boolean chestOpen;
    private final Map<String, Long> cooldown;
    private final List<ItemStack> items;

    private boolean looted;
    private boolean addToCooldown;
    private int count;
    private int tryAgain;

    private static final int SEARCH_RANGE = 16;

    @SuppressWarnings("ConstantConditions")
    public LootChest() {
        super(ImmutableMap.of(VillagerNPC.HAS_LOOTED_RECENTLY, MemoryStatus.VALUE_ABSENT));
        cooldown = new HashMap<>();
        items = new ArrayList<>();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!Config.LOOT_CHEST_ENABLED.asBool()) return false;

        if (villager.isSleeping()) return false;

        if (tryAgain > 0) {
            tryAgain--;
            return false;
        }

        if (!(villager instanceof VillagerNPC npc)
                || !npc.isDoingNothing(ChangeItemType.LOOTING)
                || !canStoreItems(npc)) {
            return false;
        }

        if (!Config.LOOT_CHEST_ALLOW_BABIES.asBool() && villager.isBaby()) return false;

        if (villager.isDeadOrDying()) return false;
        if (villager.getBrain().hasMemoryValue(VillagerNPC.HAS_LOOTED_RECENTLY)) return false;

        if (chestPos != null) return true;

        BlockPos.MutableBlockPos mutable = villager.blockPosition().mutable();

        out:
        for (int x = -SEARCH_RANGE; x <= SEARCH_RANGE; x++) {
            for (int y = -SEARCH_RANGE; y <= SEARCH_RANGE; y++) {
                for (int z = -SEARCH_RANGE; z <= SEARCH_RANGE; z++) {
                    mutable.set(villager.getX() + x, villager.getY() + y, villager.getZ() + z);

                    Block block = level.getBlockState(mutable).getBlock();
                    if (!(block instanceof ChestBlock)) continue;

                    chestPos = new BlockPos(mutable);

                    long last = cooldown.getOrDefault(chestPos.toShortString(), 0L);
                    if (System.currentTimeMillis() - last <= Config.LOOT_CHEST_PER_CHEST_COOLDOWN.asLong()) {
                        chestPos = null;
                        continue;
                    }

                    ChestManager chestManager = npc.getPlugin().getChestManager();
                    if (chestManager.getVillagerChests().containsKey(toBukkit())) {
                        chestPos = null;
                        continue;
                    }

                    String requiredLine = Config.LOOT_CHEST_REQUIRED_SIGN_LINE.asStringTranslated();
                    if (requiredLine.isEmpty()) break;

                    for (Direction direction : Direction.values()) {
                        if (direction.getAxis().isVertical()) continue;

                        BlockPos relative = mutable.relative(direction);
                        if (!(level.getBlockState(relative).getBlock() instanceof SignBlock)) continue;

                        Sign sign = (Sign) level.getWorld().getBlockState(CraftBlock.at(level, relative).getLocation());
                        for (String line : sign.getLines()) {
                            if (line.contains(requiredLine)) break out;
                        }
                    }

                    chestPos = null;
                }
            }
        }

        return chestPos != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        UUID viewer;
        if (villager instanceof VillagerNPC npc) {
            viewer = npc.getPlugin().getChestManager().getVillagerChests().get(toBukkit());
        } else {
            viewer = null;
        }

        boolean canReach = canReach(villager, time);
        if (!canReach) tryAgain = 600;

        return checkExtraStartConditions(level, villager)
                && level.getBlockState(chestPos).getBlock() instanceof ChestBlock
                && (viewer == null || viewer.equals(villager.getUUID()))
                && canReach;
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        if (!chestPos.closerToCenterThan(villager.position(), 3.0d) || !(villager instanceof VillagerNPC npc)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, chestPos, Villager.SPEED_MODIFIER, 1);
            return;
        }

        // Look at chest all the time.
        BehaviorUtils.setWalkAndLookTargetMemories(villager, chestPos, Villager.SPEED_MODIFIER, 1);

        Inventory inventory = getChest(level).getInventory();
        boolean isOpen = isOpen(level);

        // Open the chest if not opened.
        if (!chestOpen) {
            containerAction(npc, level, true, isOpen);
            chestOpen = true;
        }

        // Loot wanted items and add cooldown to selected chest.
        if (!looted) {
            for (ItemStack item : inventory.getContents()) {
                if (item == null) continue;
                if (!npc.getPlugin().isWantedItem(npc, item)) continue;

                ItemStack copy = item.clone();
                copy.setAmount(1);

                if (villager.getInventory().canAddItem(CraftItemStack.asNMSCopy(copy))) {
                    items.add(copy);
                }
            }

            looted = true;
            count = 20;
        }

        if (!chestOpen) return;

        if (count > 0) {
            count--;
            return;
        }

        if (items.isEmpty()) {
            // No more item to add, close chest (only if inventory viewers is empty).
            containerAction(npc, level, false, isOpen);

            // Add cooldown to loot action.
            villager.getBrain().setMemoryWithExpiry(VillagerNPC.HAS_LOOTED_RECENTLY, true, Config.LOOT_CHEST_COOLDOWN.asLong());

            // Should be added to cooldown.
            addToCooldown = true;
            return;
        }

        // Return if item isn't in container inventory anymore.
        ItemStack item = items.remove(0);
        if (!inventory.containsAtLeast(item, 1)) return;
        if (!inventory.removeItem(item).isEmpty()) return;

        // Add to villager inventory.
        villager.getInventory().addItem(CraftItemStack.asNMSCopy(item));

        // Only shuffle at last item and if villager is a nitwit.
        if (items.isEmpty()
                && npc.is(VillagerProfession.NITWIT)
                && Config.LOOT_CHEST_NITWIT_SHUFFLE_INVENTORY.asBool()) {
            List<ItemStack> contents = new ArrayList<>();
            Collections.addAll(contents, inventory.getContents());
            Collections.shuffle(contents);
            inventory.setContents(contents.toArray(value -> new ItemStack[0]));
        }

        if (!items.isEmpty()) count = 20;
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) npc.setLooting(true);
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setLooting(false);
            if (chestPos != null && chestOpen) {
                containerAction(npc, level, false, isOpen(level));
            }
        }

        if (addToCooldown && !villager.isDeadOrDying()) {
            addToCooldown();
        }

        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        chestPos = null;
        chestOpen = false;
        items.clear();
        looted = false;
        addToCooldown = false;
        count = 0;
    }

    private boolean canStoreItems(VillagerNPC npc) {
        Inventory inventory = npc.getBukkitEntity().getInventory();
        if (inventory.firstEmpty() != -1) return true;

        for (ItemStack item : inventory.getContents()) {
            if (!npc.getPlugin().isWantedItem(npc, item)) continue;
            if (item.getAmount() < item.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void containerAction(VillagerNPC npc, ServerLevel level, boolean open, boolean isOpen) {
        ChestManager chestManager = npc.getPlugin().getChestManager();
        Map<Vector, UUID> chests = chestManager.getVillagerChests();
        if (open) {
            chests.put(toBukkit(), npc.getUUID());
        } else {
            chests.remove(toBukkit());
        }

        // If chest is open by one or more players and a villager close it, don't play animation nor sound.
        if (isOpen && !open) return;

        for (ServerPlayer player : level.players()) {
            player.connection.connection.send(new ClientboundBlockEventPacket(
                    chestPos,
                    Blocks.CHEST,
                    1,
                    open ? 1 : 0));
        }

        // Only play sound if inventory isn't open by one or more players.
        if (!isOpen) playSound(level, open);
    }

    private void playSound(ServerLevel level, boolean open) {
        BlockState state = level.getBlockState(chestPos);

        double x = chestPos.getX() + 0.5d;
        double y = chestPos.getY() + 0.5d;
        double z = chestPos.getZ() + 0.5d;

        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type != ChestType.SINGLE) {
            Direction direction = ChestBlock.getConnectedDirection(state);
            x += direction.getStepX() * 0.5d;
            z += direction.getStepZ() * 0.5d;
        }

        SoundEvent sound = open ? SoundEvents.CHEST_OPEN : SoundEvents.CHEST_CLOSE;
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
    }

    private Vector toBukkit() {
        return new Vector(chestPos.getX(), chestPos.getY(), chestPos.getZ());
    }

    private boolean isOpen(ServerLevel level) {
        return !getChest(level).getBlockInventory().getViewers().isEmpty();
    }

    private Chest getChest(ServerLevel level) {
        return (Chest) level.getWorld().getBlockState(CraftBlock.at(level, chestPos).getLocation());
    }

    private void addToCooldown() {
        if (chestPos != null) cooldown.put(
                chestPos.toShortString(),
                System.currentTimeMillis() + Config.LOOT_CHEST_PER_CHEST_COOLDOWN.asLong());
    }

    public static boolean canReach(Villager villager, long time) {
        Long cantReachTime = villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).orElse(null);
        if (cantReachTime == null) return true;

        double seconds = (double) (time - cantReachTime) / 20;
        return seconds <= 8;
    }
}