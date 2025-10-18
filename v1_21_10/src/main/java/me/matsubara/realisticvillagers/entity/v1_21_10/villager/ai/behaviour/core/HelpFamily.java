package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.data.Exchangeable;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class HelpFamily extends Behavior<Villager> implements Exchangeable {

    private final float speedModifier;
    private @Getter ItemStack previousItem;
    private ItemStack food;
    private Player target;
    private Consume.ConsumeType type;

    private static final int DISTANCE_TO_HELP = 3;

    @SuppressWarnings("ConstantConditions")
    public HelpFamily(int duration, float speedModifier) {
        super(ImmutableMap.of(
                        MemoryModuleType.NEAREST_VISIBLE_PLAYER, MemoryStatus.VALUE_PRESENT,
                        VillagerNPC.HAS_HELPED_FAMILY_RECENTLY, MemoryStatus.VALUE_ABSENT),
                duration);
        this.speedModifier = speedModifier;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        boolean first = villager instanceof VillagerNPC npc
                && npc.isDoingNothing(ChangeItemType.HELPING_FAMILY)
                && !npc.getBrain().hasMemoryValue(VillagerNPC.HAS_HELPED_FAMILY_RECENTLY)
                && !npc.isSleeping()
                && Config.VILLAGER_HELP_FAMILY.asBool();
        if (!first) return false;

        if (canHelpNearestHungry(villager)) {
            target = getNearestHungry(villager).get();
            type = Consume.ConsumeType.FOOD;
        } else if (canHelpNearestPoisoned(villager)) {
            target = getNearestPoisoned(villager).get();
            type = Consume.ConsumeType.HARMFUL;
        } else if (canHelpNearestDamaged(villager)) {
            target = getNearestDamaged(villager).get();
            type = Consume.ConsumeType.HEALTH;
        }

        return type != null;
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long game) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        Optional<ItemStack> food;
        if (type.isFood()) {
            food = villager.getInventory().getContents().stream()
                    .filter(item -> Consume.isSafeFood(item.getItem()))
                    .max(Comparator.comparingInt(value -> {
                        FoodProperties info = value.get(DataComponents.FOOD);
                        return info != null ? info.nutrition() : 1;
                    }));
        } else if (type.isHarmful()) {
            food = Optional.of(Items.MILK_BUCKET.getDefaultInstance());
        } else {
            // Then it's health since we don't use power here.
            food = villager.getInventory().getContents().stream()
                    .filter(item -> Consume.POTION_PREDICATE.test(item, type.isHealth() ? Consume.HEALTH_EFFECTS : Collections.emptySet()))
                    .findFirst();
        }

        if (food.isEmpty()) return;

        npc.setHelpingFamily(true);
        previousItem = npc.getMainHandItem();

        villager.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, target);
        BehaviorUtils.lookAtEntity(villager, target);

        villager.setItemInHand(InteractionHand.MAIN_HAND, food.get());
        this.food = food.get();
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setHelpingFamily(false);
            npc.setItemSlot(EquipmentSlot.MAINHAND, previousItem);
        }

        target = null;
        type = null;

        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        BehaviorUtils.lookAtEntity(villager, target);

        if (!isWithinHelpingDistance(villager, target)) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, target, speedModifier, 0);
            return;
        }

        npc.drop(food.copyWithCount(1), npc.getPlugin().getIgnoreItemKey());

        // Swing hand.
        villager.swing(InteractionHand.MAIN_HAND);

        // Time to try to help again (in ticks).
        long cooldown = Config.VILLAGER_HELP_FAMILY_COOLDOWN.asLong();
        if (cooldown > 0L) {
            villager.getBrain().setMemoryWithExpiry(VillagerNPC.HAS_HELPED_FAMILY_RECENTLY, true, cooldown);
        }

        if (type.isHarmful()) {
            // Remove bucket.
            villager.getInventory().removeItemType(food.getItem(), 1);
        } else {
            // Remove food.
            food.shrink(1);
        }
    }

    private boolean canHelpNearestHungry(Villager villager) {
        return getNearestHungry(villager).isPresent() && villager.getInventory().hasAnyMatching(item -> Consume.isSafeFood(item.getItem()));
    }

    private Optional<Player> getNearestHungry(Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return Optional.empty();
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
                .filter(player -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)
                        && player.getFoodData().needsFood()
                        && npc.isFamily(player.getUUID(), true));
    }

    private boolean canHelpNearestPoisoned(Villager villager) {
        return getNearestPoisoned(villager).isPresent() && villager.getInventory().hasAnyMatching(item -> item.is(Items.MILK_BUCKET));
    }

    private Optional<Player> getNearestPoisoned(Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return Optional.empty();
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
                .filter(player -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)
                        && Consume.hasHarmfulEffects(player.getActiveEffects())
                        && npc.isFamily(player.getUUID(), true));
    }

    private boolean canHelpNearestDamaged(Villager villager) {
        return getNearestDamaged(villager).isPresent() && villager.getInventory().hasAnyMatching(item -> Consume.POTION_PREDICATE.test(item, Consume.HEALTH_EFFECTS));
    }

    private Optional<Player> getNearestDamaged(Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return Optional.empty();
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
                .filter(player -> EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)
                        && player.getHealth() < player.getMaxHealth()
                        && npc.isFamily(player.getUUID(), true));
    }

    private boolean isWithinHelpingDistance(@NotNull Villager villager, @NotNull Player player) {
        return villager.blockPosition().closerThan(player.blockPosition(), DISTANCE_TO_HELP);
    }
}