package me.matsubara.realisticvillagers.entity.v1_19_r1.villager.ai.behaviour.core;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_19_r1.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.bukkit.Bukkit;

public class LookAndFollowPlayerSink extends Behavior<Villager> {

    private final float speedModifier;
    private Player player;
    private boolean isFollowing;

    public LookAndFollowPlayerSink(float speedModifier) {
        super(ImmutableMap.of(
                        MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                        MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED),
                Integer.MAX_VALUE);
        this.speedModifier = speedModifier;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!(villager instanceof VillagerNPC npc)) return false;

        Player player;
        if (npc.isConversating() || npc.isFollowing()) {
            isFollowing = npc.isFollowing();
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
            if (npc.isFollowing()) npc.stopInteracting();
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
    public void stop(ServerLevel level, Villager villager, long time) {
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
        if (player.level != level && villager instanceof VillagerNPC npc) {
            npc.stopInteracting();
            return;
        }

        if (canTeleport) {
            villager.absMoveTo(player.getX(), player.getY(), player.getZ(), player.getYHeadRot(), player.getXRot());
            villager.setYHeadRot(player.getYHeadRot());
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

    private boolean isValid(VillagerNPC villager, Player player) {
        boolean checkContainer = villager.getTradingPlayer() != null || villager.isConversating();
        boolean checkForGift = villager.isExpectingGift();

        return villager.isAlive()
                && player != null
                && Bukkit.getServer().getPlayer(player.getUUID()) != null
                && !villager.hurtMarked
                && (!checkForGift || !villager.isGiftDropped())
                && (!checkContainer || player.containerMenu != null);
    }

    private void followPlayer(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        float speed = speedModifier * (isFollowing ? 1.25f : 1f);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(player, false), speed, 2));
        brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
    }
}