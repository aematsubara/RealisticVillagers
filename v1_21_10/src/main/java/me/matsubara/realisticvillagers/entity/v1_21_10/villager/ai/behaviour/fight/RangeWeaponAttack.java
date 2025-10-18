package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.bukkit.craftbukkit.v1_21_R6.enchantments.CraftEnchantment;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RangeWeaponAttack extends Behavior<Villager> {

    private int delay;
    private CrossbowState state;
    private ItemStack weapon;
    private InteractionHand hand;
    private boolean isCrossbow;

    public RangeWeaponAttack() {
        super(
                ImmutableMap.of(
                        MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                        MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
        state = CrossbowState.UNCHARGED;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        LivingEntity target = getAttackTarget(villager);
        return isHoldingUsableProjectileWeapon(villager)
                && BlockAttackWithShield.notUsingShield(villager)
                && BehaviorUtils.canSee(villager, target)
                && BehaviorUtils.isWithinAttackRange(villager, target, 0);
    }

    private boolean isHoldingUsableProjectileWeapon(@NotNull Villager villager) {
        return villager.isHolding((stack) -> stack.getItem() instanceof ProjectileWeaponItem item
                && villager instanceof VillagerNPC npc && npc.canFireProjectileWeapon(item));
    }

    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        weapon = villager.getMainHandItem();
        hand = ProjectileUtil.getWeaponHoldingHand(villager, weapon.getItem());
        isCrossbow = weapon.is(Items.CROSSBOW);

        // Crossbow was already charged, attack.
        if (CrossbowItem.isCharged(weapon)) {
            state = CrossbowState.READY_TO_ATTACK;
        }
    }

    @Override
    public boolean canStillUse(ServerLevel level, @NotNull Villager villager, long time) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && checkExtraStartConditions(level, villager);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        LivingEntity target = getAttackTarget(villager);
        lookAtTarget(villager, target);
        crossbowAttack((VillagerNPC) villager, target);
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        if (villager.isUsingItem()) villager.stopUsingItem();

        if (villager.isHolding(weapon.getItem())) {
            ((VillagerNPC) villager).setChargingCrossbow(false);
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);
        }

        state = CrossbowState.UNCHARGED;
    }

    @Override
    public boolean timedOut(long time) {
        return false;
    }

    private void crossbowAttack(VillagerNPC npc, LivingEntity target) {
        if (state == CrossbowState.UNCHARGED) {
            npc.startUsingItem(hand);
            state = CrossbowState.CHARGING;
            npc.setChargingCrossbow(true);
        } else if (state == CrossbowState.CHARGING) {
            if (!npc.isUsingItem()) state = CrossbowState.UNCHARGED;

            int chargeDuration = isCrossbow ?
                    CrossbowItem.getChargeDuration(weapon, npc) :
                    BowItem.MAX_DRAW_DURATION;

            if (npc.getTicksUsingItem() < chargeDuration) return;

            // Only stop using here if it's a crossbow and set crossbow as charged.
            if (isCrossbow) {
                stopCharging(npc);

                ItemStack projectile = npc.getProjectile((ProjectileWeaponItem) weapon.getItem());
                addChargedProjectiles(weapon, projectile);
            }

            state = CrossbowState.CHARGED;

            delay = npc.getRandom().nextInt(isCrossbow ? 20 : 10);
        } else if (state == CrossbowState.CHARGED) {
            // Set ready to attack after random delay.
            if (--delay <= 0) state = CrossbowState.READY_TO_ATTACK;
        } else if (state == CrossbowState.READY_TO_ATTACK) {
            // Stop charging here if it's bow.
            if (!isCrossbow) stopCharging(npc);

            boolean multishot = EnchantmentHelper.getItemEnchantmentLevel(CraftEnchantment.bukkitToMinecraftHolder(Enchantment.MULTISHOT), weapon) > 0;

            // Start shooting arrows.
            for (int i = 0; i < (isCrossbow && multishot ? 3 : 1); i++) {
                float force = (i == 0) ? (isCrossbow ? 0.0f : 1.0f) : (i == 1) ? 10.0f : -10.0f;
                npc.performRangedAttack(target, force);
            }

            ItemStack arrow = npc.getProjectile((ProjectileWeaponItem) weapon.getItem());

            // Remove arrow(s).
            boolean infinity = EnchantmentHelper.getItemEnchantmentLevel(CraftEnchantment.bukkitToMinecraftHolder(Enchantment.INFINITY), weapon) > 0;
            if (!infinity || arrow.is(Items.SPECTRAL_ARROW)) {
                arrow.shrink(1);
            }

            // Set crossbow as uncharged.
            weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY);

            state = CrossbowState.UNCHARGED;
        }
    }

    private void addChargedProjectiles(@NotNull ItemStack weapon, @NotNull ItemStack projectile) {
        List<ItemStack> projectiles = new ArrayList<>();
        ItemStack copy = projectile.copy();

        int multishot = EnchantmentHelper.getItemEnchantmentLevel(CraftEnchantment.bukkitToMinecraftHolder(Enchantment.MULTISHOT), weapon);
        for (int i = 0; i < (multishot > 0 ? 3 : 1); i++) {
            projectiles.add(useAmmo(weapon, i == 0 ? projectile : copy, i > 0));
        }

        weapon.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(projectiles));
    }

    protected ItemStack useAmmo(ItemStack weapon, ItemStack projectile, boolean multishot) {
        ItemStack ammo;
        if (multishot || hasInfiniteArrows(weapon, projectile)) {
            ammo = projectile.copyWithCount(1);
            ammo.set(DataComponents.INTANGIBLE_PROJECTILE, Unit.INSTANCE);
        } else {
            ammo = projectile.split(1);
        }
        return ammo;
    }

    protected boolean hasInfiniteArrows(ItemStack weapon, @NotNull ItemStack projectile) {
        return projectile.is(Items.ARROW) && EnchantmentHelper.getItemEnchantmentLevel(CraftEnchantment.bukkitToMinecraftHolder(Enchantment.INFINITY), weapon) > 0;
    }


    private void stopCharging(@NotNull VillagerNPC npc) {
        npc.releaseUsingItem();
        npc.setChargingCrossbow(false);
    }

    private void lookAtTarget(@NotNull Mob mob, LivingEntity target) {
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private @NotNull LivingEntity getAttackTarget(@NotNull LivingEntity target) {
        // Can't be null since the value should be present in the constructor.
        return target.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }

    enum CrossbowState {
        UNCHARGED,
        CHARGING,
        CHARGED,
        READY_TO_ATTACK
    }
}