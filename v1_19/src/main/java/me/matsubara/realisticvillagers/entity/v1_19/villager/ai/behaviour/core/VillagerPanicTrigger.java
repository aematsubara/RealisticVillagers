package me.matsubara.realisticvillagers.entity.v1_19.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import me.matsubara.realisticvillagers.data.TargetReason;
import me.matsubara.realisticvillagers.entity.v1_19.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.util.EntityHead;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.craftbukkit.v1_19_R3.inventory.CraftItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;


public class VillagerPanicTrigger extends Behavior<Villager> {

    private static final ImmutableSet<Item> HALLOWEEN_MASKS = ImmutableSet.of(
            Items.DRAGON_HEAD,
            Items.WITHER_SKELETON_SKULL,
            Items.ZOMBIE_HEAD,
            Items.SKELETON_SKULL,
            Items.CREEPER_HEAD);

    public VillagerPanicTrigger() {
        super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return (isHurt(villager) || hasHostile(villager)) && shouldPanic(villager);
    }

    @Override
    public void start(ServerLevel level, @NotNull Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();

        LivingEntity target = getTarget(villager);
        if (target == null || target instanceof Villager) {
            if (hasNearTNT(villager)) handleNormalReaction(brain);
            return;
        }

        if (target instanceof ServerPlayer player) {
            if (player.isCreative()) return;
            if (villager instanceof VillagerNPC npc && npc.isPartner(player.getUUID())) return;

            boolean atRaid = level.getRaidAt(villager.blockPosition()) != null;
            if (!Config.VILLAGER_ATTACK_PLAYER_DURING_RAID.asBool() && atRaid) return;
        }

        if (villager instanceof VillagerNPC npc) npc.stopAllInteractions();
        if (villager.isPassenger()) villager.stopRiding();

        // Use the same condition as canStillUse(), but the name doesn't mean anything.
        if (canStillUse(level, villager, time)) {
            if (target.getType().getCategory() != MobCategory.MONSTER) return;
            handleNormalReaction(brain);
        } else if (!shouldPanic(villager) && (isHurt(villager) || hasHostile(villager))) {
            handleFightReaction(brain, target, TargetReason.DEFEND);
        }
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        if (time % 100L == 0L) villager.spawnGolemIfNeeded(level, time, 3);
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private @Nullable LivingEntity getTarget(@NotNull Villager villager) {
        Brain<Villager> brain = villager.getBrain();

        if (hasHostile(villager)) {
            LivingEntity direct = brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).get();

            if (direct instanceof ServerPlayer player && villager instanceof VillagerNPC npc) {
                if (ignorePlayer(npc, player)) return null;
            }

            return direct;
        }

        if (!isHurt(villager)) return null;

        Entity direct = villager.getBrain().getMemory(MemoryModuleType.HURT_BY).get().getEntity();
        if (direct instanceof Projectile projectile && projectile.getOwner() != null) {
            return (LivingEntity) projectile.getOwner();
        } else if (direct instanceof LivingEntity living) {
            return living;
        }

        return null;
    }

    private void handleNormalReaction(@NotNull Brain<Villager> brain) {
        if (!brain.isActive(Activity.PANIC)) stopWhatWasDoing(brain);
        brain.setActiveActivityIfPossible(Activity.PANIC);
    }

    public static void handleFightReaction(@NotNull Brain<Villager> brain, LivingEntity target, TargetReason targetReason) {
        if (!brain.isActive(Activity.FIGHT)) stopWhatWasDoing(brain);
        brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
        brain.setMemory(VillagerNPC.TARGET_REASON, targetReason);
        brain.setDefaultActivity(Activity.FIGHT);
        brain.setActiveActivityIfPossible(Activity.FIGHT);
    }

    private static void stopWhatWasDoing(@NotNull Brain<Villager> brain) {
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
    }

    private boolean shouldPanic(Villager villager) {
        return !(villager instanceof VillagerNPC npc) || !npc.canAttack();
    }

    private boolean hasHostile(@NotNull LivingEntity entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
    }

    private boolean isHurt(@NotNull LivingEntity entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }

    private boolean hasNearTNT(@NotNull LivingEntity entity) {
        return entity.getBrain().hasMemoryValue(VillagerNPC.NEAREST_PRIMED_TNT);
    }

    public static boolean ignorePlayer(VillagerNPC npc, Player player) {
        return !isWearingMonsterHead(npc, player) && !isDefendVillager(npc, player) && !npc.getPlayers().contains(player.getUUID());
    }

    private static boolean isWearingMonsterHead(VillagerNPC npc, Player player) {
        return isWearingMonsterHead(player)
                && Config.ATTACK_PLAYER_WEARING_MONSTER_SKULL.asBool()
                && getTypeBySkullType(player.getItemBySlot(EquipmentSlot.HEAD))
                .map(entityType -> npc.getTargetEntities().contains(entityType)).orElse(false);
    }

    private static @NotNull Optional<EntityType<?>> getTypeBySkullType(ItemStack item) {
        for (EntityHead skull : EntityHead.values()) {
            if (CraftItemStack.asNMSCopy(skull.getHead()).is(item.getItem())) {
                org.bukkit.entity.EntityType type = skull.getType();
                if (type != null) EntityType.byString(type.name().toLowerCase(Locale.ROOT));
            }
        }
        return Optional.empty();
    }

    private static boolean isWearingMonsterHead(@NotNull LivingEntity entity) {
        ItemStack current = entity.getItemBySlot(EquipmentSlot.HEAD);
        for (Item head : HALLOWEEN_MASKS) {
            if (current.is(head)) return true;
        }
        return false;
    }

    private static boolean isDefendVillager(LivingEntity entity, LivingEntity closest) {
        if (!(entity instanceof VillagerNPC npc)) return false;
        if (!(closest instanceof Player player)) return false;
        if (!Config.VILLAGER_DEFEND_FAMILY_MEMBER.asBool()) return false;

        Optional<Player> damager = getPlayerFightningFamily(npc);
        return damager.isPresent() && damager.get().is(player);
    }

    private static Optional<Player> getPlayerFightningFamily(Villager villager) {
        Optional<Player> empty = Optional.empty();
        if (!(villager instanceof VillagerNPC npc)) return empty;

        Optional<NearestVisibleLivingEntities> nearest = npc.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        if (nearest.isEmpty()) return empty;

        Optional<LivingEntity> closest = nearest.get().findClosest(
                living -> living instanceof VillagerNPC && npc.isFamily(living.getUUID(), true));
        if (closest.isEmpty() || !(closest.get() instanceof VillagerNPC family)) return empty;

        Optional<LivingEntity> hurtBy = family.getBrain().getMemory(MemoryModuleType.HURT_BY_ENTITY);
        return hurtBy.isPresent() && hurtBy.get() instanceof Player player ? Optional.of(player) : empty;
    }
}