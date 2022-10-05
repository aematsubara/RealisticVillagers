package me.matsubara.realisticvillagers.entity.v1_19_r1.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.data.ChangeItemType;
import me.matsubara.realisticvillagers.entity.v1_19_r1.villager.VillagerNPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public class Consume extends Behavior<Villager> {

    private ItemStack food;
    private ItemStack previousItem;
    private int duration;

    public Consume() {
        super(ImmutableMap.of(), 100);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return villager instanceof VillagerNPC npc
                && npc.isDoingNothing(ChangeItemType.EATING)
                && (needsFood(npc) || hasHarmfulEffects(npc));
    }

    private boolean needsFood(VillagerNPC npc) {
        return npc.getFoodData().needsFood() && npc.getInventory().getContents().stream().anyMatch(item -> isSafeFood(item.getItem()));
    }

    private boolean hasHarmfulEffects(VillagerNPC npc) {
        Stream<MobEffectInstance> effects = npc.getActiveEffects().stream();
        Stream<ItemStack> inventory = npc.getInventory().getContents().stream();
        return effects.anyMatch(Consume::isHarmful) && inventory.anyMatch(item -> item.is(Items.MILK_BUCKET));
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        previousItem = villager.getMainHandItem().copy();

        Optional<ItemStack> food;
        if (needsFood(npc)) {
            food = villager.getInventory().getContents().stream()
                    .filter(item -> isSafeFood(item.getItem()))
                    .max(Comparator.comparingInt(item -> item.getItem().getFoodProperties().getNutrition()));
        } else {
            food = Optional.ofNullable(Items.MILK_BUCKET.getDefaultInstance());
        }

        if (food.isEmpty()) return;

        npc.setEating(true);
        villager.setItemInHand(InteractionHand.MAIN_HAND, food.get());

        duration = food.get().getUseDuration();
        this.food = food.get();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        if (!(villager instanceof VillagerNPC npc)) return;

        handleEffects(npc);

        if (--duration == 0) {
            if (needsFood(npc)) {
                npc.eat(level, food);
            } else {
                npc.removeAllEffects(EntityPotionEffectEvent.Cause.MILK);

                SimpleContainer inventory = npc.getInventory();
                inventory.removeItemType(food.getItem(), 1);
                inventory.addItem(Items.BUCKET.getDefaultInstance());
            }
        }
    }

    private void handleEffects(VillagerNPC npc) {
        FoodProperties properties = food.getItem().getFoodProperties();

        boolean flag = properties != null && properties.isFastFood();
        flag |= duration <= food.getUseDuration() - 7;

        if (!(flag && duration % 4 == 0)) return;

        RandomSource random = npc.level.random;

        if (needsFood(npc)) {
            spawnFoodParticles(npc);
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
        return duration > 0;
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        villager.setItemInHand(InteractionHand.MAIN_HAND, previousItem);
        if (villager instanceof VillagerNPC npc) npc.setEating(false);
    }

    public static boolean isSafeFood(Item item) {
        FoodProperties properties = item.getFoodProperties();
        return properties != null && properties.getEffects()
                .stream()
                .noneMatch(pair -> isHarmful(pair.getFirst()));
    }

    private static boolean isHarmful(MobEffectInstance instance) {
        return instance.getEffect().getCategory() == MobEffectCategory.HARMFUL;
    }
}