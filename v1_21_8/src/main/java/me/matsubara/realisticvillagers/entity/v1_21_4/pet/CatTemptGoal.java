package me.matsubara.realisticvillagers.entity.v1_21_4.pet;

import lombok.Getter;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.crafting.Ingredient;
import org.bukkit.craftbukkit.v1_21_R5.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_21_R5.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class CatTemptGoal extends Goal {

    private final Cat cat;
    private final double speedModifier;
    private final Ingredient items;
    private final boolean canScare;
    private final TargetingConditions targetingConditions;

    private double px;
    private double py;
    private double pz;
    private double pRotX;
    private double pRotY;

    private @Nullable LivingEntity b;
    private @Nullable LivingEntity c;
    private int calmDown;
    @Getter
    private boolean isRunning;
    private int tryVillagerAgain;

    private static final double DISTANCE = 10.0d;
    private static final TargetingConditions TEMPT_TARGETING = TargetingConditions.forNonCombat().range(10.0).ignoreLineOfSight();

    public CatTemptGoal(Cat cat, double speedModifier, Ingredient items, boolean canScare) {
        this.cat = cat;
        this.speedModifier = speedModifier;
        this.items = items;
        this.canScare = canScare;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.targetingConditions = TEMPT_TARGETING.copy().selector((living, level) -> shouldFollow(living));
    }

    @Override
    public boolean canUse() {
        if (calmDown > 0) {
            --calmDown;
            return false;
        }

        LivingEntity closestPlayer = cat.level() instanceof ServerLevel level ? level.getNearestPlayer(targetingConditions, cat) : null;
        if (closestPlayer != null) {
            b = closestPlayer;
        } else if (--tryVillagerAgain <= 0 || b instanceof VillagerNPC) {
            b = getNearestVillager();

            // Try again in 5 seconds.
            tryVillagerAgain = 100;
        }

        if (b == null) return false;

        EntityTargetLivingEntityEvent event = CraftEventFactory.callEntityTargetLivingEvent(
                cat,
                b,
                EntityTargetEvent.TargetReason.TEMPT);
        if (event.isCancelled()) return false;

        b = event.getTarget() == null ? null : ((CraftLivingEntity) event.getTarget()).getHandle();
        return b != null && !cat.isTame();
    }

    private @Nullable LivingEntity getNearestVillager() {
        return cat.level() instanceof ServerLevel temp ? temp.getNearestEntity(
                VillagerNPC.class,
                targetingConditions,
                cat,
                cat.getX(),
                cat.getY(),
                cat.getZ(),
                cat.getBoundingBox().inflate(DISTANCE, DISTANCE, DISTANCE)) : null;
    }

    private boolean shouldFollow(@NotNull LivingEntity living) {
        return items.test(living.getMainHandItem()) || items.test(living.getOffhandItem());
    }

    @Override
    public boolean canContinueToUse() {
        if (!canScare()) return canUse();
        if (b == null) return false;

        if (cat.distanceToSqr(b) < 36.0d) {
            if (b.distanceToSqr(px, py, pz) > 0.010000000000000002d) {
                return false;
            }

            if (Math.abs((double) b.getXRot() - pRotX) > 5.0d || Math.abs((double) b.getYRot() - pRotY) > 5.0d) {
                return false;
            }
        } else {
            px = b.getX();
            py = b.getY();
            pz = b.getZ();
        }

        pRotX = b.getXRot();
        pRotY = b.getYRot();

        return canUse();
    }

    protected boolean canScare() {
        return (c == null || !c.equals(b)) && canScare;
    }

    @Override
    public void start() {
        if (b == null) return;
        px = b.getX();
        py = b.getY();
        pz = b.getZ();
        isRunning = true;
    }

    @Override
    public void stop() {
        b = null;
        cat.getNavigation().stop();
        calmDown = reducedTickDelay(100);
        isRunning = false;
    }

    @Override
    public void tick() {
        cat.getLookControl().setLookAt(b, (float) (cat.getMaxHeadYRot() + 20), (float) cat.getMaxHeadXRot());
        if (cat.distanceToSqr(b) < 6.25d) {
            cat.getNavigation().stop();
        } else {
            cat.getNavigation().moveTo(b, speedModifier);
        }

        if (c == null && cat.getRandom().nextInt(adjustedTickDelay(600)) == 0) {
            c = b;
        } else if (cat.getRandom().nextInt(adjustedTickDelay(500)) == 0) {
            c = null;
        }
    }
}