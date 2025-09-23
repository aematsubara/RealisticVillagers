package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R5.entity.CraftEntity;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.jetbrains.annotations.NotNull;

public class LookAndFollowPlayerSink extends Behavior<Villager> {

    private Player player;
    private boolean isFollowing;

    public LookAndFollowPlayerSink() {
        super(ImmutableMap.of(
                        MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                        MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
                Integer.MAX_VALUE);
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return false;

        isFollowing = npc.isFollowing();

        Player player;
        if (npc.isConversating() || isFollowing) {
            player = level.getPlayerByUUID(npc.getInteractingWith());
        } else if (npc.isExpecting()) {
            player = level.getPlayerByUUID(npc.getExpectingFrom());
        } else if (npc.getProcreatingWith() != null) {
            player = level.getPlayerByUUID(npc.getProcreatingWith());
        } else {
            player = npc.getTradingPlayer();
        }

        if (!isValid(npc, player)) {
            // If villager was following, stop.
            if (isFollowing) npc.stopInteracting();
            return false;
        }

        this.player = player;
        return true;
    }

    @Override
    public boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return checkExtraStartConditions(level, villager);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        followPlayer(villager);
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        int teleportDistance = Config.TELEPORT_WHEN_FOLLOWING_DISTANCE.asInt();
        boolean canTeleport = Config.TELEPORT_WHEN_FOLLOWING_IF_FAR_AWAY.asBool()
                && villager.distanceToSqr(player) > (double) teleportDistance * teleportDistance;

        // Player changed world.
        if (player.level() != level && villager instanceof VillagerNPC npc) {
            npc.stopInteracting();
            return;
        }

        if (canTeleport && !villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            teleportToPlayer(villager.getVehicle() instanceof AbstractHorse horse ? horse : villager, player);
        } else {
            // Don't follow if villager is fighting with someone with a bow.
            if (!(villager instanceof VillagerNPC npc)
                    || !npc.isFighting()
                    || !npc.isHoldingRangeWeapon()
                    || !npc.canFireProjectileWeapon((ProjectileWeaponItem) npc.getMainHandItem().getItem())) {
                followPlayer(villager);
            }
        }
    }

    @Override
    public boolean timedOut(long time) {
        return false;
    }

    private boolean isValid(@NotNull VillagerNPC villager, Player player) {
        boolean checkContainer = villager.getTradingPlayer() != null || villager.isConversating();
        boolean checkForGift = villager.isExpectingGift();

        return villager.isAlive()
                && player != null
                && Bukkit.getServer().getPlayer(player.getUUID()) != null
                && !villager.hurtMarked
                && (!checkForGift || !villager.isGiftDropped())
                && (!checkContainer || player.containerMenu != null);
    }

    private void followPlayer(@NotNull Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        float speed = isFollowing ? VillagerNPC.SPRINT_SPEED.get() : VillagerNPC.WALK_SPEED.get();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(player, false), speed, 2));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
    }

    private void teleportToPlayer(PathfinderMob mob, @NotNull Player player) {
        BlockPos pos = player.blockPosition();

        for (int i = 0; i < 10; i++) {
            int randomX = randomIntInclusive(mob, -3, 3);
            int randomY = randomIntInclusive(mob, -1, 1);
            int randomZ = randomIntInclusive(mob, -3, 3);

            boolean flag = maybeTeleportTo(mob, player, pos.getX() + randomX, pos.getY() + randomY, pos.getZ() + randomZ);
            if (flag) return;
        }
    }

    private boolean maybeTeleportTo(PathfinderMob mob, @NotNull Player player, int x, int y, int z) {
        if (Math.abs((double) x - player.getX()) < 2.0d && Math.abs((double) z - player.getZ()) < 2.0d) return false;
        if (!canTeleportTo((ServerLevel) mob.level(), mob, new BlockPos(x, y, z), false)) return false;

        CraftEntity entity = mob.getBukkitEntity();
        Location to = new Location(entity.getWorld(), (double) x + 0.5d, y, (double) z + 0.5d, mob.getYRot(), mob.getXRot());

        EntityTeleportEvent event = new EntityTeleportEvent(entity, entity.getLocation(), to);
        mob.level().getCraftServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;

        to = event.getTo();
        if (to == null) return false;

        mob.setPos(to.getX(), to.getY(), to.getZ());
        mob.setYRot(to.getYaw());
        mob.setXRot(to.getPitch());
        mob.getNavigation().stop();
        return true;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean canTeleportTo(ServerLevel level, PathfinderMob mob, @NotNull BlockPos pos, boolean canFly) {
        PathType pathType = WalkNodeEvaluator.getPathTypeStatic(mob, pos.mutable());
        if (pathType != PathType.WALKABLE) return false;

        BlockState state = level.getBlockState(pos.below());
        if (!canFly && state.getBlock() instanceof LeavesBlock) return false;

        return level.noCollision(mob, mob.getBoundingBox().move(pos.subtract(mob.blockPosition())));
    }

    private int randomIntInclusive(@NotNull PathfinderMob mob, int origin, int bound) {
        return mob.getRandom().nextInt(bound - origin + 1) + origin;
    }
}