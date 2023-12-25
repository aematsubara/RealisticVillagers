package me.matsubara.realisticvillagers.entity.v1_18_r2.pet;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Pet;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.nms.v1_18_r2.NMSConverter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftParrot;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.UUID;

public class PetParrot extends Parrot implements Pet {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    @Getter
    private @Setter boolean tamedByVillager;

    public PetParrot(EntityType<? extends Parrot> type, Level level) {
        super(type, level);
    }

    @Override
    public void tameByVillager(IVillagerNPC npc) {
        if (!isSilent()) {
            level.playSound(
                    null,
                    getX(),
                    getY(),
                    getZ(),
                    SoundEvents.PARROT_EAT,
                    getSoundSource(),
                    1.0f,
                    1.0f + (random.nextFloat() - random.nextFloat()) * 0.2f);
        }

        setTame(true);
        setOwnerUUID(npc.bukkit().getUniqueId());
        setTamedByVillager(true);
        setPersistenceRequired();
    }

    @Override
    public UUID getOwnerUniqueId() {
        return super.getOwnerUUID();
    }

    public boolean setEntityOnShoulder(@NotNull VillagerNPC npc) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", getEncodeId());
        saveWithoutId(tag);

        if (!npc.setEntityOnShoulder(tag)) return false;

        discard();
        return true;
    }

    @Override
    protected void doPush(Entity entity) {
        if (!(entity instanceof VillagerNPC)) super.doPush(entity);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new PanicGoal(this, 1.25d));
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, VillagerNPC.class, 8.0f));
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.0d, 5.0f, 1.0f, true));
        goalSelector.addGoal(2, new ParrotWanderGoal(this, 1.0d));
        goalSelector.addGoal(3, new LandOnOwnersShoulderGoal(this));
        goalSelector.addGoal(3, new FollowMobGoal(this, 1.0d, 3.0f, 7.0f));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        NMSConverter.updateTamedData(plugin, tag, this, tamedByVillager);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tamedByVillager = NMSConverter.getOrCreateBukkitTag(tag).getBoolean(plugin.getTamedByVillagerKey().toString());
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        if (!tamedByVillager) return super.getOwner();

        UUID ownerUUID = getOwnerUUID();
        return ownerUUID != null ? (LivingEntity) ((ServerLevel) level).getEntity(ownerUUID) : null;
    }

    @Override
    public CraftParrot getBukkitEntity() {
        return (CraftParrot) super.getBukkitEntity();
    }

    private static class ParrotWanderGoal extends WaterAvoidingRandomFlyingGoal {

        public ParrotWanderGoal(PathfinderMob mob, double speedModifier) {
            super(mob, speedModifier);
        }

        @Override
        protected @Nullable Vec3 getPosition() {
            Vec3 vec3d = null;
            if (mob.isInWater()) {
                vec3d = LandRandomPos.getPos(mob, 15, 15);
            }

            if (mob.getRandom().nextFloat() >= probability) {
                vec3d = getTreePos();
            }

            return vec3d == null ? super.getPosition() : vec3d;
        }

        private @Nullable Vec3 getTreePos() {
            BlockPos pos = mob.blockPosition();

            BlockPos.MutableBlockPos upBlock = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos treeBlock = new BlockPos.MutableBlockPos();

            Iterable<BlockPos> iterable = BlockPos.betweenClosed(
                    Mth.floor(mob.getX() - 3.0d),
                    Mth.floor(mob.getY() - 6.0d),
                    Mth.floor(mob.getZ() - 3.0d),
                    Mth.floor(mob.getX() + 3.0d),
                    Mth.floor(mob.getY() + 6.0d),
                    Mth.floor(mob.getZ() + 3.0d));

            Iterator<BlockPos> iterator = iterable.iterator();

            BlockPos treePos;
            boolean isTree;
            do {
                do {
                    if (!iterator.hasNext()) {
                        return null;
                    }

                    treePos = iterator.next();
                } while (pos.equals(treePos));

                BlockState data = mob.level.getBlockState(treeBlock.setWithOffset(treePos, Direction.DOWN));
                isTree = data.getBlock() instanceof LeavesBlock || data.is(BlockTags.LOGS);
            } while (!isTree
                    || !mob.level.isEmptyBlock(treePos)
                    || !mob.level.isEmptyBlock(upBlock.setWithOffset(treePos, Direction.UP)));

            return Vec3.atBottomCenterOf(treePos);
        }
    }

    public static class LandOnOwnersShoulderGoal extends Goal {

        private final PetParrot parrot;
        private LivingEntity owner;
        private boolean isSittingOnShoulder;

        public LandOnOwnersShoulderGoal(PetParrot parrot) {
            this.parrot = parrot;
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = parrot.getOwner();
            if (owner == null) return false;

            boolean firstCondition;
            if (owner instanceof ServerPlayer player) {
                firstCondition = !player.isSpectator()
                        && !player.getAbilities().flying
                        && !player.isInWater()
                        && !player.isInPowderSnow;
            } else {
                firstCondition = !(owner instanceof VillagerNPC npc) || !npc.isFighting();
            }

            return firstCondition && !parrot.isOrderedToSit() && parrot.canSitOnShoulder();
        }

        @Override
        public boolean isInterruptable() {
            return !isSittingOnShoulder;
        }

        @Override
        public void start() {
            owner = parrot.getOwner();
            isSittingOnShoulder = false;
        }

        @Override
        public void tick() {
            if (isSittingOnShoulder || parrot.isInSittingPose() || parrot.isLeashed()) return;
            if (!parrot.getBoundingBox().intersects(owner.getBoundingBox())) return;

            isSittingOnShoulder = false;
            if (owner instanceof ServerPlayer player) {
                isSittingOnShoulder = parrot.setEntityOnShoulder(player);
            } else if (owner instanceof VillagerNPC npc) {
                isSittingOnShoulder = parrot.setEntityOnShoulder(npc);
            }
        }
    }
}