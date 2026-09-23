package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.NotNull;

public class MeleeAttack extends Behavior<Villager> {

    public MeleeAttack() {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED));
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return canAttack(villager, false);
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return canAttack(villager, true);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        performAttack(level, villager);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) {
            if (canAttack(villager, false)) {
                performAttack(level, villager);
            }
        }
    }

    private void performAttack(ServerLevel level, Villager villager) {
        LivingEntity target = getAttackTarget(villager);
        if (target == null || target.isDeadOrDying()) return;

        BehaviorUtils.lookAtEntity(villager, target);

        // Final check before hitting - prevents air hits if target just moved away.
        if (villager.distanceToSqr(target) > 25.0) return;

        // Random jump to be more "realistic".
        if (villager.getRandom().nextFloat() < Config.MELEE_ATTACK_JUMP_CHANCE.asFloat()) {
            villager.getJumpControl().jump();
        }

        villager.swing(InteractionHand.MAIN_HAND);
        villager.doHurtTarget(level, target);
        villager.getMainHandItem().hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);

        setAttackCooldown(villager);
    }

    public static boolean canAttack(Villager villager, boolean ignoreRange) {
        LivingEntity target = getAttackTarget(villager);
        return villager instanceof VillagerNPC npc
                && !npc.isAttackingWithTrident()
                && npc.isHoldingMeleeWeapon()
                && BlockAttackWithShield.notUsingShield(npc)
                && BehaviorUtils.canSee(npc, target)
                && (ignoreRange || npc.distanceToSqr(target) <= 20.25); // Max 4.5 blocks
    }

    public static void setAttackCooldown(@NotNull Villager villager) {
        int cooldown = Config.MELEE_ATTACK_COOLDOWN.asInt();
        villager.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, cooldown);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static @NotNull LivingEntity getAttackTarget(@NotNull Villager villager) {
        // Can't be null since the value should be present in the constructor.
        return villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }
}