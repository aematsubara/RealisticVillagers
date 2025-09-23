package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.data.Exchangeable;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ConsumeEffect;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.v1_21_R5.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;

public class Consume extends Behavior<Villager> implements Exchangeable {

    private ConsumeType type;
    private ItemStack food;
    private @Getter ItemStack previousItem;
    private int duration;

    public static final ImmutableSet<Holder<MobEffect>> HEALTH_EFFECTS = ImmutableSet.of(
            MobEffects.INSTANT_HEALTH,
            MobEffects.REGENERATION,
            MobEffects.HEALTH_BOOST,
            MobEffects.ABSORPTION);

    public static final ImmutableSet<Holder<MobEffect>> POWER_EFFECTS = ImmutableSet.of(
            MobEffects.SPEED,
            MobEffects.STRENGTH,
            MobEffects.RESISTANCE,
            MobEffects.FIRE_RESISTANCE);

    public static final BiPredicate<ItemStack, Set<Holder<MobEffect>>> POTION_PREDICATE = (item, effects) -> {
        if (!item.is(Items.POTION)) return false;

        PotionContents contents = item.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;

        boolean isWanted = false;
        for (MobEffectInstance instance : contents.getAllEffects()) {
            if (isHarmful(instance)) return false;
            if (effects.contains(instance.getEffect())) {
                isWanted = true;
            }
        }

        return isWanted;
    };

    public Consume() {
        super(ImmutableMap.of(), 100);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc) || npc.isSleeping() || !npc.isDoingNothing(ChangeItemType.EATING)) {
            return false;
        }

        if (needsFood(npc)) {
            type = ConsumeType.FOOD;
        } else if (hasHarmfulEffects(npc)) {
            type = ConsumeType.HARMFUL;
        } else if (needsHealthPotion(npc)) {
            type = ConsumeType.HEALTH;
        } else if (needsPowerPotion(npc)) {
            type = ConsumeType.POWER;
        }

        return type != null;
    }

    private boolean needsFood(@NotNull VillagerNPC npc) {
        return npc.getFoodData().needsFood() && npc.getInventory().hasAnyMatching(item -> isSafeFood(item.getItem()));
    }

    private boolean hasHarmfulEffects(@NotNull VillagerNPC npc) {
        return hasHarmfulEffects(npc.getActiveEffects()) && npc.getInventory().hasAnyMatching(item -> item.is(Items.MILK_BUCKET));
    }

    public static boolean hasHarmfulEffects(@NotNull Collection<MobEffectInstance> effects) {
        for (MobEffectInstance effect : effects) {
            if (isHarmful(effect)) return true;
        }
        return false;
    }

    private boolean needsHealthPotion(@NotNull VillagerNPC npc) {
        return npc.getHealth() < npc.getMaxHealth()
                && npc.getInventory().hasAnyMatching(item -> POTION_PREDICATE.test(item, HEALTH_EFFECTS));
    }

    private boolean needsPowerPotion(@NotNull VillagerNPC npc) {
        Raid raid;
        return npc.checkCurrentActivity(Activity.PRE_RAID)
                && npc.level() instanceof ServerLevel level
                && (raid = level.getRaidAt(npc.blockPosition())) != null
                && raid.getGroupsSpawned() < raid.numGroups
                && npc.getInventory().hasAnyMatching(item -> POTION_PREDICATE.test(item, POWER_EFFECTS));
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        Optional<ItemStack> food;
        if (type.isFood()) {
            food = villager.getInventory().getContents().stream()
                    .filter(item -> isSafeFood(item.getItem()))
                    .max(Comparator.comparingInt(item -> {
                        FoodProperties properties = item.get(DataComponents.FOOD);
                        return properties != null ? properties.nutrition() : 1;
                    }));
        } else if (type.isHarmful()) {
            food = Optional.of(Items.MILK_BUCKET.getDefaultInstance());
        } else {
            food = villager.getInventory().getContents().stream()
                    .filter(item -> POTION_PREDICATE.test(item, type.isHealth() ?
                            HEALTH_EFFECTS : type.isPower() ?
                            POWER_EFFECTS :
                            Collections.emptySet()))
                    .findFirst();
        }

        if (food.isEmpty()) return;

        npc.setEating(true);
        previousItem = villager.getMainHandItem();

        villager.setItemInHand(InteractionHand.MAIN_HAND, food.get());

        duration = food.get().getUseDuration(villager);
        this.food = food.get();
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) {
            npc.setEating(false);
            npc.setItemInHand(InteractionHand.MAIN_HAND, previousItem);
        }
        type = null;
        duration = 0;
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc) || food == null) return;

        handleEffects(npc);

        if (--duration != 0) return;

        SimpleContainer inventory = npc.getInventory();
        if (type.isFood()) {
            npc.eat(level, food);
            return;
        } else if (type.isHarmful()) {
            npc.removeAllEffects(EntityPotionEffectEvent.Cause.MILK);

            inventory.removeItemType(food.getItem(), 1);
            inventory.addItem(Items.BUCKET.getDefaultInstance());
        } else {
            PotionContents contents = food.get(DataComponents.POTION_CONTENTS);
            if (contents != null) {
                for (MobEffectInstance instance : contents.getAllEffects()) {
                    MobEffect effect = instance.getEffect().value();
                    if (effect.isInstantenous()) {
                        effect.applyInstantenousEffect(null, null, npc, null, instance.getAmplifier(), 1.0d);
                    } else {
                        npc.addEffect(new MobEffectInstance(instance), EntityPotionEffectEvent.Cause.POTION_DRINK);
                    }
                }
            }
            food.shrink(1);
        }

        inventory.addItem(Items.GLASS_BOTTLE.getDefaultInstance());
        npc.gameEvent(GameEvent.DRINK);
    }

    private void handleEffects(VillagerNPC npc) {
        if (!shouldTriggerItemUseEffects(npc)) return;

        RandomSource random = npc.level().random;

        if (type.isFood()) {
            if (!food.is(Items.HONEY_BOTTLE)) spawnFoodParticles(npc);

            Consumable consumable = food.get(DataComponents.CONSUMABLE);
            if (consumable == null) return;

            npc.playSound(
                    consumable.sound().value(),
                    0.5f + 0.5f * (float) random.nextInt(2),
                    (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
        } else {
            npc.playSound(SoundEvents.GENERIC_DRINK.value(), 0.5f, random.nextFloat() * 0.1f + 0.9f);
        }
    }

    private boolean shouldTriggerItemUseEffects(Villager villager) {
        boolean flag = food.getUseDuration(villager) - duration > (int) ((float) food.getUseDuration(villager) * 0.21875f);
        return flag && duration % 4 == 0;
    }

    private void spawnFoodParticles(Villager villager) {
        for (int i = 0; i < 16; i++) {
            Location location = villager.getBukkitEntity()
                    .getLocation()
                    .clone()
                    .add(0.0d, 1.42d, 0.0d);

            villager.level().getWorld().spawnParticle(
                    Particle.ITEM,
                    location.add(PluginUtils.offsetVector(new Vector(0.0d, 0.0d, 0.3d), location.getYaw(), location.getPitch())),
                    1,
                    0.03d,
                    0.125d,
                    0.03d,
                    0.0d,
                    CraftItemStack.asBukkitCopy(food));
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return duration > 0 && food != null;
    }

    public static boolean isSafeFood(@NotNull Item item) {
        FoodProperties properties = item.getDefaultInstance().get(DataComponents.FOOD);
        if (properties == null) return false;

        Consumable consumable = item.getDefaultInstance().get(DataComponents.CONSUMABLE);
        if (consumable == null) return true;

        for (ConsumeEffect effect : consumable.onConsumeEffects()) {
            if (!(effect instanceof ApplyStatusEffectsConsumeEffect apply)) continue;

            for (MobEffectInstance instance : apply.effects()) {
                if (isHarmful(instance)) return false;
            }
        }

        return true;
    }

    private static boolean isHarmful(@NotNull MobEffectInstance instance) {
        return instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL;
    }

    public enum ConsumeType {
        FOOD,
        HARMFUL,
        HEALTH,
        POWER;

        public boolean isFood() {
            return this == FOOD;
        }

        public boolean isHarmful() {
            return this == HARMFUL;
        }

        public boolean isHealth() {
            return this == HEALTH;
        }

        public boolean isPower() {
            return this == POWER;
        }
    }
}