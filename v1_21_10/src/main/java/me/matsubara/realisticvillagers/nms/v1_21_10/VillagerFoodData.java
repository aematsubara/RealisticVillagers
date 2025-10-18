package me.matsubara.realisticvillagers.nms.v1_21_10;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerExhaustionEvent;
import me.matsubara.realisticvillagers.event.VillagerFoodLevelChangeEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodConstants;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_21_R6.inventory.CraftItemStack;
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
public class VillagerFoodData {

    private final VillagerNPC npc;

    private int foodLevel;
    private int tickTimer;
    private float saturationLevel;
    private float exhaustionLevel;

    private static final int SATURATED_REGEN_RATE = 10;
    private static final int UNSATURATED_REGEN_RATE = 80;
    private static final int STARVATION_RATE = 80;

    public VillagerFoodData(VillagerNPC npc) {
        this.npc = npc;
        this.foodLevel = 20;
        this.saturationLevel = 5.0f;
    }

    private void add(int foodLevel, float saturationLevel) {
        this.foodLevel = Math.min(foodLevel + this.foodLevel, 20);
        this.saturationLevel = Math.min(saturationLevel + this.saturationLevel, (float) this.foodLevel);
    }

    public void eat(int foodLevel, float saturationLevel) {
        this.add(foodLevel, FoodConstants.saturationByModifier(foodLevel, saturationLevel));
    }

    public void eat(@NotNull ItemStack item) {
        FoodProperties foodinfo = item.get(DataComponents.FOOD);
        if (foodinfo == null) return;

        int oldFoodLevel = foodLevel;

        VillagerFoodLevelChangeEvent event = callEvent(foodinfo.nutrition() + oldFoodLevel, item);
        if (!event.isCancelled()) add(event.getFoodLevel() - oldFoodLevel, foodinfo.saturation());
    }

    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;

        Difficulty difficulty = level.getDifficulty();

        if (exhaustionLevel > 4.0f) {
            exhaustionLevel -= 4.0f;
            if (saturationLevel > 0.0f) {
                saturationLevel = Math.max(saturationLevel - 1.0f, 0.0f);
            } else if (difficulty != Difficulty.PEACEFUL) {
                VillagerFoodLevelChangeEvent event = callEvent(Math.max(foodLevel - 1, 0), null);
                if (!event.isCancelled()) foodLevel = event.getFoodLevel();
            }
        }

        boolean naturalRegeneration = level.getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION);
        if (naturalRegeneration && saturationLevel > 0.0f && npc.isHurt() && foodLevel >= 20) {
            ++tickTimer;
            if (tickTimer >= SATURATED_REGEN_RATE) {
                float amount = Math.min(saturationLevel, 6.0f);
                npc.heal(amount / 6.0f, RegainReason.SATIATED);
                npc.causeFoodExhaustion(amount, VillagerExhaustionEvent.ExhaustionReason.REGEN);
                tickTimer = 0;
            }
        } else if (naturalRegeneration && foodLevel >= 18 && npc.isHurt()) {
            ++tickTimer;
            if (tickTimer >= UNSATURATED_REGEN_RATE) {
                npc.heal(1.0f, RegainReason.SATIATED);
                npc.causeFoodExhaustion(level.spigotConfig.regenExhaustion, VillagerExhaustionEvent.ExhaustionReason.REGEN);
                tickTimer = 0;
            }
        } else if (foodLevel <= 0) {
            ++tickTimer;
            if (tickTimer >= STARVATION_RATE) {
                if (npc.getHealth() > 10.0f || difficulty == Difficulty.HARD || npc.getHealth() > 1.0f && difficulty == Difficulty.NORMAL) {
                    npc.hurtServer(level, npc.damageSources().starve(), 1.0f);
                }
                tickTimer = 0;
            }
        } else {
            tickTimer = 0;
        }
    }

    public boolean needsFood() {
        return foodLevel < 20;
    }

    public void addExhaustion(float exhaustionLevel) {
        this.exhaustionLevel = Math.min(this.exhaustionLevel + exhaustionLevel, 40.0f);
    }

    private @NotNull VillagerFoodLevelChangeEvent callEvent(int level, @Nullable ItemStack item) {
        VillagerFoodLevelChangeEvent event = new VillagerFoodLevelChangeEvent(
                npc,
                level,
                item != null ? CraftItemStack.asBukkitCopy(item) : null);
        Bukkit.getServer().getPluginManager().callEvent(event);
        return event;
    }
}