package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.sensing;

import com.google.common.collect.Sets;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.core.VillagerPanicTrigger;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class VillagerHostilesSensor extends NearestVisibleLivingEntitySensor {

    private static final Set<EntityType<?>> SPECIAL_ENTITIES = Sets.newHashSet(EntityType.CREEPER, EntityType.WITCH, EntityType.PLAYER);
    private static final BiPredicate<Player, LivingEntity> IS_BEING_TARGETED = (player, entity) -> entity instanceof Monster monster
            && player.equals(monster.getTarget());

    @Override
    public boolean isMatchingEntity(ServerLevel level, LivingEntity entity, LivingEntity closest) {
        return (isTarget(entity, closest)
                || SPECIAL_ENTITIES.contains(closest.getType())
                || isDefend(entity, closest, this::getNearestFamily, Config.VILLAGER_DEFEND_FAMILY_MEMBER)
                || isDefend(entity, closest, this::getNearestHero, Config.VILLAGER_DEFEND_HERO_OF_THE_VILLAGE)
                || isDefend(entity, closest, this::getFollowing, Config.VILLAGER_DEFEND_FOLLOWING_PLAYER))
                && isClose(entity, closest);
    }

    private boolean isTarget(LivingEntity entity, LivingEntity closest) {
        if (!(entity instanceof VillagerNPC npc)) return false;
        return npc.getTargetEntities().contains(closest.getType());
    }

    private boolean isDefend(LivingEntity entity, LivingEntity closest, Function<Villager, Optional<Player>> playerGetter, Config config) {
        if (!(entity instanceof VillagerNPC npc) || !npc.canAttack()) return false;
        if (!config.asBool()) return false;

        Optional<Player> player = playerGetter.apply(npc);
        return player != null && player.isPresent() && IS_BEING_TARGETED.test(player.get(), closest);
    }

    private boolean isClose(LivingEntity current, LivingEntity closest) {
        if (!(current instanceof VillagerNPC npc)) return false;

        double range = Config.HOSTILE_DETECTION_RANGE.asDouble();
        double distance = range * range;

        double multiplier = npc.canAttack() ? 2.5d : 1.0d;

        if (!isTarget(current, closest) && SPECIAL_ENTITIES.contains(closest.getType())) {
            if (closest instanceof ServerPlayer player) {
                if (VillagerPanicTrigger.ignorePlayer(npc, player)) return false;
                if (!EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(player)) return false;
            }

            if (closest instanceof Creeper creeper) {
                if (creeper.swell < creeper.maxSwell / 3) return false;
                multiplier = 1.0d;
            }
        }

        if (closest instanceof Witch witch && witch.getCurrentRaid() == null) return false;
        if (closest instanceof Vex
                && npc.isHoldingMeleeWeapon()
                && closest.getY() > current.getY() + 1.0d) return false;
        return isClosest(closest, current, distance * multiplier);
    }

    private boolean isClosest(@NotNull LivingEntity first, LivingEntity second, double distance) {
        return first.distanceToSqr(second) <= distance;
    }

    @Override
    public MemoryModuleType<LivingEntity> getMemory() {
        return MemoryModuleType.NEAREST_HOSTILE;
    }

    private Optional<Player> getNearestFamily(Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return Optional.empty();
        return npc.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
                .filter(player -> npc.isFamily(player.getUUID(), true));
    }

    private Optional<Player> getNearestHero(@NotNull Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
                .filter(player -> {
                    if (player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) return true;

                    Raid raid;
                    return player.level() instanceof ServerLevel level
                            && (raid = level.getRaidAt(player.blockPosition())) != null
                            && raid.heroesOfTheVillage.contains(player.getUUID());
                });
    }

    private Optional<Player> getFollowing(Villager villager) {
        if (!(villager instanceof VillagerNPC npc) || !npc.isFollowing()) return Optional.empty();
        return npc.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER)
                .filter(player -> player.getUUID().equals(npc.getInteractingWith()));
    }
}