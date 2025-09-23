package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.data.Exchangeable;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.manager.ChestManager;
import me.matsubara.realisticvillagers.manager.gift.Gift;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.craftbukkit.v1_21_R5.block.CraftBlock;
import org.bukkit.craftbukkit.v1_21_R5.block.CraftChest;
import org.bukkit.craftbukkit.v1_21_R5.inventory.CraftItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class LootChest extends Behavior<Villager> implements Exchangeable {

    private Chest chest;
    private boolean chestOpen;
    private final Map<String, Long> cooldown = new HashMap<>();
    private final List<ItemStack> items = new ArrayList<>();

    private boolean looted;
    private boolean addToCooldown;
    private int count;
    private int tryAgain;

    private static final int SEARCH_RANGE = 8;
    private static final int TRY_AGAIN_COOLDOWN = 1200;

    @SuppressWarnings("ConstantConditions")
    public LootChest() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                VillagerNPC.HAS_LOOTED_RECENTLY, MemoryStatus.VALUE_ABSENT));
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

    private boolean canStart(ServerLevel level, Villager villager) {
        if (!Config.LOOT_CHEST_ENABLED.asBool()) return false;
        if (villager.isSleeping()) return false;

        if (!(villager instanceof VillagerNPC npc)
                || !npc.isDoingNothing(ChangeItemType.LOOTING)
                || !canStoreItems(npc)) {
            // Task already started, probably the villager was interrupted while looting.
            if (chest != null) addToCooldown = true;
            return false;
        }

        if (!npc.checkCurrentActivity(Activity.WORK, Activity.IDLE, Activity.PRE_RAID, Activity.RAID)) return false;

        if (!Config.LOOT_CHEST_ALLOW_BABIES.asBool() && villager.isBaby()) return false;

        if (villager.isDeadOrDying()) return false;
        if (villager.getBrain().hasMemoryValue(VillagerNPC.HAS_LOOTED_RECENTLY)) return false;

        if (chest != null) return true;

        BlockPos.MutableBlockPos mutable = villager.blockPosition().mutable();

        out:
        for (int x = -SEARCH_RANGE; x <= SEARCH_RANGE; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -SEARCH_RANGE; z <= SEARCH_RANGE; z++) {
                    mutable.set(villager.getX() + x, villager.getY() + y, villager.getZ() + z);

                    BlockState state = level.getWorld().getBlockState(mutable.getX(), mutable.getY(), mutable.getZ());
                    if (state.getType() != Material.CHEST) continue;

                    chest = (Chest) state;

                    long last = cooldown.getOrDefault(chest.getLocation().toString(), 0L);
                    if (System.currentTimeMillis() - last <= Config.LOOT_CHEST_PER_CHEST_COOLDOWN.asLong()) {
                        chest = null;
                        continue;
                    }

                    ChestManager chestManager = npc.getPlugin().getChestManager();
                    if (chestManager.getVillagerChests().containsKey(vector())) {
                        chest = null;
                        continue;
                    }

                    String requiredLine = Config.LOOT_CHEST_REQUIRED_SIGN_LINE.asStringTranslated();
                    if (requiredLine.isEmpty()) break;

                    for (Direction direction : Direction.values()) {
                        if (direction.getAxis().isVertical()) continue;

                        state = level.getWorld().getBlockState(CraftBlock.at(level, mutable.relative(direction)).getLocation());
                        if (!(state instanceof Sign sign)) continue;

                        for (Side side : Side.values()) {
                            for (String line : sign.getSide(side).getLines()) {
                                if (line.contains(requiredLine)) break out;
                            }
                        }
                    }

                    chest = null;
                }
            }
        }

        return chest != null;
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        UUID viewer;
        if (villager instanceof VillagerNPC npc) {
            viewer = npc.getPlugin().getChestManager().getVillagerChests().get(vector());
        } else {
            viewer = null;
        }

        boolean canReach = canReach(villager, time);
        if (!canReach) tryAgain = TRY_AGAIN_COOLDOWN;

        return checkExtraStartConditions(level, villager)
                && (viewer == null || viewer.equals(villager.getUUID()))
                && canReach
                && level.getWorld().getBlockAt(chest.getLocation()).getType() == Material.CHEST;
    }

    @Override
    public void tick(ServerLevel level, @NotNull Villager villager, long time) {
        BlockPos pos = ((CraftChest) chest).getPosition();
        if (chest.getLocation().distance(villager.getBukkitEntity().getLocation()) > 3 || !(villager instanceof VillagerNPC npc)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, pos, VillagerNPC.WALK_SPEED.get(), 1);
            return;
        }

        // Look at chest all the time.
        BehaviorUtils.setWalkAndLookTargetMemories(villager, pos, VillagerNPC.WALK_SPEED.get(), 1);

        Inventory inventory = chest.getInventory();
        boolean isOpen = isOpen();

        // Open the chest if not opened.
        if (!chestOpen) {
            containerAction(npc, level, true, isOpen);
            chestOpen = true;
        }

        // Loot wanted items and add cooldown to selected chest.
        if (!looted) {
            for (ItemStack item : inventory.getContents()) {
                if (item == null || alreadyLooted(npc, item)) continue;

                Gift gift = npc.getPlugin().getWantedItem(npc, item, false);
                if (gift == null) continue;

                ItemStack copy = item.clone();
                int takeAmount = gift.getAmount();
                int currentAmount = copy.getAmount();

                copy.setAmount(Math.min(takeAmount != -1 ? takeAmount : level.random.nextInt(1, currentAmount + 1), currentAmount));

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
            // No more item to add, close chest (only if inventory viewers are empty).
            containerAction(npc, level, false, isOpen);

            // Add cooldown to loot action.
            villager.getBrain().setMemoryWithExpiry(VillagerNPC.HAS_LOOTED_RECENTLY, true, Config.LOOT_CHEST_COOLDOWN.asLong());

            // Should be added to cooldown.
            addToCooldown = true;
            return;
        }

        // Return if item isn't in container inventory anymore.
        ItemStack item = items.removeFirst();
        if (!inventory.containsAtLeast(item, 1)) return;
        if (!inventory.removeItem(item).isEmpty()) {
            if (item.getAmount() == 1) return;

            // Try again, but only try to take 1 item.
            item.setAmount(1);
            if (!inventory.removeItem(item).isEmpty()) return;
        }

        if (ItemStackUtils.isWeapon(item)
                || ItemStackUtils.getSlotByItem(item) != null
                || item.getType() == Material.SHIELD) {
            // Equip armor/weapon.
            ItemStackUtils.setBetterWeaponInMaindHand(npc.bukkit(), item);
            ItemStackUtils.setArmorItem(npc.bukkit(), item);
        } else {
            // Add to villager inventory.
            villager.getInventory().addItem(CraftItemStack.asNMSCopy(item));
        }

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
    public boolean timedOut(long time) {
        return false;
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) npc.setLooting(true);
        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setLooting(false);
            if (chest != null && chestOpen) {
                containerAction(npc, level, false, isOpen());
            }
        }

        if (addToCooldown && !villager.isDeadOrDying()) {
            addToCooldown();
        }

        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        chest = null;
        chestOpen = false;
        items.clear();
        looted = false;
        addToCooldown = false;
        count = 0;
    }

    private boolean canStoreItems(@NotNull VillagerNPC npc) {
        Inventory inventory = npc.getBukkitEntity().getInventory();
        if (inventory.firstEmpty() != -1) return true;

        for (ItemStack item : inventory.getContents()) {
            if (npc.getPlugin().getWantedItem(npc, item, false) == null) continue;
            if (item.getAmount() < item.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void containerAction(@NotNull VillagerNPC npc, ServerLevel level, boolean open, boolean isOpen) {
        ChestManager chestManager = npc.getPlugin().getChestManager();
        Map<Vector, UUID> chests = chestManager.getVillagerChests();
        if (open) {
            chests.put(vector(), npc.getUUID());
        } else {
            chests.remove(vector());
        }

        // If chest is open by one or more players and a villager close it, don't play animation nor sound.
        if (isOpen && !open) return;

        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundBlockEventPacket(
                    ((CraftChest) chest).getPosition(),
                    Blocks.CHEST,
                    1,
                    open ? 1 : 0));
        }

        // Only play sound if inventory isn't open by one or more players.
        if (!isOpen) playSound(level, open);
    }

    private void playSound(ServerLevel level, boolean open) {
        Location location = chest.getLocation().clone().add(0.5d, 0.5d, 0.5d);

        org.bukkit.block.data.type.Chest data = (org.bukkit.block.data.type.Chest) chest.getBlockData();
        org.bukkit.block.data.type.Chest.Type type = (data).getType();
        if (type != org.bukkit.block.data.type.Chest.Type.SINGLE) {
            BlockFace facing = data.getFacing();
            facing = type == org.bukkit.block.data.type.Chest.Type.LEFT ? getClockWise(facing) : getCounterClockWise(facing);
            location.add(facing.getModX() * 0.5d, 0.0d, facing.getModZ() * 0.5d);
        }

        Sound sound = open ? Sound.BLOCK_CHEST_OPEN : Sound.BLOCK_CHEST_CLOSE;
        level.getWorld().playSound(location, sound, SoundCategory.BLOCKS, 0.5f, level.random.nextFloat() * 0.1f + 0.9f);
    }

    public BlockFace getCounterClockWise(@NotNull BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case SOUTH -> BlockFace.EAST;
            case WEST -> BlockFace.SOUTH;
            case EAST -> BlockFace.NORTH;
            default -> null;
        };
    }

    public BlockFace getClockWise(@NotNull BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            case EAST -> BlockFace.SOUTH;
            default -> null;
        };
    }

    private @NotNull Vector vector() {
        return chest.getLocation().toVector();
    }

    private boolean isOpen() {
        return !chest.getBlockInventory().getViewers().isEmpty();
    }

    private void addToCooldown() {
        if (chest != null) cooldown.put(
                chest.getLocation().toString(),
                System.currentTimeMillis() + Config.LOOT_CHEST_PER_CHEST_COOLDOWN.asLong());
    }

    private boolean alreadyLooted(@NotNull VillagerNPC npc, ItemStack check) {
        EquipmentSlot checkItemSlot = ItemStackUtils.getSlotByItem(check);
        boolean isCheckSword = ItemStackUtils.isSword(check);
        boolean isCheckAxe = ItemStackUtils.isAxe(check);

        Material checkType = check.getType();
        boolean isCheckTrident = checkType == Material.TRIDENT;
        boolean isCheckBow = checkType == Material.BOW;
        boolean isCheckCrossbow = checkType == Material.CROSSBOW;

        // Already has weapon, no need to pick more.
        if ((npc.isHoldingWeapon() && (isCheckSword || isCheckAxe || isCheckTrident || isCheckBow || isCheckCrossbow))
                || (npc.getOffhandItem().is(Items.SHIELD) && checkType == Material.SHIELD)) return true;

        for (ItemStack item : items) {
            Material type = item.getType();
            if (type == checkType) return true;

            if (checkItemSlot != null && checkItemSlot == ItemStackUtils.getSlotByItem(item)) return true;

            boolean isSword = ItemStackUtils.isSword(item);
            boolean isAxe = ItemStackUtils.isAxe(item);
            boolean isTrident = type == Material.TRIDENT;
            boolean isBow = type == Material.BOW;
            boolean isCrossbow = type == Material.CROSSBOW;
            if ((isCheckSword || isCheckAxe || isCheckTrident || isCheckBow || isCheckCrossbow)
                    && (isSword || isAxe || isTrident || isBow || isCrossbow)) return true;
        }

        return false;
    }

    @Override
    public Object getPreviousItem() {
        return null;
    }

    public static boolean canReach(@NotNull Villager villager, long time) {
        Long cantReachTime = villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).orElse(null);
        if (cantReachTime == null) return true;

        double seconds = (double) (time - cantReachTime) / 20;
        return seconds <= 8;
    }
}