package me.matsubara.realisticvillagers.entity.v1_21_4.villager.ai.behaviour.fight;

import com.google.common.collect.ImmutableMap;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerRiptideEvent;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_21_R5.enchantments.CraftEnchantment;
import org.bukkit.craftbukkit.v1_21_R5.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public class TridentAttack extends Behavior<Villager> {

    private int delay;
    private TridentState state;
    private InteractionHand hand;
    private ItemStack weapon;

    public static final int TRIDENT_DISTANCE_ATTACK = 5;

    public TridentAttack() {
        super(ImmutableMap.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT));
        state = TridentState.UNCHARGED;
    }

    @Override
    public boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        LivingEntity target = getAttackTarget(villager);
        return villager instanceof VillagerNPC npc
                && npc.isHolding(Items.TRIDENT)
                && canThrow(npc)
                && BlockAttackWithShield.notUsingShield(npc)
                && BehaviorUtils.canSee(npc, target)
                && npc.distanceTo(target) > TRIDENT_DISTANCE_ATTACK
                && level.random.nextInt(3) == 0;
    }

    private boolean canThrow(@NotNull VillagerNPC npc) {
        ItemStack item = npc.getItemInHand(ProjectileUtil.getWeaponHoldingHand(npc, Items.TRIDENT));
        return getEnchantmentLevel(item, Enchantment.RIPTIDE) > 0 || getEnchantmentLevel(item, Enchantment.LOYALTY) > 0;
    }

    private int getEnchantmentLevel(ItemStack item, Enchantment enchantment) {
        return EnchantmentHelper.getItemEnchantmentLevel(CraftEnchantment.bukkitToMinecraftHolder(enchantment), item);
    }

    @Override
    public void start(ServerLevel level, Villager villager, long time) {
        if (villager instanceof VillagerNPC npc) npc.setAttackingWithTrident(true);

        hand = ProjectileUtil.getWeaponHoldingHand(villager, Items.TRIDENT);
        weapon = villager.getItemInHand(hand);
    }

    @Override
    public boolean canStillUse(ServerLevel level, @NotNull Villager villager, long time) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    public void tick(ServerLevel level, Villager villager, long time) {
        removeWalkTargetIfNeeded(villager);

        LivingEntity target = getAttackTarget(villager);
        lookAtTarget(villager, target);
        tridentAttack((VillagerNPC) villager);
    }

    private void removeWalkTargetIfNeeded(@NotNull Villager villager) {
        Brain<Villager> brain = villager.getBrain();

        Optional<WalkTarget> walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET);
        if (walkTarget.isEmpty()) return;

        if (!(walkTarget.get().getTarget() instanceof EntityTracker tracker)) return;

        Entity target = tracker.getEntity();
        if (!target.is(getAttackTarget(villager))) return;

        if (villager.distanceTo(target) <= TRIDENT_DISTANCE_ATTACK) {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    @Override
    public void stop(ServerLevel level, @NotNull Villager villager, long time) {
        if (villager.isUsingItem()) villager.stopUsingItem();

        if (villager.isHolding(Items.TRIDENT)) {
            ((VillagerNPC) villager).setChargingCrossbow(false);
        }

        state = TridentState.UNCHARGED;

        if (villager instanceof VillagerNPC npc) npc.setAttackingWithTrident(false);
    }

    private void tridentAttack(VillagerNPC npc) {
        if (state == TridentState.UNCHARGED) {
            npc.startUsingItem(hand);
            state = TridentState.CHARGING;
            npc.setChargingCrossbow(true);
        } else if (state == TridentState.CHARGING) {
            if (!npc.isUsingItem()) state = TridentState.UNCHARGED;

            if (npc.getTicksUsingItem() < 25) return;

            state = TridentState.CHARGED;
            delay = npc.getRandom().nextInt(10);
        } else if (state == TridentState.CHARGED) {
            // Set ready to attack after random delay.
            if (--delay <= 0) state = TridentState.READY_TO_ATTACK;
        } else if (state == TridentState.READY_TO_ATTACK) {
            npc.releaseUsingItem();
            npc.setChargingCrossbow(false);

            throwTrident(npc);

            state = TridentState.UNCHARGED;
        }
    }

    private void throwTrident(VillagerNPC npc) {
        int riptide = getEnchantmentLevel(weapon, Enchantment.RIPTIDE);
        if (riptide == 0) {
            ThrownTrident trident = new ThrownTrident(npc.level(), npc, weapon);
            trident.shootFromRotation(npc, npc.getXRot(), npc.getYRot(), 0.0f, 2.5f + (float) riptide * 0.5f, 1.0f);
            trident.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

            if (!npc.level().addFreshEntity(trident)) return;

            weapon.hurtAndBreak(1, npc, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);

            trident.pickupItemStack = weapon.copy();
            npc.level().playSound(null, trident, SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
            npc.setItemInHand(hand, ItemStack.EMPTY);
            npc.setThrownTrident(trident);
            return;
        }

        if (Config.RIPTIDE_ONLY_IN_WATER_OR_RAIN.asBool() && !npc.isInWaterOrRain()) return;

        Bukkit.getPluginManager().callEvent(new VillagerRiptideEvent(npc, CraftItemStack.asBukkitCopy(weapon)));

        float yRot = npc.getYRot();
        float xRot = npc.getXRot();

        float x = -Mth.sin(yRot * 0.017453292f) * Mth.cos(xRot * 0.017453292f);
        float y = -Mth.sin(xRot * 0.017453292f);
        float z = Mth.cos(yRot * 0.017453292f) * Mth.cos(xRot * 0.017453292f);

        float sqrt = Mth.sqrt(x * x + y * y + z * z);
        float rot = 3.0f * ((1.0f + (float) riptide) / 4.0f);

        npc.push(
                x * rot / sqrt,
                y * rot / sqrt,
                z * rot / sqrt);

        npc.startAutoSpinAttack(20);
        if (npc.onGround()) {
            npc.move(MoverType.SELF, new Vec3(0.0d, 1.1999999284744263d, 0.0d));
        }

        Holder<SoundEvent> sound;
        if (riptide >= 3) {
            sound = SoundEvents.TRIDENT_RIPTIDE_3;
        } else if (riptide == 2) {
            sound = SoundEvents.TRIDENT_RIPTIDE_2;
        } else {
            sound = SoundEvents.TRIDENT_RIPTIDE_1;
        }
        npc.level().playSound(null, npc, sound.value(), SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @Override
    public boolean timedOut(long time) {
        return false;
    }

    private void lookAtTarget(@NotNull Mob mob, LivingEntity target) {
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private @NotNull LivingEntity getAttackTarget(@NotNull Villager villager) {
        // Can't be null since the value should be present in the constructor.
        return villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
    }

    enum TridentState {
        UNCHARGED,
        CHARGING,
        CHARGED,
        READY_TO_ATTACK
    }
}