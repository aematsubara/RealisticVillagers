package me.matsubara.realisticvillagers.entity.v1_18_r2.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_18_r2.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

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

    private boolean isHoldingUsableProjectileWeapon(Villager villager) {
        return villager.isHolding((stack) -> stack.getItem() instanceof ProjectileWeaponItem item
                && villager instanceof VillagerNPC npc && npc.canFireProjectileWeapon(item));
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        weapon = villager.getMainHandItem();
        hand = ProjectileUtil.getWeaponHoldingHand(villager, weapon.getItem());
        isCrossbow = weapon.is(Items.CROSSBOW);
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET) && checkExtraStartConditions(level, villager);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        LivingEntity target = getAttackTarget(villager);
        lookAtTarget(villager, target);
        crossbowAttack((VillagerNPC) villager, target);
    }

    @Override
    public void stop(ServerLevel level, Villager villager, long time) {
        if (villager.isUsingItem()) villager.stopUsingItem();

        if (villager.isHolding(weapon.getItem())) {
            ((VillagerNPC) villager).setChargingCrossbow(false);
            CrossbowItem.setCharged(weapon, false);
        }
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
                    CrossbowItem.getChargeDuration(weapon) :
                    BowItem.MAX_DRAW_DURATION;

            if (npc.getTicksUsingItem() < chargeDuration) return;

            // Only stop releasing here if it's crossbow.
            if (isCrossbow) stopCharging(npc);

            // Set crossbow as charged.
            CrossbowItem.setCharged(weapon, true);

            state = CrossbowState.CHARGED;

            delay = npc.getRandom().nextInt(isCrossbow ? 20 : 10);
        } else if (state == CrossbowState.CHARGED) {
            // Set ready to attack after random delay.
            if (--delay <= 0) state = CrossbowState.READY_TO_ATTACK;
        } else if (state == CrossbowState.READY_TO_ATTACK) {
            // Stop charging here if it's bow.
            if (!isCrossbow) stopCharging(npc);

            boolean multishot = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MULTISHOT, weapon) > 0;

            // Start shooting arrows.
            for (int i = 0; i < (isCrossbow && multishot ? 3 : 1); i++) {
                float force = (i == 0) ? (isCrossbow ? 0.0f : 1.0f) : (i == 1) ? 10.0f : -10.0f;
                npc.performRangedAttack(target, force);
            }

            if (Config.REQUIRE_ARROWS_FOR_PROJECTILE_WEAPON.asBool()) {
                // Remove arrow(s).
                boolean hasInfinity = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, weapon) > 0;
                if (!hasInfinity) npc.getInventory().removeItemType(Items.ARROW, 1);
            }

            // Set crossbow as uncharged.
            CrossbowItem.setCharged(weapon, false);

            state = CrossbowState.UNCHARGED;
        }
    }

    private void stopCharging(VillagerNPC npc) {
        npc.releaseUsingItem();
        npc.setChargingCrossbow(false);
    }

    private void lookAtTarget(Mob mob, LivingEntity target) {
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private LivingEntity getAttackTarget(LivingEntity target) {
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