package me.matsubara.realisticvillagers.entity.v1_21_4.pet;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.Pet;
import me.matsubara.realisticvillagers.entity.v1_21_4.villager.VillagerNPC;
import me.matsubara.realisticvillagers.nms.v1_21_4.NMSConverter;
import me.matsubara.realisticvillagers.util.Reflection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_21_R5.entity.CraftCat;
import org.bukkit.craftbukkit.v1_21_R5.entity.CraftLivingEntity;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.UUID;

public class PetCat extends Cat implements Pet {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    @Getter
    private @Setter boolean tamedByVillager;
    private CatAvoidEntityGoal<Player> avoidPlayersGoal;
    private @Nullable CatTemptGoal temptGoal;

    @SuppressWarnings("unchecked")
    private static final EntityDataAccessor<Boolean> RELAX_STATE_ONE = (EntityDataAccessor<Boolean>) Reflection.getFieldValue(Reflection.getField(Cat.class, EntityDataAccessor.class, "ch", true, "RELAX_STATE_ONE"));
    private static final Ingredient TEMPT_INGREDIENT = Ingredient.of(Items.COD, Items.SALMON);

    public PetCat(EntityType<? extends Cat> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        temptGoal = new CatTemptGoal(this, 0.6d, TEMPT_INGREDIENT, true);
        goalSelector.addGoal(1, new FloatGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(2, new CatRelaxOnOwnerGoal(this));
        goalSelector.addGoal(3, temptGoal);
        goalSelector.addGoal(5, new CatLieOnBedGoal(this, 1.1d, 8));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0d, 10.0f, 5.0f));
        goalSelector.addGoal(7, new CatSitOnBlockGoal(this, 0.8d));
        goalSelector.addGoal(8, new LeapAtTargetGoal(this, 0.3f));
        goalSelector.addGoal(9, new OcelotAttackGoal(this));
        goalSelector.addGoal(10, new BreedGoal(this, 0.8d));
        goalSelector.addGoal(11, new WaterAvoidingRandomStrollGoal(this, 0.8d, 1.0000001E-5f));
        goalSelector.addGoal(12, new LookAtPlayerGoal(this, Player.class, 10.0f));
        goalSelector.addGoal(12, new LookAtPlayerGoal(this, VillagerNPC.class, 10.0f));
        targetSelector.addGoal(1, new NonTameRandomTargetGoal<>(this, Rabbit.class, false, null));
        targetSelector.addGoal(1, new NonTameRandomTargetGoal<>(this, Turtle.class, false, Turtle.BABY_ON_LAND_SELECTOR));
    }

    @Override
    public void tameByVillager(@NotNull IVillagerNPC npc) {
        playSound(SoundEvents.CAT_EAT, 1.0f, 1.0f);

        setTame(true, true);
        setOwner(((CraftLivingEntity) npc.bukkit()).getHandle());
        setTamedByVillager(true);
        setPersistenceRequired();
        this.persist = true;
    }

    @Override
    public UUID getOwnerUniqueId() {
        EntityReference<LivingEntity> reference = getOwnerReference();
        if (reference != null) return reference.getUUID();
        return null;
    }

    @Override
    public void tick() {
        super.tick();

        if (temptGoal != null && temptGoal.isRunning() && !isTame() && tickCount % 100 == 0) {
            playSound(SoundEvents.CAT_BEG_FOR_FOOD, 1.0f, 1.0f);
        }
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
    protected void reassessTameGoals() {
        if (avoidPlayersGoal == null) {
            avoidPlayersGoal = new CatAvoidEntityGoal<>(this, Player.class, 16.0f, 0.8d, 1.33d);
        }

        goalSelector.removeGoal(avoidPlayersGoal);
        if (!isTame()) goalSelector.addGoal(4, avoidPlayersGoal);
    }

    @Override
    public @Nullable LivingEntity getOwner() {
        if (!tamedByVillager) return super.getOwner();

        UUID ownerUUID = getOwnerUniqueId();
        return ownerUUID != null ? (LivingEntity) level().getEntity(ownerUUID) : null;
    }

    @Override
    public CraftCat getBukkitEntity() {
        return (CraftCat) super.getBukkitEntity();
    }

    private void setRelaxStateOne(boolean flag) {
        entityData.set(RELAX_STATE_ONE, flag);
    }

    private boolean isRelaxStateOne() {
        return entityData.get(RELAX_STATE_ONE);
    }

    private static class CatAvoidEntityGoal<T extends LivingEntity> extends AvoidEntityGoal<T> {

        private final Cat cat;

        public CatAvoidEntityGoal(Cat cat, Class<T> avoidClass, float maxDist, double walkSpeedModifier, double sprintSpeedModifier) {
            super(cat, avoidClass, maxDist, walkSpeedModifier, sprintSpeedModifier, EntitySelector.NO_CREATIVE_OR_SPECTATOR::test);
            this.cat = cat;
        }

        @Override
        public boolean canUse() {
            return !cat.isTame() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !cat.isTame() && super.canContinueToUse();
        }
    }

    private static class CatRelaxOnOwnerGoal extends Goal {

        private final PetCat cat;

        private int onBedTicks;
        private @Nullable LivingEntity owner;
        private @Nullable BlockPos goalPos;

        public CatRelaxOnOwnerGoal(PetCat cat) {
            this.cat = cat;
        }

        @Override
        public boolean canUse() {
            if (!cat.isTame()) return false;
            if (cat.isOrderedToSit()) return false;

            owner = cat.getOwner();
            if (owner == null || !owner.isSleeping()) return false;

            if (cat.distanceToSqr(owner) > 100.0d) return false;

            BlockPos position = owner.blockPosition();
            BlockState data = cat.level().getBlockState(position);
            if (data.is(BlockTags.BEDS)) {
                goalPos = data
                        .getOptionalValue(BedBlock.FACING)
                        .map((direction) -> position.relative(direction.getOpposite()))
                        .orElseGet(() -> new BlockPos(position));
                return spaceAvailable();
            }

            return false;
        }

        private boolean spaceAvailable() {
            if (goalPos == null) return false;

            Iterator<PetCat> iterator = cat.level().getEntitiesOfClass(PetCat.class, (new AABB(goalPos)).inflate(2.0d)).iterator();

            PetCat cat;
            do {
                do {
                    if (!iterator.hasNext()) return true;
                    cat = iterator.next();
                } while (cat == this.cat);
            } while (!cat.isLying() && !cat.isRelaxStateOne());

            return false;
        }

        @Override
        public boolean canContinueToUse() {
            return cat.isTame()
                    && !cat.isOrderedToSit()
                    && owner != null
                    && owner.isSleeping()
                    && goalPos != null
                    && spaceAvailable();
        }

        @Override
        public void start() {
            if (goalPos == null) return;
            cat.setInSittingPose(false);
            cat.getNavigation().moveTo(goalPos.getX(), goalPos.getY(), goalPos.getZ(), 1.100000023841858);
        }

        @Override
        public void stop() {
            cat.setLying(false);
            float time = cat.level().getTimeOfDay(1.0f);
            if ((!(owner instanceof Player player) || player.getSleepTimer() >= 100)
                    && time > 0.77f
                    && time < 0.8f
                    && cat.level().getRandom().nextFloat() < 0.7f) {
                giveMorningGift();
            }

            onBedTicks = 0;
            cat.setRelaxStateOne(false);
            cat.getNavigation().stop();
        }

        @SuppressWarnings("WhileLoopReplaceableByForEach")
        private void giveMorningGift() {
            RandomSource random = cat.getRandom();

            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
            mutable.set(cat.blockPosition());

            cat.randomTeleport(
                    mutable.getX() + random.nextInt(11) - 5,
                    mutable.getY() + random.nextInt(5) - 2,
                    mutable.getZ() + random.nextInt(11) - 5,
                    false);

            mutable.set(cat.blockPosition());

            MinecraftServer server = cat.level().getServer();
            if (server == null) return;

            LootTable loot = server.reloadableRegistries().getLootTable(BuiltInLootTables.CAT_MORNING_GIFT);
            LootParams params = new LootParams.Builder((ServerLevel) cat.level())
                    .withParameter(LootContextParams.ORIGIN, cat.position())
                    .withParameter(LootContextParams.THIS_ENTITY, cat)
                    .create(LootContextParamSets.GIFT);

            Iterator<ItemStack> iterator = loot.getRandomItems(params).iterator();

            while (iterator.hasNext()) {
                ItemEntity itemEntity = new ItemEntity(
                        cat.level(),
                        (double) mutable.getX() - (double) Mth.sin(cat.yBodyRot * 0.017453292f),
                        mutable.getY(),
                        (double) mutable.getZ() + (double) Mth.cos(cat.yBodyRot * 0.017453292f),
                        iterator.next());

                EntityDropItemEvent event = new EntityDropItemEvent(cat.getBukkitEntity(), (org.bukkit.entity.Item) itemEntity.getBukkitEntity());
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled()) cat.level().addFreshEntity(itemEntity);
            }

        }

        @Override
        public void tick() {
            if (owner == null || goalPos == null) return;

            cat.setInSittingPose(false);
            cat.getNavigation().moveTo(goalPos.getX(), goalPos.getY(), goalPos.getZ(), 1.100000023841858d);

            if (cat.distanceToSqr(owner) >= 2.5d) {
                cat.setLying(false);
                return;
            }

            ++onBedTicks;

            if (onBedTicks > adjustedTickDelay(16)) {
                cat.setLying(true);
                cat.setRelaxStateOne(false);
            } else {
                cat.lookAt(owner, 45.0f, 45.0f);
                cat.setRelaxStateOne(true);
            }
        }
    }
}