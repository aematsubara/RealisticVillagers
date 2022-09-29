package me.matsubara.realisticvillagers.entity.v1_19_r1.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_19_r1.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;

public class MeleeAttack extends Behavior<Villager> {

    private final static int COOLDOWN_BETWEEN_ATTACKS = 10;

    public MeleeAttack() {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        LivingEntity target = getAttackTarget(villager);
        return isHoldingMeleeWeapon(villager)
                && BlockAttackWithShield.notUsingShield(villager)
                && BehaviorUtils.canSee(villager, target)
                && BehaviorUtils.isWithinAttackRange(villager, target, 0);
    }

    private boolean isHoldingMeleeWeapon(Villager villager) {
        return villager.isHolding(item -> item.getItem() instanceof SwordItem || item.getItem() instanceof AxeItem);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        LivingEntity target = getAttackTarget(villager);
        BehaviorUtils.lookAtEntity(villager, target);

        // Random jump to be more "realistic".
        if (villager.getRandom().nextFloat() < 0.45f) {
            villager.getJumpControl().jump();
        }

        villager.swing(InteractionHand.MAIN_HAND);
        villager.doHurtTarget(target);
        villager.getMainHandItem().hurtAndBreak(1, villager, npc -> npc.broadcastBreakEvent(InteractionHand.MAIN_HAND));

        int cooldown;
        if (villager instanceof VillagerNPC npc) {
            cooldown = npc.getMeleeAttackCooldown();
        } else {
            cooldown = COOLDOWN_BETWEEN_ATTACKS;
        }

        villager.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, cooldown);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private LivingEntity getAttackTarget(Villager villager) {
        // Can't be null since the value should be present in the constructor.
        return villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }
}