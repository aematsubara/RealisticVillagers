package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import lombok.Getter;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.data.Exchangeable;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

public class ShowTradesToPlayer extends Behavior<Villager> implements Exchangeable {

    private static final int MAX_LOOK_TIME = 900;
    private static final int STARTING_LOOK_TIME = 40;
    private @Nullable ItemStack playerItemStack;
    private final List<ItemStack> displayItems = Lists.newArrayList();
    private int cycleCounter;
    private int displayIndex;
    private int lookTime;

    private @Getter ItemStack previousItem;

    public ShowTradesToPlayer(int minDuration, int maxDuration) {
        super(ImmutableMap.of(
                        MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_PRESENT,
                        MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT),
                minDuration,
                maxDuration);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, @NotNull Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        if (brain.getMemory(MemoryModuleType.INTERACTION_TARGET).isEmpty()) return false;

        LivingEntity target = brain.getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        if (target.getMainHandItem().isEmpty()) return false;

        boolean canShow = target.getType() == EntityType.PLAYER
                && villager.isAlive()
                && target.isAlive()
                && !villager.isBaby()
                && villager.distanceToSqr(target) <= 17.0d
                && (!(villager instanceof VillagerNPC npc) || npc.isDoingNothing(ChangeItemType.SHOWING_TRADES));
        if (!canShow) return false;

        for (MerchantOffer offer : villager.getOffers()) {
            if (!offer.isOutOfStock() && playerItemStackMatchesCostOfOffer(offer, target.getMainHandItem())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return checkExtraStartConditions(level, villager)
                && lookTime > 0
                && villager.getBrain().getMemory(MemoryModuleType.INTERACTION_TARGET).isPresent();
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setShowingTrades(true);
            previousItem = npc.getMainHandItem();
        }

        lookAtTarget(villager);
        cycleCounter = 0;
        displayIndex = 0;
        lookTime = STARTING_LOOK_TIME;
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setShowingTrades(false);
            clearHeldItem(npc);
        }

        villager.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        playerItemStack = null;
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        LivingEntity target = lookAtTarget(villager);
        findItemsToDisplay(target, villager);
        if (!displayItems.isEmpty()) {
            displayCyclingItems(villager);
        } else {
            clearHeldItem(villager);
            lookTime = Math.min(lookTime, STARTING_LOOK_TIME);
        }
        --lookTime;
    }

    private void findItemsToDisplay(@NotNull LivingEntity living, Villager villager) {
        boolean flag = false;
        ItemStack handItem = living.getMainHandItem();
        if (playerItemStack == null || !ItemStack.isSameItem(playerItemStack, handItem)) {
            playerItemStack = handItem;
            flag = true;
            displayItems.clear();
        }

        if (!flag || playerItemStack == null || playerItemStack.isEmpty()) return;

        updateDisplayItems(villager);
        if (!displayItems.isEmpty()) {
            lookTime = MAX_LOOK_TIME;
            displayFirstItem(villager);
        }
    }

    private void displayFirstItem(Villager villager) {
        displayAsHeldItem(villager, displayItems.getFirst());
    }

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    private void updateDisplayItems(@NotNull Villager villager) {
        Iterator<MerchantOffer> iterator = villager.getOffers().iterator();

        while (iterator.hasNext()) {
            MerchantOffer offer = iterator.next();
            if (!offer.isOutOfStock() && playerItemStackMatchesCostOfOffer(offer)) {
                displayItems.add(offer.getResult());
            }
        }
    }

    private boolean playerItemStackMatchesCostOfOffer(MerchantOffer offer) {
        return playerItemStackMatchesCostOfOffer(offer, playerItemStack);
    }

    private boolean playerItemStackMatchesCostOfOffer(@NotNull MerchantOffer offer, ItemStack item) {
        return ItemStack.isSameItem(item, offer.getCostA()) || ItemStack.isSameItem(item, offer.getCostB());
    }

    private void clearHeldItem(Villager villager) {
        displayInMainHand(villager, previousItem);
    }

    private void displayAsHeldItem(Villager villager, ItemStack item) {
        displayInMainHand(villager, item);
    }

    private void displayInMainHand(@NotNull Villager villager, ItemStack item) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, item);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private @NotNull LivingEntity lookAtTarget(@NotNull Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        // Can't be null since the value should be present in the constructor.
        LivingEntity target = brain.getMemory(MemoryModuleType.INTERACTION_TARGET).get();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        return target;
    }

    private void displayCyclingItems(Villager villager) {
        if (displayItems.size() < 2 || ++cycleCounter < STARTING_LOOK_TIME) return;

        ++displayIndex;
        cycleCounter = 0;
        if (displayIndex > displayItems.size() - 1) {
            displayIndex = 0;
        }

        displayAsHeldItem(villager, displayItems.get(displayIndex));
    }
}