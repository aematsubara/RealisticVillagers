package me.matsubara.realisticvillagers.entity.v1_21_10.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
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
        if (BehaviorUtils.canSee(villager, target) && BehaviorUtils.isWithinAttackRange(villager, target, 1)) {
            clearWalkTarget(villager);
        } else {
            setWalkAndLookTarget(villager, target);
        }
    }

    private void setWalkAndLookTarget(@NotNull Villager villager, LivingEntity target) {
        Brain<Villager> brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), speedModifier.apply(villager), 0));

        // Fake attacks.
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
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}