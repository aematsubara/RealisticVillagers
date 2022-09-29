package me.matsubara.realisticvillagers.entity.v1_18_r2;

import lombok.Setter;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Pet;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftWolf;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class PetWolf extends Wolf implements Pet {

    @Setter
    private boolean tamedByPlayer;

    public PetWolf(EntityType<? extends Wolf> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(1, new WolfPanicGoal(this, 1.5d));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new WolfAvoidEntityGoal<>(this, Llama.class, 24.0f, 1.5d, 1.5d));
        goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4f));
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.0d, true));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0d, 10.0f, 2.0f, false));
        goalSelector.addGoal(7, new BreedGoal(this, 1.0d));
        goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0f));
        goalSelector.addGoal(9, new BegGoal(this, 8.0f));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, VillagerNPC.class, 8.0f));
        goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this).setAlertOthers());
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, VillagerNPC.class, 10, true, false, this::isAngryAt));
        targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false, PREY_SELECTOR));
        targetSelector.addGoal(6, new NonTameRandomTargetGoal<>(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
        targetSelector.addGoal(7, new NearestAttackableTargetGoal<>(this, AbstractSkeleton.class, false));
        targetSelector.addGoal(8, new ResetUniversalAngerTargetGoal<>(this, true));
    }

    @Override
    public void tameByVillager(IVillagerNPC npc) {
        setTame(true);
        setOwnerUUID(npc.bukkit().getUniqueId());
        setTamedByPlayer(false);
        getNavigation().stop();
        setTarget(null);
        level.broadcastEntityEvent(this, (byte) 7);
        setPersistenceRequired();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("TamedByPlayer", tamedByPlayer);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tamedByPlayer = tag.getBoolean("TamedByPlayer");
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        if (tamedByPlayer) return super.getOwner();

        UUID ownerUUID = getOwnerUUID();
        return ownerUUID != null ? (LivingEntity) ((ServerLevel) level).getEntity(ownerUUID) : null;
    }

    @Override
    public CraftWolf getBukkitEntity() {
        return (CraftWolf) super.getBukkitEntity();
    }

    private static class WolfAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Wolf wolf;

        public WolfAvoidEntityGoal(Wolf wolf, Class<T> avoidClass, float maxDist, double walkSpeedModifier, double sprintSpeedModifier) {
            super(wolf, avoidClass, maxDist, walkSpeedModifier, sprintSpeedModifier);
            this.wolf = wolf;
        }

        public boolean canUse() {
            return super.canUse() && toAvoid instanceof Llama && !wolf.isTame() && avoidLlama((Llama) toAvoid);
        }

        private boolean avoidLlama(Llama llama) {
            return llama.getStrength() >= wolf.getRandom().nextInt(5);
        }

        public void start() {
            wolf.setTarget(null);
            super.start();
        }

        public void tick() {
            wolf.setTarget(null);
            super.tick();
        }
    }

    private static class WolfPanicGoal extends PanicGoal {

        public WolfPanicGoal(Wolf wolf, double speedModifier) {
            super(wolf, speedModifier);
        }

        protected boolean shouldPanic() {
            return mob.isFreezing() || mob.isOnFire();
        }
    }

    private static class BegGoal extends Goal {

        private final Wolf wolf;
        private @Nullable LivingEntity living;
        private final Level level;
        private final float lookDistance;
        private int lookTime;
        private final TargetingConditions begTargeting;

        public BegGoal(Wolf wolf, float lookDistance) {
            this.wolf = wolf;
            this.level = wolf.level;
            this.lookDistance = lookDistance;
            this.begTargeting = TargetingConditions.forNonCombat().range(lookDistance);
            setFlags(EnumSet.of(Flag.LOOK));
        }

        public boolean canUse() {
            LivingEntity closestVillager = getNearestVillager();
            LivingEntity closestPlayer = level.getNearestPlayer(begTargeting, wolf);

            if (closestVillager != null && closestPlayer != null) {
                living = wolf.distanceTo(closestVillager) > wolf.distanceTo(closestPlayer) ? closestPlayer : closestVillager;
            } else if (closestVillager != null) {
                living = closestVillager;
            } else if (closestPlayer != null) {
                living = closestPlayer;
            }

            return living != null && holdingInteresting(living);
        }

        private LivingEntity getNearestVillager() {
            return level.getNearestEntity(
                    VillagerNPC.class,
                    begTargeting, wolf,
                    wolf.getX(),
                    wolf.getY(),
                    wolf.getZ(),
                    wolf.getBoundingBox().inflate(lookDistance, lookDistance, lookDistance));
        }

        public boolean canContinueToUse() {
            if (living == null || !living.isAlive()) return false;
            if (wolf.distanceToSqr(living) > lookDistance * lookDistance) return false;

            return lookTime > 0 && holdingInteresting(living);
        }

        public void start() {
            wolf.setIsInterested(true);
            lookTime = adjustedTickDelay(40 + wolf.getRandom().nextInt(40));
        }

        public void stop() {
            wolf.setIsInterested(false);
            living = null;
        }

        public void tick() {
            if (living != null) wolf.getLookControl().setLookAt(
                    living.getX(),
                    living.getEyeY(),
                    living.getZ(),
                    10.0f,
                    (float) wolf.getMaxHeadXRot());
            --lookTime;
        }

        private boolean holdingInteresting(LivingEntity living) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack item = living.getItemInHand(hand);
                if (wolf.isTame() && item.is(Items.BONE)) return true;
                if (wolf.isFood(item)) return true;
            }
            return false;
        }
    }
}