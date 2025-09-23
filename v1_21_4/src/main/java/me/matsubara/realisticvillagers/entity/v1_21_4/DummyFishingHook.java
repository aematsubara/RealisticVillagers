package me.matsubara.realisticvillagers.entity.v1_21_4;

import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerFishEvent;
import me.matsubara.realisticvillagers.files.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.entity.FishHook;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Iterator;

@SuppressWarnings("WhileLoopReplaceableByForEach")
public class DummyFishingHook extends FishingHook {

    private final int luck;
    private final int lureSpeed;
    private final RandomSource syncronizedRandom;

    private int life;
    private int nibble;
    private int timeUntilLured;
    private int timeUntilHooked;
    private int outOfWaterTime;
    private boolean openWater;
    private float fishAngle;
    private boolean biting;
    private boolean hasBeenShot;
    private boolean leftOwner;

    private static final EntityDataAccessor<Boolean> BITING;

    static {
        BITING = SynchedEntityData.defineId(DummyFishingHook.class, EntityDataSerializers.BOOLEAN);
    }

    public DummyFishingHook(VillagerNPC npc, Level level, int luck, int lureSpeed) {
        super(EntityType.FISHING_BOBBER, level);
        this.luck = luck;
        this.lureSpeed = lureSpeed;
        this.syncronizedRandom = RandomSource.create();

        setOwner(npc);

        float xRot = npc.getXRot();
        float yRot = npc.getYRot();

        float dX = Mth.sin(-yRot * 0.017453292f - 3.1415927f);
        float dZ = Mth.cos(-yRot * 0.017453292f - 3.1415927f);

        float dcY = -Mth.cos(-xRot * 0.017453292f);
        float dsY = Mth.sin(-xRot * 0.017453292f);

        double x = npc.getX() - (double) dX * 0.5d;
        double y = npc.getEyeY();
        double z = npc.getZ() - (double) dZ * 0.5d;
        setPos(x, y, z);
        setYRot(yRot);
        setXRot(xRot);

        Vec3 delta = new Vec3(-dX, Mth.clamp(-(dsY / dcY), -5.0f, 5.0f), -dZ);
        setDeltaMovement(delta.multiply(
                0.6d / delta.length() + random.triangle(0.5d, 0.0103365d),
                0.6d / delta.length() + random.triangle(0.5d, 0.0103365d),
                0.6d / delta.length() + random.triangle(0.5d, 0.0103365d)));


        setYRot((float) (Mth.atan2(getDeltaMovement().x(), getDeltaMovement().z()) * 57.2957763671875f));
        setXRot((float) (Mth.atan2(getDeltaMovement().y(), getDeltaMovement().horizontalDistance()) * 57.2957763671875f));

        yRotO = getYRot();
        xRotO = getXRot();
    }

    @Override
    public void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(BITING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);

        if (!BITING.equals(accessor)) return;

        biting = getEntityData().get(BITING);
        if (biting) {
            Vec3 delta = getDeltaMovement();
            setDeltaMovement(delta.x, -0.4f * Mth.nextFloat(syncronizedRandom, 0.6f, 1.0f), delta.z);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        if (leftOwner) output.putBoolean("LeftOwner", true);
        output.putBoolean("HasBeenShot", hasBeenShot);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        leftOwner = input.getBooleanOr("LeftOwner", false);
        hasBeenShot = input.getBooleanOr("HasBeenShot", false);
    }

    private boolean checkLeftOwner() {
        Entity owner = getOwner();
        if (owner == null) return true;

        Iterator<Entity> iterator = level().getEntities(
                this,
                getBoundingBox().expandTowards(getDeltaMovement()).inflate(1.0d),
                (entity) -> !entity.isSpectator() && entity.isPickable()).iterator();

        while (iterator.hasNext()) {
            Entity entity = iterator.next();
            if (entity.getRootVehicle() == owner.getRootVehicle()) return false;
        }

        return true;
    }

    @Override
    protected boolean canHitEntity(@NotNull Entity entity) {
        boolean item = entity.isAlive() && entity instanceof ItemEntity;
        if (entity.isSpectator() || !entity.isAlive() || !entity.isPickable()) return item;

        Entity owner = getOwner();
        return owner == null || leftOwner || !owner.isPassengerOfSameVehicle(entity) || item;
    }

    private void superTick() {
        if (!hasBeenShot) {
            gameEvent(GameEvent.PROJECTILE_SHOOT, getOwner());
            hasBeenShot = true;
        }

        if (!leftOwner) leftOwner = checkLeftOwner();

        baseTick();
    }

    @Override
    public void tick() {
        syncronizedRandom.setSeed(getUUID().getLeastSignificantBits() ^ level().getGameTime());
        superTick();

        VillagerNPC npc = getNPCOwner();
        if (npc == null) {
            discard();
            return;
        }

        if (!level().isClientSide && shouldStopFishing(npc)) return;

        if (onGround) {
            ++life;

            // Retrieve if on the ground for more than 5 seconds.
            if (life >= 100) {
                retrieveAndAddCooldown();
                return;
            }
        } else {
            life = 0;
        }

        BlockPos pos = blockPosition();
        FluidState fluid = level().getFluidState(pos);

        float height = fluid.is(FluidTags.WATER) ? fluid.getHeight(level(), pos) : 0.0f;

        if (currentState == FishHookState.FLYING) {
            if (hookedIn != null) {
                setDeltaMovement(Vec3.ZERO);
                currentState = FishHookState.HOOKED_IN_ENTITY;
                return;
            }

            if (height > 0.0f) {
                setDeltaMovement(getDeltaMovement().multiply(0.3d, 0.2d, 0.3d));
                currentState = FishHookState.BOBBING;
                return;
            }

            checkCollision();
        } else if (currentState == FishHookState.HOOKED_IN_ENTITY) {
            if (hookedIn == null) return;

            if (!hookedIn.isRemoved() && hookedIn.level().dimension() == level().dimension()) {
                setPos(hookedIn.getX(), hookedIn.getY(0.8d), hookedIn.getZ());
                // Retrieve hooked entity.
                retrieveAndAddCooldown();
            } else {
                setHookedEntity(null);
                currentState = FishHookState.FLYING;
            }
            return;
        } else if (currentState == FishHookState.BOBBING) {
            Vec3 delta = getDeltaMovement();

            double dY = getY() + delta.y() - (double) pos.getY() - (double) height;
            boolean flag = Math.abs(dY) < 0.01d;
            setDeltaMovement(
                    delta.x() * 0.9d,
                    delta.y() - (dY + (flag ? Math.signum(dY) * 0.1d : 0.0d)) * random.nextFloat() * 0.2d,
                    delta.z() * 0.9d);

            openWater = nibble <= 0 && timeUntilHooked <= 0 || openWater && outOfWaterTime < 10 && calculateOpenWater(pos);

            if (height > 0.0f) {
                outOfWaterTime = Math.max(0, outOfWaterTime - 1);
                if (biting) {
                    setDeltaMovement(getDeltaMovement().add(
                            0.0d,
                            -0.1d * syncronizedRandom.nextFloat() * syncronizedRandom.nextFloat(),
                            0.0d));
                }

                if (!level().isClientSide) catchingFish(pos);
            } else {
                outOfWaterTime = Math.min(10, outOfWaterTime + 1);
            }
        }

        if (!fluid.is(FluidTags.WATER)) {
            setDeltaMovement(getDeltaMovement().add(0.0d, -0.03d, 0.0d));
        }

        move(MoverType.SELF, getDeltaMovement());
        updateRotation();

        if (currentState == FishHookState.FLYING && (onGround || horizontalCollision)) {
            setDeltaMovement(Vec3.ZERO);
        }

        setDeltaMovement(getDeltaMovement().scale(0.92d));
        reapplyPosition();
    }

    private @NotNull VillagerFishEvent callEvent(@Nullable Entity entity, VillagerFishEvent.State state) {
        VillagerFishEvent fishEvent = new VillagerFishEvent(
                getNPCOwner(),
                entity != null ? entity.getBukkitEntity() : null,
                (FishHook) getBukkitEntity(),
                state);
        Bukkit.getServer().getPluginManager().callEvent(fishEvent);

        return fishEvent;
    }

    private void catchingFish(@NotNull BlockPos pos) {
        ServerLevel level = (ServerLevel) this.level();

        int i = 1;
        BlockPos above = pos.above();

        if (random.nextFloat() < 0.25f && level().isRainingAt(above)) ++i;
        if (random.nextFloat() < 0.5f && !level().canSeeSky(above)) --i;

        if (nibble > 0) {
            if (--nibble <= 0) {
                timeUntilLured = 0;
                timeUntilHooked = 0;
                getEntityData().set(BITING, false);
                callEvent(null, VillagerFishEvent.State.FAILED_ATTEMPT);
            }
            return;
        }

        float xOffset, zOffset;
        double x, y, z;
        BlockState data;

        if (timeUntilHooked <= 0 && timeUntilLured <= 0) {
            timeUntilLured = Mth.nextInt(random, minWaitTime, maxWaitTime);
            timeUntilLured -= applyLure ? lureSpeed * 20 * 5 : 0;
            return;
        }

        if (timeUntilHooked > 0) {
            timeUntilHooked -= i;
            if (timeUntilHooked > 0) {
                fishAngle += (float) random.triangle(0.0d, 9.188d);
                float angle = fishAngle * 0.017453292f;

                xOffset = Mth.sin(angle);
                zOffset = Mth.cos(angle);

                x = getX() + (double) (xOffset * (float) timeUntilHooked * 0.1f);
                y = (float) Mth.floor(getY()) + 1.0f;
                z = getZ() + (double) (zOffset * (float) timeUntilHooked * 0.1f);

                data = level().getBlockState(BlockPos.containing(x, y - 1.0d, z));
                if (!data.is(Blocks.WATER)) return;

                if (random.nextFloat() < 0.15f) {
                    level.sendParticles(ParticleTypes.BUBBLE, x, y - 0.10000000149011612d, z, 1, xOffset, 0.1d, zOffset, 0.0d);
                }

                float xo = zOffset * 0.04f;
                float zo = xOffset * 0.04f;
                level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, xo, 0.01d, -zo, 1.0d);
                level.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -xo, 0.01d, zo, 1.0d);
            } else {
                VillagerFishEvent fishEvent = callEvent(null, VillagerFishEvent.State.BITE);
                if (fishEvent.isCancelled()) return;

                playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.25f, 1.0f + (random.nextFloat() - random.nextFloat()) * 0.4f);

                sendBiteParticles(ParticleTypes.BUBBLE);
                sendBiteParticles(ParticleTypes.FISHING);
                nibble = Mth.nextInt(random, 20, 40);
                getEntityData().set(BITING, true);

                // Retrieve caught fish/item.
                retrieveAndAddCooldown();
            }
            return;
        }

        timeUntilLured -= i;
        float angle = 0.15f;
        if (timeUntilLured < 20) {
            angle += (float) (20 - timeUntilLured) * 0.05f;
        } else if (timeUntilLured < 40) {
            angle += (float) (40 - timeUntilLured) * 0.02f;
        } else if (timeUntilLured < 60) {
            angle += (float) (60 - timeUntilLured) * 0.01f;
        }

        if (random.nextFloat() < angle) {
            xOffset = Mth.nextFloat(random, 0.0f, 360.0f) * 0.017453292f;
            zOffset = Mth.nextFloat(random, 25.0f, 60.0f);
            x = getX() + (double) (Mth.sin(xOffset) * zOffset) * 0.1d;
            y = (float) Mth.floor(getY()) + 1.0f;
            z = getZ() + (double) (Mth.cos(xOffset) * zOffset) * 0.1d;
            data = level.getBlockState(BlockPos.containing(x, y - 1.0d, z));
            if (data.is(Blocks.WATER)) {
                level.sendParticles(
                        ParticleTypes.SPLASH,
                        x,
                        y,
                        z,
                        2 + random.nextInt(2),
                        0.10000000149011612d,
                        0.0d,
                        0.10000000149011612d,
                        0.0d);
            }
        }

        if (timeUntilLured <= 0) {
            fishAngle = Mth.nextFloat(random, 0.0f, 360.0f);
            timeUntilHooked = Mth.nextInt(random, 20, 80);
        }
    }

    private void sendBiteParticles(SimpleParticleType type) {
        ((ServerLevel) level()).sendParticles(
                type,
                getX(),
                getY() + 0.5d,
                getZ(),
                (int) (1.0f + getBbWidth() * 20.0f),
                getBbWidth(),
                0.0d,
                getBbWidth(),
                0.20000000298023224d);
    }

    private boolean shouldStopFishing(@NotNull VillagerNPC npc) {
        ItemStack mainHand = npc.getMainHandItem();
        ItemStack offHand = npc.getOffhandItem();

        boolean isMainRod = mainHand.is(Items.FISHING_ROD);
        boolean isOffRod = offHand.is(Items.FISHING_ROD);

        if (!npc.isRemoved() && npc.isAlive() && (isMainRod || isOffRod) && distanceToSqr(npc) <= 1024.0d) {
            return false;
        }

        discard();
        return true;
    }

    public @Nullable VillagerNPC getNPCOwner() {
        return getOwner() instanceof VillagerNPC npc ? npc : null;
    }

    private void checkCollision() {
        HitResult result = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        preHitTargetOrDeflectSelf(result);
    }

    private boolean calculateOpenWater(BlockPos pos) {
        OpenWaterType type = OpenWaterType.INVALID;

        for (int i = -1; i <= 2; ++i) {
            OpenWaterType areaType = getOpenWaterTypeForArea(pos.offset(-2, i, -2), pos.offset(2, i, 2));
            switch (areaType.ordinal()) {
                case 1 -> {
                    if (type == OpenWaterType.INVALID) return false;
                }
                case 2 -> {
                    if (type == OpenWaterType.ABOVE_WATER) return false;
                }
                case 3 -> {
                    return false;
                }
            }
            type = areaType;
        }

        return true;
    }

    private OpenWaterType getOpenWaterTypeForArea(BlockPos firstPos, BlockPos secondPos) {
        return BlockPos.betweenClosedStream(firstPos, secondPos)
                .map(this::getOpenWaterTypeForBlock)
                .reduce((first, second) -> first == second ? first : OpenWaterType.INVALID)
                .orElse(OpenWaterType.INVALID);
    }

    private OpenWaterType getOpenWaterTypeForBlock(BlockPos pos) {
        BlockState state = level().getBlockState(pos);
        if (state.isAir() || state.is(Blocks.LILY_PAD)) return OpenWaterType.ABOVE_WATER;

        FluidState fluid = state.getFluidState();
        return fluid.is(FluidTags.WATER)
                && fluid.isSource()
                && state.getCollisionShape(level(), pos).isEmpty() ? OpenWaterType.INSIDE_WATER : OpenWaterType.INVALID;
    }

    @Override
    public boolean isOpenWaterFishing() {
        return openWater;
    }

    @Override
    public int retrieve(ItemStack item) {
        VillagerNPC npc = getNPCOwner();

        if (level().isClientSide || npc == null || shouldStopFishing(npc)) return 0;

        int i = 0;
        VillagerFishEvent fishEvent;
        if (hookedIn != null) {
            fishEvent = callEvent(hookedIn, VillagerFishEvent.State.CAUGHT_ENTITY);
            if (fishEvent.isCancelled()) return 0;

            pullEntity(hookedIn);
            level().broadcastEntityEvent(this, (byte) 31);
            i = hookedIn instanceof ItemEntity ? 3 : 5;
        } else if (nibble > 0 && level().getServer() != null) {
            MinecraftServer server = level().getServer();
            if (server == null) return 0;

            LootTable table = server.reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
            LootParams params = new LootParams.Builder((ServerLevel) level())
                    .withParameter(LootContextParams.ORIGIN, position())
                    .withParameter(LootContextParams.TOOL, item)
                    .withParameter(LootContextParams.THIS_ENTITY, this)
                    .withLuck((float) luck)
                    .create(LootContextParamSets.FISHING);

            Iterator<ItemStack> iterator = table.getRandomItems(params).iterator();

            while (iterator.hasNext()) {
                ItemStack loot = iterator.next();
                ItemEntity itemEntity = new ItemEntity(level(), getX(), getY(), getZ(), loot);

                fishEvent = callEvent(itemEntity, VillagerFishEvent.State.CAUGHT_FISH);
                if (fishEvent.isCancelled()) return 0;

                double dX = npc.getX() - getX();
                double dY = npc.getY() - getY();
                double dZ = npc.getZ() - getZ();
                itemEntity.setDeltaMovement(
                        dX * 0.1d,
                        dY * 0.1d + Math.sqrt(Math.sqrt(dX * dX + dY * dY + dZ * dZ)) * 0.08d,
                        dZ * 0.1d);

                level().addFreshEntity(itemEntity);
            }

            i = 1;
        }

        if (onGround) {
            fishEvent = callEvent(null, VillagerFishEvent.State.IN_GROUND);
            if (fishEvent.isCancelled()) return 0;

            i = 2;
        }

        if (i == 0) {
            fishEvent = callEvent(null, VillagerFishEvent.State.REEL_IN);
            if (fishEvent.isCancelled()) return 0;
        }

        discard();
        return i;
    }

    @Override
    public void remove(RemovalReason reason) {
        updateOwnerInfo(null);
        super.remove(reason);
    }

    @Override
    public void onClientRemoval() {
        updateOwnerInfo(null);
    }

    @Override
    public void setOwner(@Nullable Entity entity) {
        super.setOwner(entity);
        updateOwnerInfo(this);
    }

    private void updateOwnerInfo(@Nullable DummyFishingHook hook) {
        VillagerNPC npc = getNPCOwner();
        if (npc != null) npc.setFishing(hook);
    }

    private void retrieveAndAddCooldown() {
        VillagerNPC npc = getNPCOwner();
        if (npc != null) {
            retrieve(npc.getMainHandItem());
            npc.getBrain().setMemoryWithExpiry(VillagerNPC.HAS_FISHED_RECENTLY, true, Config.FISHING_COOLDOWN.asLong());
        }
    }

    private enum OpenWaterType {
        ABOVE_WATER,
        INSIDE_WATER,
        INVALID
    }
}