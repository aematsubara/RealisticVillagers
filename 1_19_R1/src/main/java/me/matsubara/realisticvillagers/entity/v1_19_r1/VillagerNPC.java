package me.matsubara.realisticvillagers.entity.v1_19_r1;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.ExpectingType;
import me.matsubara.realisticvillagers.data.InteractType;
import me.matsubara.realisticvillagers.data.TargetReason;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.VillagerNPCGoalPackages;
import me.matsubara.realisticvillagers.entity.v1_19_r1.ai.sensing.VillagerHostilesSensor;
import me.matsubara.realisticvillagers.event.VillagerRemoveEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.nms.v1_19_r1.NMSConverter;
import net.minecraft.Util;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R1.entity.CraftVillager;
import org.bukkit.craftbukkit.v1_19_R1.event.CraftEventFactory;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("Guava")
@Getter
@Setter
public class VillagerNPC extends Villager implements IVillagerNPC, CrossbowAttackMob {

    private final RealisticVillagers plugin = JavaPlugin.getPlugin(RealisticVillagers.class);

    private String villagerName;
    private String sex;
    private UUID partner;
    private boolean isPartnerVillager;
    private long lastProcreation;
    private int skinTextureId;
    private UUID interactingWith;
    private InteractType interactType;
    private ExpectingType expectingType;
    private UUID expectingFrom;
    private int expectingTicks;
    private boolean giftDropped;
    private UUID procreatingWith;
    private UUID father;
    private boolean isFatherVillager;
    private UUID mother;
    private boolean isMotherVillager;
    private List<UUID> childrens = new ArrayList<>();
    private UUID bedHomeWorld;
    private BlockPos bedHome;
    private Set<EntityType<?>> targetEntities = getDefaultTargets();
    private long lastGossipTime;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private DamageSource lastDamageSource;

    private final SimpleContainer inventory = new SimpleContainer(Math.min(36, Config.VILLAGER_INVENTORY_SIZE.asInt()), getBukkitEntity());
    private final ItemCooldowns cooldowns = new ItemCooldowns();

    public final static MemoryModuleType<Boolean> HAS_HEALED_GOLEM_RECENTLY = NMSConverter.registerMemoryType("has_healed_golem_recently", Codec.BOOL);
    public final static MemoryModuleType<Boolean> CELEBRATE_VICTORY = NMSConverter.registerMemoryType("celebrate_victory", Codec.BOOL);
    public final static MemoryModuleType<GlobalPos> STAY_PLACE = NMSConverter.registerMemoryType("stay_place", GlobalPos.CODEC);
    public final static MemoryModuleType<Long> HEARD_HORN_TIME = NMSConverter.registerMemoryType("heard_horn_time");
    public final static MemoryModuleType<Player> PLAYER_HORN = NMSConverter.registerMemoryType("player_horn");
    public final static MemoryModuleType<TargetReason> TARGET_REASON = NMSConverter.registerMemoryType("target_reason");

    public final static Activity STAY = NMSConverter.registerActivity("stay");

    private final static ImmutableList<MemoryModuleType<?>> MEMORIES = ImmutableList.of(
            MemoryModuleType.HOME,
            MemoryModuleType.JOB_SITE,
            MemoryModuleType.POTENTIAL_JOB_SITE,
            MemoryModuleType.MEETING_POINT,
            MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.VISIBLE_VILLAGER_BABIES,
            MemoryModuleType.NEAREST_PLAYERS,
            MemoryModuleType.NEAREST_VISIBLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
            MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.INTERACTION_TARGET,
            MemoryModuleType.BREED_TARGET,
            MemoryModuleType.PATH,
            MemoryModuleType.DOORS_TO_CLOSE,
            MemoryModuleType.NEAREST_BED,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.NEAREST_HOSTILE,
            MemoryModuleType.SECONDARY_JOB_SITE,
            MemoryModuleType.HIDING_PLACE,
            MemoryModuleType.HEARD_BELL_TIME,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.LAST_SLEPT,
            MemoryModuleType.LAST_WOKEN,
            MemoryModuleType.LAST_WORKED_AT_POI,
            MemoryModuleType.GOLEM_DETECTED_RECENTLY,
            HAS_HEALED_GOLEM_RECENTLY,
            CELEBRATE_VICTORY,
            STAY_PLACE,
            HEARD_HORN_TIME,
            PLAYER_HORN,
            TARGET_REASON,
            MemoryModuleType.ATTACK_TARGET,
            MemoryModuleType.ATTACK_COOLING_DOWN);

    private final static SensorType<VillagerHostilesSensor> NEAREST_HOSTILE = NMSConverter.registerSensor("nearest_hostile", VillagerHostilesSensor::new);

    private final static ImmutableList<SensorType<? extends Sensor<? super Villager>>> SENSORS = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES,
            SensorType.NEAREST_BED,
            SensorType.HURT_BY,
            SensorType.VILLAGER_BABIES,
            SensorType.SECONDARY_POIS,
            SensorType.GOLEM_DETECTED,
            SensorType.NEAREST_PLAYERS,
            SensorType.NEAREST_ITEMS,
            NEAREST_HOSTILE != null ? NEAREST_HOSTILE : SensorType.VILLAGER_HOSTILES);

    private final static Vec3i ITEM_PICKUP_REACH = new Vec3i(1, 1, 1);
    private final static Set<Item> ALLOWED_ARROW_TYPE = Sets.newHashSet(Items.ARROW);
    private final static EntityDataAccessor<Boolean> DATA_IS_CHARGING = SynchedEntityData.defineId(VillagerNPC.class, EntityDataSerializers.BOOLEAN);
    private final static ImmutableSet<Class<? extends Item>> DO_NOT_SAVE = ImmutableSet.of(
            SwordItem.class,
            AxeItem.class,
            ProjectileWeaponItem.class,
            ShieldItem.class,
            ArmorItem.class);

    public VillagerNPC(EntityType<? extends Villager> type, Level world) {
        this(type, world, VillagerType.PLAINS);
    }

    public VillagerNPC(EntityType<? extends Villager> type, Level world, VillagerType villagerType) {
        super(type, world, villagerType);

        refreshBrain((ServerLevel) level);

        NMSConverter.registerAttribute(this, Attributes.ATTACK_DAMAGE, Config.ATTACK_DAMAGE.asDouble());
        NMSConverter.registerAttribute(this, Attributes.MAX_HEALTH, Config.VILLAGER_MAX_HEALTH.asDouble());

        setPersistenceRequired();
    }

    @Override
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<Villager> brain = brainProvider().makeBrain(dynamic);
        registerBrainGoals(brain);
        return brain;
    }

    @Override
    protected Brain.Provider<Villager> brainProvider() {
        return Brain.provider(MEMORIES, SENSORS);
    }

    @Override
    public void refreshBrain(ServerLevel level) {
        Brain<Villager> brain = getBrain();
        brain.stopAll(level, this);
        this.brain = brain.copyWithoutBehaviors();
        this.brain.removeAllBehaviors();
        registerBrainGoals(getBrain());
    }

    private void registerBrainGoals(Brain<Villager> brain) {
        // We can use VillagerGoalPackages for PANIC, PLAY & PRE_RAID activities since we don't modify any behavior.
        VillagerProfession profession = getProfession();
        if (isBaby()) {
            brain.setSchedule(Schedule.VILLAGER_BABY);
            brain.addActivity(Activity.PLAY, VillagerGoalPackages.getPlayPackage(SPEED_MODIFIER));
        } else {
            brain.setSchedule(Schedule.VILLAGER_DEFAULT);
            brain.addActivityWithConditions(
                    Activity.WORK,
                    VillagerNPCGoalPackages.getWorkPackage(profession),
                    ImmutableSet.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));
        }

        brain.addActivity(Activity.CORE, VillagerNPCGoalPackages.getCorePackage(profession));
        brain.addActivityWithConditions(
                Activity.MEET,
                VillagerNPCGoalPackages.getMeetPackage(),
                ImmutableSet.of(Pair.of(MemoryModuleType.MEETING_POINT, MemoryStatus.VALUE_PRESENT)));
        brain.addActivity(Activity.REST, VillagerNPCGoalPackages.getRestPackage());
        brain.addActivity(Activity.IDLE, VillagerNPCGoalPackages.getIdlePackage());
        brain.addActivity(Activity.PANIC, VillagerGoalPackages.getPanicPackage(profession, SPEED_MODIFIER));
        brain.addActivity(Activity.PRE_RAID, VillagerGoalPackages.getPreRaidPackage(profession, SPEED_MODIFIER));
        brain.addActivity(Activity.RAID, VillagerNPCGoalPackages.getRaidPackage());
        brain.addActivity(Activity.HIDE, VillagerNPCGoalPackages.getHidePackage());
        brain.addActivity(Activity.FIGHT, VillagerNPCGoalPackages.getFightPackage());
        brain.addActivity(STAY, VillagerNPCGoalPackages.getStayPackage());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        savePluginData(tag);
    }

    public void savePluginData(CompoundTag tag) {
        CompoundTag villagerTag = new CompoundTag();

        villagerTag.put("Inventory", inventory.createTag());
        villagerTag.putString("Name", villagerName);
        villagerTag.putString("Sex", sex);
        if (partner != null) villagerTag.putUUID("Partner", partner);
        villagerTag.putBoolean("IsPartnerVillager", isPartnerVillager);
        villagerTag.putLong("LastProcreation", lastProcreation);
        villagerTag.putInt("SkinTextureId", skinTextureId);
        if (father != null) villagerTag.putUUID("Father", father);
        if (mother != null) villagerTag.putUUID("Mother", mother);
        villagerTag.putBoolean("IsFatherVillager", isFatherVillager);
        saveCollection(childrens, NbtUtils::createUUID, "Childrens", villagerTag);
        saveCollection(targetEntities, type -> StringTag.valueOf(type.toShortString()), "TargetEntities", villagerTag);

        if (bedHome != null && bedHomeWorld != null) {
            CompoundTag bedHomeTag = new CompoundTag();
            bedHomeTag.putUUID("BedHomeWorld", bedHomeWorld);
            bedHomeTag.put("BedHomePos", newDoubleList(bedHome.getX(), bedHome.getY(), bedHome.getZ()));
            villagerTag.put("BedHome", bedHomeTag);
        }

        tag.put("VillagerNPCValues", villagerTag);
    }

    private <T> void saveCollection(Collection<T> collection, Function<T, Tag> mapper, String name, CompoundTag tag) {
        ListTag list = new ListTag();
        for (T object : collection) {
            list.add(mapper.apply(object));
        }
        tag.put(name, list);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        Tag base = tag.get("VillagerNPCValues");
        loadPluginData(base != null ? (CompoundTag) base : new CompoundTag());
    }

    public void loadPluginData(CompoundTag villagerTag) {
        inventory.fromTag(villagerTag.getList("Inventory", 10));
        villagerName = villagerTag.getString("Name");
        sex = villagerTag.getString("Sex");
        if (sex.isEmpty()) sex = random.nextBoolean() ? "male" : "female";
        if (villagerName.isEmpty()) setVillagerName(plugin.getRandomNameBySex(sex));
        if (villagerTag.contains("Partner")) partner = villagerTag.getUUID("Partner");
        isPartnerVillager = villagerTag.getBoolean("IsPartnerVillager");
        lastProcreation = villagerTag.getLong("LastProcreation");
        skinTextureId = villagerTag.contains("SkinTextureId") ? villagerTag.getInt("SkinTextureId") : -1;
        if (villagerTag.contains("Father")) father = villagerTag.getUUID("Father");
        if (villagerTag.contains("Mother")) mother = villagerTag.getUUID("Mother");
        isFatherVillager = villagerTag.getBoolean("IsFatherVillager");
        fillCollection(childrens, NbtUtils::loadUUID, "Childrens", villagerTag);
        fillCollection(targetEntities, input -> EntityType.byString(input.getAsString()).orElse(null), "TargetEntities", villagerTag);
        loadBedHomePosition(villagerTag);
    }

    private void loadBedHomePosition(CompoundTag tag) {
        if (!tag.contains("BedHome")) return;

        CompoundTag bedHomeTag = (CompoundTag) tag.get("BedHome");
        if (bedHomeTag == null) return;

        World world = Bukkit.getServer().getWorld(bedHomeTag.getUUID("BedHomeWorld"));
        if (world == null) return;

        ListTag coords = bedHomeTag.getList("BedHomePos", 6);
        double x = coords.getDouble(0);
        double y = coords.getDouble(1);
        double z = coords.getDouble(2);

        ServerLevel level = ((CraftWorld) world).getHandle();
        GlobalPos pos = GlobalPos.of(level.dimension(), new BlockPos(x, y, z));

        BlockState state = level.getBlockState(pos.pos());
        if (!state.is(BlockTags.BEDS) || state.getValue(BedBlock.OCCUPIED)) return;

        Predicate<Holder<PoiType>> predicate = holder -> holder.is(PoiTypes.HOME);
        if (!level.getPoiManager().exists(pos.pos(), predicate)) return;

        Optional<BlockPos> temp = level.getPoiManager().find(predicate, found -> found.equals(pos.pos()), pos.pos(), 1, PoiManager.Occupancy.ANY);
        if (temp.isEmpty() || !temp.get().equals(pos.pos())) return;

        getBrain().setMemory(MemoryModuleType.HOME, pos);
        bedHome = pos.pos();
        bedHomeWorld = world.getUID();
    }

    @SuppressWarnings({"Guava"})
    private <T> void fillCollection(Collection<T> collection, Function<Tag, T> mapper, String name, CompoundTag tag) {
        if (!tag.contains(name)) return;
        collection.clear();

        ListTag list = (ListTag) tag.get(name);
        if (list == null) return;

        for (Tag content : list) {
            T value = mapper.apply(content);
            if (value != null) collection.add(value);
        }
    }

    @Override
    public void releasePoi(MemoryModuleType<GlobalPos> memory) {
        level.broadcastEntityEvent(this, (byte) 13);
        if (memory.equals(MemoryModuleType.HOME)) {
            Optional<GlobalPos> pos = getBrain().getMemory(memory);
            if (pos.isPresent() && pos.get().pos().equals(bedHome)) {
                bedHome = null;
                bedHomeWorld = null;
            }
        }
        super.releasePoi(memory);
    }

    @Override
    public CraftVillager getBukkitEntity() {
        return (CraftVillager) super.getBukkitEntity();
    }

    @Override
    public void setVillagerName(String villagerName) {
        setCustomName(Component.literal(this.villagerName = villagerName));
    }

    public boolean hasPartner() {
        return partner != null;
    }

    public boolean isConversating() {
        return isInteracting() && interactType.isGUI();
    }

    public boolean isFollowing() {
        return isInteracting() && interactType.isFollowing();
    }

    public void stopInteracting() {
        setInteractingWithAndType(null, null);
    }

    public void setInteractingWithAndType(UUID interactingWith, InteractType interactType) {
        this.interactingWith = interactingWith;
        this.interactType = interactType;
    }

    @Override
    public boolean canFireProjectileWeapon(ProjectileWeaponItem item) {
        return (item == Items.BOW || item == Items.CROSSBOW) &&
                (!Config.REQUIRE_ARROWS_FOR_PROJECTILE_WEAPON.asBool() || inventory.hasAnyOf(ALLOWED_ARROW_TYPE));
    }

    public int getMeleeAttackCooldown() {
        return Config.MELEE_ATTACK_COOLDOWN.asInt();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean canAttack() {
        return isHoldingWeapon();
    }

    public boolean is(VillagerProfession... professions) {
        for (VillagerProfession profession : professions) {
            if (getProfession() == profession) return true;
        }
        return false;
    }

    public boolean isHoldingWeapon() {
        return isHoldingMeleeWeapon() || isHoldingRangeWeapon();
    }

    public boolean isHoldingMeleeWeapon() {
        return isHolding(item -> item.getItem() instanceof SwordItem || item.getItem() instanceof AxeItem);
    }

    public boolean isHoldingRangeWeapon() {
        return isHolding(item -> item.getItem() instanceof ProjectileWeaponItem weapon && canFireProjectileWeapon(weapon));
    }

    @Override
    public void setChargingCrossbow(boolean flag) {
        entityData.set(DATA_IS_CHARGING, flag);
    }

    @Override
    public ItemStack getProjectile(ItemStack itemstack) {
        return Items.ARROW.getDefaultInstance();
    }

    @Override
    public void onCrossbowAttackPerformed() {
        noActionTime = 0;
    }

    @Override
    public void shootCrossbowProjectile(LivingEntity target, ItemStack bow, Projectile projectile, float force) {
        shootCrossbowProjectile(this, target, projectile, force, Config.RANGE_WEAPON_POWER.asFloat());
    }

    @Override
    public void performRangedAttack(LivingEntity target, float force) {
        if (!isHoldingRangeWeapon()) {
            onCrossbowAttackPerformed();
            return;
        }

        boolean isBow = isHolding(Items.BOW);
        InteractionHand hand = ProjectileUtil.getWeaponHoldingHand(this, getMainHandItem().getItem());

        ItemStack weapon = getItemInHand(hand);
        ItemStack arrow = new ItemStack(Items.ARROW);

        ArrowItem arrowAsItem = (ArrowItem) (arrow.getItem() instanceof ArrowItem ? arrow.getItem() : Items.ARROW);

        AbstractArrow projectile;
        if (isBow) {
            projectile = ProjectileUtil.getMobArrow(this, arrow, force);
        } else {
            projectile = arrowAsItem.createArrow(level, arrow, this);
            projectile.setSoundEvent(SoundEvents.CROSSBOW_HIT);
            projectile.setShotFromCrossbow(true);

            int piercing = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PIERCING, weapon);
            if (piercing > 0) projectile.setPierceLevel((byte) piercing);
        }
        projectile.setCritArrow(true);
        projectile.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;

        if (isBow) {
            shootBow(target, projectile);
        } else {
            shootCrossbowProjectile(target, weapon, projectile, force);
        }

        weapon.hurtAndBreak(arrow.is(Items.FIREWORK_ROCKET) ? 3 : 1, this, (npc) -> npc.broadcastBreakEvent(hand));

        EntityShootBowEvent event = CraftEventFactory.callEntityShootBowEvent(
                this,
                weapon,
                arrow,
                projectile,
                getUsedItemHand(),
                force,
                true);

        if (event.isCancelled()) {
            event.getProjectile().remove();
            return;
        } else if (event.getProjectile() == projectile.getBukkitEntity() && !level.addFreshEntity(projectile))
            return;

        onCrossbowAttackPerformed();
    }

    private void shootBow(LivingEntity target, Projectile projectile) {
        double deltaX = target.getX() - getX();
        double deltaY = target.getY(0.3333333333333333d) - projectile.getY();
        double deltaZ = target.getZ() - getZ();

        double horizontalMagnitude = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        projectile.shoot(
                deltaX,
                deltaY + horizontalMagnitude * 0.20000000298023224d,
                deltaZ,
                Config.RANGE_WEAPON_POWER.asFloat(),
                (float) (14 - level.getDifficulty().getId() * 4));

        playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (random.nextFloat() * 0.4f + 0.8f));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_IS_CHARGING, false);
    }

    private void maybeWearWeaponAndShield() {
        if (random.nextFloat() >= Config.CHANCE_OF_WEARING_WEAPON.asFloat()) return;

        ItemStack weapon = createSpawnWeapon();
        if (!weapon.isEmpty()) {
            setItemSlot(EquipmentSlot.MAINHAND, weapon);
            if (random.nextFloat() < Config.CHANCE_OF_WEARING_SHIELD.asFloat()) {
                setItemSlot(EquipmentSlot.OFFHAND, Items.SHIELD.getDefaultInstance());
            }
        }

        if (weapon.getItem() instanceof ProjectileWeaponItem) {
            int min = Config.MIN_AMOUNT_OF_ARROWS.asInt();
            int max = Config.MAX_AMOUNT_OF_ARROWS.asInt();
            inventory.addItem(new ItemStack(Items.ARROW, random.nextInt(min, max)));
        }
    }

    private void maybeWearArmor(EquipmentSlot slot) {
        if (!getItemBySlot(slot).isEmpty()) return;
        if (random.nextFloat() >= Config.CHANCE_OF_WEARING_EACH_ARMOUR_ITEM.asFloat()) return;

        Optional<Item> item = getItemByNameFromConfig("armor-items." + slot.name().toLowerCase());
        item.ifPresent(value -> setItemSlot(slot, value.getDefaultInstance()));
    }

    @Override
    public void die(DamageSource source) {
        super.die(source);

        if (!level.isClientSide && hasPartner() && !isPartnerVillager) {
            Player player = level.getPlayerByUUID(getPartner());
            if (player != null) player.sendSystemMessage(getCombatTracker().getDeathMessage());
        }
    }

    private ItemStack createSpawnWeapon() {
        List<String> weapons = plugin.getConfig().getStringList("random-weapon");
        if (weapons.isEmpty()) return ItemStack.EMPTY;

        Optional<Item> item = Registry.ITEM.getOptional(new ResourceLocation(Util.getRandom(weapons, random).toLowerCase()));
        if (item.isPresent()) return item.get().getDefaultInstance();

        return ItemStack.EMPTY;
    }

    private Optional<Item> getItemByNameFromConfig(String path) {
        String item = plugin.getConfig().getString(path);
        if (item != null) return Registry.ITEM.getOptional(new ResourceLocation(item.toLowerCase()));
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    private boolean isCharging() {
        return entityData.get(DATA_IS_CHARGING);
    }

    @Override
    public @Nullable SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData groupData, @Nullable CompoundTag tag) {
        if (!isBaby()) {
            maybeWearWeaponAndShield();

            maybeWearArmor(EquipmentSlot.HEAD);
            maybeWearArmor(EquipmentSlot.CHEST);
            maybeWearArmor(EquipmentSlot.LEGS);
            maybeWearArmor(EquipmentSlot.FEET);

            enchantItemBySlot(EquipmentSlot.MAINHAND, 0.25f);
            enchantItemBySlot(EquipmentSlot.OFFHAND, 0.25f);

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
                enchantItemBySlot(slot, 0.5f);
            }
        }

        return super.finalizeSpawn(level, difficulty, spawnType, groupData, tag);
    }

    private void enchantItemBySlot(EquipmentSlot slot, float chance) {
        float multiplier = getLevel().getCurrentDifficultyAt(blockPosition()).getSpecialMultiplier();

        ItemStack item = getItemBySlot(slot);
        if (!item.isEmpty() && random.nextFloat() < chance * multiplier) {
            setItemSlot(slot, EnchantmentHelper.enchantItem(random, item, (int) (5.0f + multiplier * (float) random.nextInt(18)), false));
        }
    }

    @Override
    public void setDropChance(EquipmentSlot slot, float chance) {
        (slot.getType() == EquipmentSlot.Type.HAND ? handDropChances : armorDropChances)[slot.getIndex()] = chance;
    }

    @Override
    protected Vec3i getPickupReach() {
        return ITEM_PICKUP_REACH;
    }

    @Override
    public void onReputationEventFrom(ReputationEventType type, Entity entity) {
        boolean raidCheck = Config.VILLAGER_ATTACK_PLAYER_DURING_RAID.asBool() || !isInsideRaid();
        boolean thornsCheck = !(getLastDamageSource() instanceof EntityDamageSource source) || !source.isThorns();

        if (type != ReputationEventType.VILLAGER_HURT
                || (EntitySelector.NO_CREATIVE_OR_SPECTATOR.test(entity)
                && raidCheck
                && thornsCheck)) {
            super.onReputationEventFrom(type, entity);
        }

        if (!isPartner(entity.getUUID()) || !(entity instanceof ServerPlayer player)) return;

        if (getPlayerReputation(player) < Config.DIVORCE_IF_REPUTATION_IS_LESS_THAN.asInt()) {
            plugin.getMessages().send(this, player.getBukkitEntity(), Messages.Message.MARRY_END);

            divorceAndDropRing(player);
        }
    }

    public void divorceAndDropRing(Player player) {
        org.bukkit.inventory.ItemStack ring = plugin.getRing().getRecipe().getResult();
        getBukkitEntity().getInventory().removeItem(ring);
        drop(CraftItemStack.asNMSCopy(ring));

        setPartner(null);
        player.getBukkitEntity().getPersistentDataContainer().remove(plugin.getMarriedWith());
    }

    public boolean isPartner(UUID uuid) {
        return partner != null && partner.equals(uuid);
    }

    @Override
    public String getActivityName(String none) {
        return getBrain().getActiveNonCoreActivity().map(Activity::getName).orElse(none);
    }

    @Override
    public void addTarget(org.bukkit.entity.EntityType type) {
        ifTargetPresent(type, entityType -> targetEntities.add(entityType));
    }

    @Override
    public void removeTarget(org.bukkit.entity.EntityType type) {
        ifTargetPresent(type, entityType -> targetEntities.remove(entityType));
    }

    private void ifTargetPresent(org.bukkit.entity.EntityType type, Consumer<EntityType<?>> consumer) {
        EntityType.byString(type.name().toLowerCase()).ifPresent(consumer);
    }

    private Set<EntityType<?>> getDefaultTargets() {
        Set<EntityType<?>> types = new HashSet<>();

        for (String entity : plugin.getDefaultTargets()) {
            Optional<EntityType<?>> type = EntityType.byString(entity.toLowerCase());
            if (type.isEmpty()) continue;
            if (type.get().getCategory() != MobCategory.MONSTER) continue;
            types.add(type.get());
        }
        return types;
    }

    @Override
    public boolean isTarget(org.bukkit.entity.EntityType type) {
        for (EntityType<?> entityType : targetEntities) {
            if (entityType.toShortString().equalsIgnoreCase(type.name())) return true;
        }
        return false;
    }

    @Override
    public ItemStack eat(Level world, ItemStack itemstack) {
        world.playSound(null, positionAsBlock(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.5f, random.nextFloat() * 0.1f + 0.9f);
        return super.eat(world, itemstack);
    }

    @Override
    public SoundEvent getNotifyTradeSound() {
        return useVillagerSounds() ? super.getNotifyTradeSound() : null;
    }

    @Override
    public void notifyTradeUpdated(ItemStack item) {
        if (level.isClientSide || ambientSoundTime <= -getAmbientSoundInterval() + 20) return;

        ambientSoundTime = -getAmbientSoundInterval();

        SoundEvent tradeSound = getTradeUpdatedSound(!item.isEmpty());
        if (tradeSound != null) playSound(tradeSound, getSoundVolume(), getVoicePitch());
    }

    @Override
    protected SoundEvent getTradeUpdatedSound(boolean success) {
        return useVillagerSounds() ? super.getTradeUpdatedSound(success) : null;
    }

    @Override
    public void playCelebrateSound() {
        if (useVillagerSounds()) super.playCelebrateSound();
    }

    @Override
    public void setUnhappy() {
        if (useVillagerSounds()) {
            super.setUnhappy();
        } else {
            setUnhappyCounter(40);
        }
    }

    @Override
    protected @Nullable SoundEvent getAmbientSound() {
        return useVillagerSounds() ? super.getAmbientSound() : null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        if (useVillagerSounds()) return super.getHurtSound(source);
        return switch (source.getMsgId()) {
            case "onFire" -> SoundEvents.PLAYER_HURT_ON_FIRE;
            case "drown" -> SoundEvents.PLAYER_HURT_DROWN;
            case "sweetBerryBush" -> SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH;
            case "freeze" -> SoundEvents.PLAYER_HURT_FREEZE;
            default -> SoundEvents.PLAYER_HURT;
        };
    }

    @Override
    protected SoundEvent getDeathSound() {
        return useVillagerSounds() ? super.getDeathSound() : SoundEvents.PLAYER_DEATH;
    }

    @Override
    public SoundEvent getSwimSound() {
        return useVillagerSounds() ? super.getSwimSound() : SoundEvents.PLAYER_SWIM;
    }

    @Override
    public SoundEvent getSwimSplashSound() {
        return useVillagerSounds() ? super.getSwimSplashSound() : SoundEvents.PLAYER_SPLASH;
    }

    @Override
    public SoundEvent getSwimHighSpeedSplashSound() {
        return useVillagerSounds() ? super.getSwimHighSpeedSplashSound() : SoundEvents.PLAYER_SPLASH_HIGH_SPEED;
    }

    @Override
    public SoundSource getSoundSource() {
        return useVillagerSounds() ? super.getSoundSource() : SoundSource.PLAYERS;
    }

    @Override
    public Fallsounds getFallSounds() {
        return useVillagerSounds() ? super.getFallSounds() : new Fallsounds(SoundEvents.PLAYER_SMALL_FALL, SoundEvents.PLAYER_BIG_FALL);
    }

    private boolean useVillagerSounds() {
        return Config.USE_VILLAGER_SOUNDS.asBool();
    }

    @Override
    public SimpleContainer getInventory() {
        return inventory;
    }

    public void setPartner(UUID partner, boolean isPartnerVillager) {
        this.partner = partner;
        this.isPartnerVillager = isPartnerVillager;
    }

    public void startTrading(Player player) {
        updateSpecialPrices(player);
        setTradingPlayer(player);
        openTradingScreen(player, getDisplayName(), getVillagerData().getLevel());
    }

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    private void updateSpecialPrices(Player player) {
        int reputation = getPlayerReputation(player);
        if (reputation != 0) {
            Iterator<MerchantOffer> offers = getOffers().iterator();
            while (offers.hasNext()) {
                MerchantOffer offer = offers.next();
                offer.addToSpecialPriceDiff(-Mth.floor((float) reputation * offer.getPriceMultiplier()));
            }
        }

        MobEffectInstance heroEffect = player.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
        if (heroEffect == null) return;

        int amplifier = heroEffect.getAmplifier();
        Iterator<MerchantOffer> offers = getOffers().iterator();

        while (offers.hasNext()) {
            MerchantOffer offer = offers.next();
            int discount = (int) Math.floor((0.3d + 0.0625d * (double) amplifier) * (double) offer.getBaseCostA().getCount());
            offer.addToSpecialPriceDiff(-Math.max(discount, 1));
        }
    }

    public boolean isFemale() {
        return sex.equalsIgnoreCase("female");
    }

    public boolean isMale() {
        return sex.equalsIgnoreCase("male");
    }

    @Override
    public boolean is(org.bukkit.entity.Villager.Profession... professions) {
        for (org.bukkit.entity.Villager.Profession profession : professions) {
            if (is(CraftVillager.bukkitToNmsProfession(profession))) return true;
        }
        return false;
    }

    @Override
    public SlotAccess getSlot(int slot) {
        int finalSlot = slot - 300;
        return finalSlot >= 0 && finalSlot < inventory.getContainerSize() ?
                SlotAccess.forContainer(inventory, finalSlot) :
                super.getSlot(slot);
    }

    public BlockPos positionAsBlock() {
        return new BlockPos(position());
    }

    @Override
    public void increaseMerchantCareer() {
        super.increaseMerchantCareer();

        if (!Config.USE_VILLAGER_SOUNDS.asBool()) {
            level.playSound(null, positionAsBlock(), SoundEvents.PLAYER_LEVELUP, getSoundSource(), 1.0f, 1.0f);
        }
    }

    @Override
    public void pickUpItem(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        if (!wantsToPickUp(stack)) return;

        SimpleContainer container = getInventory();
        if (!container.canAddItem(stack)) return;

        ItemStack fakeRemaining = new SimpleContainer(container).addItem(stack);
        if (CraftEventFactory.callEntityPickupItemEvent(this, entity, fakeRemaining.getCount(), false).isCancelled()) {
            return;
        }

        onItemPickup(entity);
        take(entity, stack.getCount() - fakeRemaining.getCount());

        Item item = stack.getItem();
        for (Class<? extends Item> clazz : DO_NOT_SAVE) {
            if (clazz.isAssignableFrom(item.getClass())) {
                handleRemaining(stack, fakeRemaining, entity);
                return;
            }
        }

        ItemStack remaining = container.addItem(stack);
        handleRemaining(stack, remaining, entity);
    }

    public boolean isDoingNothing() {
        return !isFighting() && !isExpecting() && !isInteracting() && !isTrading() && procreatingWith == null;
    }

    private void handleRemaining(ItemStack original, ItemStack remaining, ItemEntity itemEntity) {
        if (remaining.isEmpty()) itemEntity.discard();
        else original.setCount(remaining.getCount());
    }

    @Override
    public VillagerNPC getBreedOffspring(ServerLevel level, AgeableMob breedWith) {
        double chance = random.nextDouble();

        VillagerType type;
        if (chance < 0.5d) {
            type = VillagerType.byBiome(level.getBiome(blockPosition()));
        } else if (chance < 0.75d) {
            type = getVillagerData().getType();
        } else {
            type = ((Villager) breedWith).getVillagerData().getType();
        }

        VillagerNPC baby = new VillagerNPC(EntityType.VILLAGER, level, type);
        baby.finalizeSpawn(level, level.getCurrentDifficultyAt(baby.blockPosition()), MobSpawnType.BREEDING, null, null);
        return baby;
    }

    @Override
    public boolean wantsToPickUp(ItemStack stack) {
        if (!getInventory().canAddItem(stack)) return false;

        UUID thrower = getThrower(stack);
        if (isExpectingGiftFrom(thrower)) return true;

        return super.wantsToPickUp(stack) && thrower == null;
    }

    private UUID getThrower(ItemStack stack) {
        ItemMeta meta = CraftItemStack.asBukkitCopy(stack).getItemMeta();
        if (meta != null) {
            String uuidString = meta.getPersistentDataContainer().get(plugin.getGiftKey(), PersistentDataType.STRING);
            if (uuidString != null) return UUID.fromString(uuidString);
        }
        return null;
    }

    public VillagerProfession getProfession() {
        return getVillagerData().getProfession();
    }

    @Override
    public void startSleeping(BlockPos pos) {
        Optional<Long> lastSlept = brain.getMemory(MemoryModuleType.LAST_SLEPT);

        super.startSleeping(pos);
        collides = false;

        if (plugin.getVillagerTracker().fixSleep()) brain.setMemory(MemoryModuleType.LAST_SLEPT, lastSlept);
    }

    @Override
    public void stopSleeping() {
        Optional<Long> lastWoken = getBrain().getMemory(MemoryModuleType.LAST_WOKEN);

        super.stopSleeping();
        collides = true;

        if (plugin.getVillagerTracker().fixSleep()) brain.setMemory(MemoryModuleType.LAST_WOKEN, lastWoken);
    }

    @Override
    public <T extends Mob> T convertTo(EntityType<T> to, boolean equipment, EntityTransformEvent.TransformReason transformReason, CreatureSpawnEvent.SpawnReason spawnReason) {
        return Config.ZOMBIE_INFECTION.asBool() ? super.convertTo(to, equipment, transformReason, spawnReason) : null;
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        if (Config.WITCH_CONVERTION.asBool()) super.thunderHit(level, lightning);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);

        VillagerRemoveEvent removeEvent = new VillagerRemoveEvent(this, toWrapper(reason));
        plugin.getServer().getPluginManager().callEvent(removeEvent);
    }

    private VillagerRemoveEvent.RemovalReason toWrapper(RemovalReason reason) {
        return switch (reason) {
            case KILLED -> VillagerRemoveEvent.RemovalReason.KILLED;
            case DISCARDED -> VillagerRemoveEvent.RemovalReason.DISCARDED;
            case UNLOADED_TO_CHUNK -> VillagerRemoveEvent.RemovalReason.UNLOADED_TO_CHUNK;
            case UNLOADED_WITH_PLAYER -> VillagerRemoveEvent.RemovalReason.UNLOADED_WITH_PLAYER;
            case CHANGED_DIMENSION -> VillagerRemoveEvent.RemovalReason.CHANGED_DIMENSION;
        };
    }

    @Override
    public void spawnGolemIfNeeded(ServerLevel level, long time, int villagersRequried) {
        if (!Config.VILLAGER_SPAWN_IRON_GOLEM.asBool()) return;
        super.spawnGolemIfNeeded(level, time, villagersRequried);
    }

    @Override
    protected void hurtCurrentlyUsedShield(float blockingModifier) {
        if (!useItem.is(Items.SHIELD)) return;

        if (blockingModifier < 3.0f) return;

        int damage = 1 + Mth.floor(blockingModifier);

        InteractionHand hand = getUsedItemHand();
        useItem.hurtAndBreak(damage, this, (entityhuman) -> entityhuman.broadcastBreakEvent(hand));

        if (!useItem.isEmpty()) return;

        if (hand == InteractionHand.MAIN_HAND) {
            setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else {
            setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        }

        useItem = ItemStack.EMPTY;
        playSound(SoundEvents.SHIELD_BREAK, 0.8f, 0.8f + random.nextFloat() * 0.4f);
    }

    @Override
    protected void hurtArmor(DamageSource source, float damage) {
        hurt(source, damage, EquipmentSlot.values());
    }

    @Override
    protected void hurtHelmet(DamageSource source, float damage) {
        hurt(source, damage, EquipmentSlot.HEAD);
    }

    private void hurt(DamageSource source, float damage, EquipmentSlot... slots) {
        if (damage <= 0.0f) return;

        damage /= 4.0f;
        if (damage < 1.0f) damage = 1.0f;

        for (EquipmentSlot slot : slots) {
            if (slot.getType() == EquipmentSlot.Type.HAND) continue;
            ItemStack item = getItemBySlot(slot);

            if ((!source.isFire() || !item.getItem().isFireResistant()) && item.getItem() instanceof ArmorItem) {
                item.hurtAndBreak(
                        (int) damage,
                        this,
                        npc -> npc.broadcastBreakEvent(EquipmentSlot.byTypeAndIndex(EquipmentSlot.Type.ARMOR, slot.getIndex())));
            }
        }
    }

    @Override
    protected void blockUsingShield(LivingEntity living) {
        super.blockUsingShield(living);
        if (living.canDisableShield()) disableShield(true);
    }

    public boolean canBreedWith(VillagerNPC other) {
        return !other.getSex().equalsIgnoreCase(sex)
                && canBreed()
                && other.canBreed()
                && (!hasPartner() ? !other.hasPartner() : isPartner(other.getUUID()))
                && !isFamily(other.getUUID(), false);
    }

    public boolean isFamily(UUID otherUUID) {
        return isFamily(otherUUID, false);
    }

    @Override
    public void gossip(ServerLevel level, Villager with, long time) {
        if (!(with instanceof VillagerNPC npc)) return;
        if ((time >= lastGossipTime && time < lastGossipTime + 1200L)) return;
        if ((time >= npc.getLastGossipTime() && time < npc.getLastGossipTime() + 1200L)) return;

        // Reduced amount of gossips from 10 to 5, otherwise villager reputation with a player will rise too fast.
        getGossips().transferFrom(with.getGossips(), random, 5);
        lastGossipTime = time;
        npc.setLastGossipTime(time);
        spawnGolemIfNeeded(level, time, 5);
    }

    public boolean isFamily(UUID otherUUID, boolean checkPartner) {
        return (checkPartner && isPartner(otherUUID))
                || childrens.contains(otherUUID)
                || (father != null && father.equals(otherUUID))
                || (mother != null && mother.equals(otherUUID));
    }

    public void disableShield(boolean flag) {
        float chanceOfBlocking = 0.25f + (float) EnchantmentHelper.getBlockEfficiency(this) * 0.05f;
        if (flag) chanceOfBlocking += 0.75f;

        if (random.nextFloat() < chanceOfBlocking) {
            getCooldowns().addCooldown(Items.SHIELD, 100);
            stopUsingItem();
            level.broadcastEntityEvent(this, (byte) 30);
        }
    }

    @Override
    public void tick() {
        super.tick();
        cooldowns.tick();

        if (!collides && !isSleeping()) collides = true;

        if (expectingTicks > 0) {
            if (isExpectingBed() || !giftDropped) expectingTicks--;
        } else if (expectingFrom != null) {
            plugin.getExpectingManager().remove(expectingFrom);

            Player player = level.getPlayerByUUID(expectingFrom);
            if (player instanceof ServerPlayer serverPlayer) {
                if (expectingType.isGift()) {
                    plugin.getMessages().send(this, serverPlayer.getBukkitEntity(), Messages.Message.GIFT_EXPECTING_FAIL);
                } else {
                    plugin.getMessages().send(this, serverPlayer.getBukkitEntity(), Messages.Message.SET_HOME_FAIL);
                }
            }

            stopExpecting();
        }
    }

    @Override
    protected boolean damageEntity0(DamageSource source, float damage) {
        lastDamageSource = source;
        boolean flag = super.damageEntity0(source, damage);
        lastDamageSource = null;
        return flag;
    }

    @Override
    public boolean isDamageSourceBlocked() {
        return lastDamageSource != null && isDamageSourceBlocked(lastDamageSource);
    }

    @Override
    public @Nullable LivingEntity getTarget() {
        return brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    public void startExpectingFrom(ExpectingType expectingType, UUID expectingGiftFrom, int expectingGiftTicks) {
        setExpectingType(expectingType);
        setExpectingFrom(expectingGiftFrom);
        setExpectingTicks(expectingGiftTicks);
    }

    @Override
    public void divorceAndDropRing(org.bukkit.entity.Player player) {
        divorceAndDropRing(((CraftPlayer) player).getHandle());
    }

    @Override
    public void drop(org.bukkit.inventory.ItemStack item) {
        drop(CraftItemStack.asNMSCopy(item));
    }

    @Override
    public void startTrading(org.bukkit.entity.Player player) {
        startTrading(((CraftPlayer) player).getHandle());
    }

    public boolean isExpectingGift() {
        return isExpecting() && expectingType.isGift();
    }

    public boolean isExpectingBed() {
        return isExpecting() && expectingType.isBed();
    }

    public boolean isExpecting() {
        return expectingTicks > 0 && expectingFrom != null;
    }

    public void stopExpecting() {
        startExpectingFrom(null, null, 0);
        giftDropped = false;
    }

    public boolean isStayingInPlace() {
        return checkCurrentActivity(STAY) && isInteracting() && interactType.isStay();
    }

    public boolean checkCurrentActivity(Activity checkActivity) {
        Optional<Activity> active = getBrain().getActiveNonCoreActivity();
        return active.map(activity -> activity.equals(checkActivity)).orElse(false);
    }

    public void drop(ItemStack item) {
        if (item.isEmpty()) return;
        if (level.isClientSide) swing(InteractionHand.MAIN_HAND);

        ItemEntity itemEntity = new ItemEntity(level, getX(), getEyeY() - 0.30000001192092896d, getZ(), item);
        itemEntity.setPickUpDelay(40);
        itemEntity.setThrower(getUUID());

        float xRotSin = Mth.sin(getXRot() * 0.017453292f);
        float xRotCos = Mth.cos(getXRot() * 0.017453292f);

        float yRotSin = Mth.sin(getYRot() * 0.017453292f);
        float yRotCos = Mth.cos(getYRot() * 0.017453292f);

        float f5 = random.nextFloat() * 6.2831855f;
        float f6 = random.nextFloat() * 0.02f;

        itemEntity.setDeltaMovement(
                (double) (-yRotSin * xRotCos * 0.3f) + Math.cos(f5) * (double) f6,
                -xRotSin * 0.3f + 0.1f + (random.nextFloat() - random.nextFloat()) * 0.1f,
                (double) (yRotCos * xRotCos * 0.3f) + Math.sin(f5) * (double) f6);

        level.addFreshEntity(itemEntity);
    }

    @Override
    public int getReputation(UUID uuid) {
        return getGossips().getReputation(uuid, (type) -> true);
    }

    @Override
    public org.bukkit.entity.Villager bukkit() {
        return getBukkitEntity();
    }

    @Override
    public void addMinorPositive(UUID uuid, int amount) {
        getGossips().add(uuid, GossipType.MINOR_POSITIVE, amount);
    }

    @Override
    public void addMinorNegative(UUID uuid, int amount) {
        getGossips().add(uuid, GossipType.MINOR_NEGATIVE, amount);
    }

    @Override
    public void jumpIfPossible() {
        if (isOnGround()) getJumpControl().jump();
    }

    @Override
    public boolean handleBedHome(Block block) {
        BlockPos bedPosition = new BlockPos(block.getX(), block.getY(), block.getZ());

        Predicate<Holder<PoiType>> predicate = holder -> holder.is(PoiTypes.HOME);

        // Add poi if doesn't exists.
        PoiManager poiManager = ((ServerLevel) level).getPoiManager();
        if (!poiManager.exists(bedPosition, predicate)) {
            Optional<Holder<PoiType>> poi = PoiTypes.forState(level.getBlockState(bedPosition));
            if (poi.isEmpty()) {
                plugin.getLogger().warning("An error occurred while trying to set a new POI.");
                return false;
            }
            poiManager.add(bedPosition, poi.get());
        }

        // Bed already setted in same position.
        Optional<GlobalPos> previousHome = getBrain().getMemory(MemoryModuleType.HOME);
        if (previousHome.isPresent()) {
            Optional<BlockPos> temp = poiManager.find(predicate, pos -> pos.equals(bedPosition), bedPosition, 1, PoiManager.Occupancy.ANY);
            if (temp.isPresent() && temp.get().equals(previousHome.get().pos())) {
                releasePoi(MemoryModuleType.HOME);
            }
        }

        Optional<BlockPos> takePos = poiManager.take(predicate, (holder, pos) -> pos.equals(bedPosition), bedPosition, 1);
        if (takePos.isPresent()) {
            // Release previous POI.
            releasePoi(MemoryModuleType.HOME);
        } else {
            plugin.getLogger().warning("Either the POI is already occupied or an error occurred when trying to acquire it.");
            return false;
        }

        GlobalPos bedHome = GlobalPos.of(level.dimension(), bedPosition);
        getBrain().setMemory(MemoryModuleType.HOME, bedHome);

        setBedHomeWorld(block.getWorld().getUID());
        setBedHome(bedHome.pos());

        level.broadcastEntityEvent(this, (byte) 14);
        DebugPackets.sendPoiTicketCountPacket((ServerLevel) level, bedPosition);
        return true;
    }

    @Override
    public void stayInPlace() {
        getBrain().setMemory(STAY_PLACE, GlobalPos.of(level.dimension(), blockPosition()));
        getBrain().setDefaultActivity(STAY);
        getBrain().setActiveActivityIfPossible(STAY);
    }

    public void stopStayingInPlace() {
        getBrain().eraseMemory(STAY_PLACE);
        getBrain().setDefaultActivity(Activity.IDLE);
        getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }

    @Override
    public void reactToSeekHorn(org.bukkit.entity.Player player) {
        getBrain().setMemory(HEARD_HORN_TIME, level.getGameTime());
        getBrain().setMemory(PLAYER_HORN, ((CraftPlayer) player).getHandle());
    }

    @Override
    public boolean isInsideRaid() {
        return ((ServerLevel) level).getRaidAt(blockPosition()) != null;
    }

    @Override
    public boolean isFighting() {
        return checkCurrentActivity(Activity.FIGHT);
    }

    @Override
    public boolean isProcreating() {
        return procreatingWith != null;
    }

    @Override
    public boolean isExpectingGiftFrom(UUID uuid) {
        return isExpectingGift() && expectingFrom.equals(uuid);
    }

    @Override
    public boolean isExpectingBedFrom(UUID uuid) {
        return isExpectingBed() && expectingFrom.equals(uuid);
    }

    @Override
    public boolean isInteracting() {
        return interactingWith != null && interactType != null;
    }

    @Override
    public void spawnParticle(Particle particle) {
        getBukkitEntity().getWorld().spawnParticle(
                particle,
                getRandomX(1.05d),
                getRandomY() + 1.15d,
                getRandomZ(1.05d),
                1,
                random.nextGaussian() * 0.02d,
                random.nextGaussian() * 0.02d,
                random.nextGaussian() * 0.02d);
    }
}