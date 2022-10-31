package me.matsubara.realisticvillagers.entity.v1_19_r1;

import me.matsubara.realisticvillagers.entity.v1_19_r1.villager.VillagerNPC;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.item.crafting.Ingredient;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.v1_19_R1.event.CraftEventFactory;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

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
    private boolean isRunning;

    private static final double DISTANCE = 10.0d;
    private static final TargetingConditions TEMP_TARGETING = TargetingConditions.forNonCombat().range(10.0).ignoreLineOfSight();

    public CatTemptGoal(Cat cat, double speedModifier, Ingredient items, boolean canScare) {
        this.cat = cat;
        this.speedModifier = speedModifier;
        this.items = items;
        this.canScare = canScare;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        this.targetingConditions = TEMP_TARGETING.copy().selector(this::shouldFollow);
    }

    public boolean canUse() {
        if (calmDown > 0) {
            --calmDown;
            return false;
        }

        LivingEntity closestVillager = getNearestVillager();
        LivingEntity closestPlayer = cat.level.getNearestPlayer(targetingConditions, cat);

        if (closestVillager != null && closestPlayer != null) {
            b = cat.distanceTo(closestVillager) > cat.distanceTo(closestPlayer) ? closestPlayer : closestVillager;
        } else if (closestVillager != null) {
            b = closestVillager;
        } else if (closestPlayer != null) {
            b = closestPlayer;
        }

        if (b != null) {
            EntityTargetLivingEntityEvent event = CraftEventFactory.callEntityTargetLivingEvent(
                    cat,
                    b,
                    EntityTargetEvent.TargetReason.TEMPT);
            if (event.isCancelled()) return false;

            b = event.getTarget() == null ? null : ((CraftLivingEntity) event.getTarget()).getHandle();
        }

        return b != null && !cat.isTame();
    }

    private LivingEntity getNearestVillager() {
        return cat.level.getNearestEntity(
                VillagerNPC.class,
                targetingConditions, cat,
                cat.getX(),
                cat.getY(),
                cat.getZ(),
                cat.getBoundingBox().inflate(DISTANCE, DISTANCE, DISTANCE));
    }

    private boolean shouldFollow(LivingEntity living) {
        return items.test(living.getMainHandItem()) || items.test(living.getOffhandItem());
    }

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

    public void start() {
        if (b == null) return;
        px = b.getX();
        py = b.getY();
        pz = b.getZ();
        isRunning = true;
    }

    public void stop() {
        b = null;
        cat.getNavigation().stop();
        calmDown = reducedTickDelay(100);
        isRunning = false;
    }

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

    public boolean isRunning() {
        return isRunning;
    }
}