package me.matsubara.realisticvillagers.entity.v1_18_r2.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Messages;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.bukkit.Bukkit;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class StopAttackingIfTargetInvalid extends Behavior<Villager> {

    private final static double MAX_DISTANCE_LIMIT = 24.0d;
    private final static long TIMEOUT_TO_GET_WITHIN_ATTACK_RANGE = 200L;
    private final Predicate<LivingEntity> stopAttackingWhen;
    private final Consumer<Villager> onTargetErased;

    public StopAttackingIfTargetInvalid(Predicate<LivingEntity> predicate, Consumer<Villager> consumer) {
        super(ImmutableMap.of(
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, MemoryStatus.REGISTERED));
        this.stopAttackingWhen = predicate;
        this.onTargetErased = consumer;
    }

    public StopAttackingIfTargetInvalid() {
        this((living) -> false, (villager) -> {
        });
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();

        if (!brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            Optional<Activity> activity = brain.getActiveNonCoreActivity();
            if (activity.isPresent()) {
                backToDefault(villager);
            }
            return;
        }

        LivingEntity target = getAttackTarget(villager);
        if (!villager.canAttack(target)
                || isTiredOfTryingToReachTarget(villager)
                || isCurrentTargetDeadOrRemoved(villager)
                || isCurrentTargetInDifferentLevel(villager)
                || stopAttackingWhen.test(getAttackTarget(villager))
                || noWeapon(villager)
                || isCurrentTargetOffline(villager)) {
            clearAttackTarget(villager);
        } else if (isCurrentTargetFarAway(villager)) {
            if (villager instanceof VillagerNPC npc && target instanceof ServerPlayer player) {
                npc.getPlugin().getMessages().send(npc, player.getBukkitEntity(), Messages.Message.RAN_AWAY);
            }
            clearAttackTarget(villager);
        }
    }

    private boolean isCurrentTargetInDifferentLevel(Villager villager) {
        return getAttackTarget(villager).level != villager.level;
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private LivingEntity getAttackTarget(Villager villager) {
        // Can't be null since the value should be present in the constructor.
        return villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }

    private boolean noWeapon(Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return false;
        return !npc.isHoldingWeapon();
    }

    private boolean isCurrentTargetOffline(Villager villager) {
        LivingEntity target = getAttackTarget(villager);
        return target instanceof ServerPlayer && Bukkit.getServer().getPlayer(target.getUUID()) == null;
    }

    private boolean isTiredOfTryingToReachTarget(Villager villager) {
        Optional<Long> optional = villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        return optional.isPresent() && villager.level.getGameTime() - optional.get() > TIMEOUT_TO_GET_WITHIN_ATTACK_RANGE;
    }

    private boolean isCurrentTargetDeadOrRemoved(Villager villager) {
        return !getAttackTarget(villager).isAlive();
    }

    private boolean isCurrentTargetFarAway(Villager villager) {
        return villager.distanceTo(getAttackTarget(villager)) > MAX_DISTANCE_LIMIT;
    }

    private void clearAttackTarget(Villager villager) {
        onTargetErased.accept(villager);

        villager.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        backToDefault(villager);
    }

    private void backToDefault(Villager villager) {
        villager.getBrain().setDefaultActivity(Activity.IDLE);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        villager.getBrain().updateActivityFromSchedule(villager.level.getDayTime(), villager.level.getGameTime());
    }
}