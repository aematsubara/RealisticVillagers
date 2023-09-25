package me.matsubara.realisticvillagers.entity.v1_19_r3.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import lombok.Getter;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.data.Exchangeable;
import me.matsubara.realisticvillagers.entity.v1_19_r3.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.gameevent.GameEvent;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
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

    public static final Set<MobEffect> HEALTH_EFFECTS = ImmutableSet.of(
            MobEffects.HEAL,
            MobEffects.REGENERATION,
            MobEffects.HEALTH_BOOST,
            MobEffects.ABSORPTION);

    public static final Set<MobEffect> POWER_EFFECTS = ImmutableSet.of(
            MobEffects.MOVEMENT_SPEED,
            MobEffects.DAMAGE_BOOST,
            MobEffects.DAMAGE_RESISTANCE,
            MobEffects.FIRE_RESISTANCE);

    public static final BiPredicate<ItemStack, Set<MobEffect>> POTION_PREDICATE = (item, effects) -> {
        if (!item.is(Items.POTION)) return false;

        Potion potion = PotionUtils.getPotion(item);
        if (potion == null) return false;

        boolean isWanted = false;
        for (MobEffectInstance instance : potion.getEffects()) {
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
                && npc.level instanceof ServerLevel level
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
                    .max(Comparator.comparingInt(item -> item.getItem().getFoodProperties().getNutrition()));
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

        duration = food.get().getUseDuration();
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
            for (MobEffectInstance instance : PotionUtils.getMobEffects(food)) {
                MobEffect effect = instance.getEffect();
                if (effect.isInstantenous()) {
                    effect.applyInstantenousEffect(null, null, npc, instance.getAmplifier(), 1.0d);
                } else {
                    npc.addEffect(new MobEffectInstance(instance), EntityPotionEffectEvent.Cause.POTION_DRINK);
                }
            }
            food.shrink(1);
        }

        inventory.addItem(Items.GLASS_BOTTLE.getDefaultInstance());
        npc.gameEvent(GameEvent.DRINK);
    }

    private void handleEffects(VillagerNPC npc) {
        FoodProperties properties = food.getItem().getFoodProperties();

        boolean flag = properties != null && properties.isFastFood();
        flag |= duration <= food.getUseDuration() - 7;

        if (!(flag && duration % 4 == 0)) return;

        RandomSource random = npc.level.random;

        if (type.isFood()) {
            if (!food.is(Items.HONEY_BOTTLE)) spawnFoodParticles(npc);
            npc.playSound(
                    food.getEatingSound(),
                    0.5f + 0.5f * (float) random.nextInt(2),
                    (random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
        } else {
            npc.playSound(food.getDrinkingSound(), 0.5f, random.nextFloat() * 0.1f + 0.9f);
        }
    }

    private void spawnFoodParticles(Villager villager) {
        for (int i = 0; i < 16; i++) {
            Location location = villager.getBukkitEntity()
                    .getLocation()
                    .clone()
                    .add(0.0d, 1.42d, 0.0d);

            villager.level.getWorld().spawnParticle(
                    Particle.ITEM_CRACK,
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
        FoodProperties properties = item.getFoodProperties();
        if (properties == null) return false;

        for (Pair<MobEffectInstance, Float> effect : properties.getEffects()) {
            if (isHarmful(effect.getFirst())) return false;
        }

        return true;
    }

    private static boolean isHarmful(@NotNull MobEffectInstance instance) {
        return instance.getEffect().getCategory() == MobEffectCategory.HARMFUL;
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