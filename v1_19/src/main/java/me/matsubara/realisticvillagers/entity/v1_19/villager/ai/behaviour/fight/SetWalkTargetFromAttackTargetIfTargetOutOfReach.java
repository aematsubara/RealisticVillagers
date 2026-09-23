package me.matsubara.realisticvillagers.entity.v1_19.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_19.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class SetWalkTargetFromAttackTargetIfTargetOutOfReach extends Behavior<Villager> {

    private int cooldown;
    private final Function<LivingEntity, Float> speedModifier;

    public SetWalkTargetFromAttackTargetIfTargetOutOfReach(Function<LivingEntity, Float> speedModifier) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryStatus.REGISTERED));
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return BlockAttackWithShield.notUsingShield(villager) && (!(villager instanceof VillagerNPC npc) || !npc.isAttackingWithTrident());
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (cooldown > 0) cooldown--;
        LivingEntity target = villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        // Use range 1 for the walk target check: stop walking when already close enough to attack.
        double stopDistSqr = 12.25; // 3.5 blocks for melee
        if (villager instanceof VillagerNPC npc && npc.isHoldingRangeWeapon()) {
            stopDistSqr = 100.0; // 10 blocks for archers
        }

        if (BehaviorUtils.canSee(villager, target) && villager.distanceToSqr(target) <= stopDistSqr) {
            clearWalkTarget(villager);
            villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        } else {
            setWalkAndLookTarget(villager, target);
        }
    }

    private void setWalkAndLookTarget(@NotNull Villager villager, LivingEntity target) {
        Brain<Villager> brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), speedModifier.apply(villager), 0));

        // Fake attacks while approaching.
        Path path;
        if (cooldown == 0
                && MeleeAttack.canAttack(villager, true)
                && !BehaviorUtils.isWithinAttackRange(villager, target, 0)
                && villager.getRandom().nextFloat() <= 0.35f
                && ((path = villager.getNavigation().createPath(target, 0)) != null && path.canReach())) {
            villager.swing(InteractionHand.MAIN_HAND);
            cooldown = 10;
        }
    }

    private void clearWalkTarget(@NotNull Villager villager) {
        // Also clear CANT_REACH_WALK_TARGET_SINCE so StopAttackingIfTargetInvalid
        // doesn't prematurely abort the fight after we successfully reach the target.
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }
}