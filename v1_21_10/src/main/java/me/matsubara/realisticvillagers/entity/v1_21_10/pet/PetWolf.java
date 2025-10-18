package me.matsubara.realisticvillagers.entity.v1_21_10.pet;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Pet;
import me.matsubara.realisticvillagers.entity.v1_21_10.villager.VillagerNPC;
import me.matsubara.realisticvillagers.nms.v1_21_10.NMSConverter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_21_R6.entity.CraftWolf;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class PetWolf extends Wolf implements Pet {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    @Getter
    private @Setter boolean tamedByVillager;

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
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0d, 10.0f, 2.0f));
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
    public void tameByVillager(@NotNull IVillagerNPC npc) {
        setTame(true, true);
        setOwner(((CraftLivingEntity) npc.bukkit()).getHandle());
        setTamedByVillager(true);
        getNavigation().stop();
        setTarget(null);
        setPersistenceRequired();
        this.persist = true;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        NMSConverter.updateTamedData(plugin, this, tamedByVillager);
    }

    @Override
    public void load(ValueInput input) {
        super.load(input);

        // We use load() instead of readAdditionalSaveData() because CraftEntity#readBukkitValues is called AFTER readAdditionalSaveData(),
        // so our data won't be present at that time.

        tamedByVillager = getBukkitEntity().getPersistentDataContainer().getOrDefault(plugin.getTamedByVillagerKey(), PersistentDataType.BOOLEAN, false);
    }

    @Override
    public UUID getOwnerUniqueId() {
        EntityReference<LivingEntity> reference = getOwnerReference();
        if (reference != null) return reference.getUUID();
        return null;
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        if (!tamedByVillager) return super.getOwner();

        UUID ownerUUID = getOwnerUniqueId();
        return ownerUUID != null ? (LivingEntity) level().getEntity(ownerUUID) : null;
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

        @Override
        public boolean canUse() {
            return super.canUse() && toAvoid instanceof Llama && !wolf.isTame() && avoidLlama((Llama) toAvoid);
        }

        private boolean avoidLlama(@NotNull Llama llama) {
            return llama.getStrength() >= wolf.getRandom().nextInt(5);
        }

        @Override
        public void start() {
            wolf.setTarget(null);
            super.start();
        }

        @Override
        public void tick() {
            wolf.setTarget(null);
            super.tick();
        }
    }

    private static class WolfPanicGoal extends PanicGoal {

        public WolfPanicGoal(Wolf wolf, double speedModifier) {
            super(wolf, speedModifier);
        }

        @Override
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
        private int tryVillagerAgain;

        public BegGoal(@NotNull Wolf wolf, float lookDistance) {
            this.wolf = wolf;
            this.level = wolf.level();
            this.lookDistance = lookDistance;
            this.begTargeting = TargetingConditions.forNonCombat().range(lookDistance);
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity closestPlayer = level instanceof ServerLevel temp ? temp.getNearestPlayer(begTargeting, wolf) : null;
            if (closestPlayer != null) {
                living = closestPlayer;
            } else if (--tryVillagerAgain <= 0) {
                living = getNearestVillager();

                // Try again in 5 seconds.
                tryVillagerAgain = 100;
            }

            return living != null && holdingInteresting(living);
        }

        private LivingEntity getNearestVillager() {
            return level instanceof ServerLevel temp ? temp.getNearestEntity(
                    VillagerNPC.class,
                    begTargeting,
                    wolf,
                    wolf.getX(),
                    wolf.getY(),
                    wolf.getZ(),
                    wolf.getBoundingBox().inflate(lookDistance, lookDistance, lookDistance)) : null;
        }

        @Override
        public boolean canContinueToUse() {
            if (living == null || !living.isAlive()) return false;
            if (wolf.distanceToSqr(living) > lookDistance * lookDistance) return false;

            return lookTime > 0 && holdingInteresting(living);
        }

        @Override
        public void start() {
            wolf.setIsInterested(true);
            lookTime = adjustedTickDelay(40 + wolf.getRandom().nextInt(40));
        }

        @Override
        public void stop() {
            wolf.setIsInterested(false);
            living = null;
        }

        @Override
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