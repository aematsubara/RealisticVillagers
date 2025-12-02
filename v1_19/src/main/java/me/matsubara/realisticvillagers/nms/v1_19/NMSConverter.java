package me.matsubara.realisticvillagers.nms.v1_19;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_19.WanderingTraderNPC;
import me.matsubara.realisticvillagers.entity.v1_19.pet.PetCat;
import me.matsubara.realisticvillagers.entity.v1_19.pet.PetParrot;
import me.matsubara.realisticvillagers.entity.v1_19.pet.PetWolf;
import me.matsubara.realisticvillagers.entity.v1_19.pet.horse.PetDonkey;
import me.matsubara.realisticvillagers.entity.v1_19.pet.horse.PetHorse;
import me.matsubara.realisticvillagers.entity.v1_19.pet.horse.PetMule;
import me.matsubara.realisticvillagers.entity.v1_19.villager.OfflineVillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_19.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Reflection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Mule;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.entity.schedule.Timeline;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_19_R3.CraftRaid;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.block.CraftBlock;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_19_R3.entity.CraftVillager;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class NMSConverter implements INMSConverter {

    private final RealisticVillagers plugin;

    // Registry fields.
    private static final MethodHandle INSTRUSIVE_HOLDER_CACHE = Reflection.getFieldSetter(MappedRegistry.class, "m");
    private static final MethodHandle FROZEN = Reflection.getFieldSetter(MappedRegistry.class, "l");
    private static final MethodHandle ATTRIBUTES = Reflection.getFieldGetter(AttributeMap.class, "b");

    // Constructors.
    private static final MethodHandle MEMORY_MODULE_TYPE = Reflection.getConstructor(MemoryModuleType.class, Optional.class);
    private static final MethodHandle SENSOR_TYPE = Reflection.getConstructor(SensorType.class, Supplier.class);
    private static final MethodHandle ACTIVITY = Reflection.getConstructor(Activity.class, String.class);

    // Other.
    private static final MethodHandle TIMELINES = Reflection.getFieldGetter(Schedule.class, "g");
    private static final MethodHandle RULE_TYPE = Reflection.getFieldGetter(GameRules.Value.class, "a");
    private static final Field RULE_CALLBACK;

    private static final Map<String, Activity> ACTIVITIES;
    private static final FilenameFilter DATA_FILE_FILTER = (directory, name) -> new File(directory, name).isFile()
            && name.endsWith(".mca")
            && !name.contains("backup")
            && !name.contains("mcc");

    static {
        try {
            //noinspection JavaReflectionMemberAccess
            RULE_CALLBACK = GameRules.Type.class.getDeclaredField("c");
        } catch (NoSuchFieldException exception) {
            throw new RuntimeException(exception);
        }

        Map<String, Activity> activities = new HashMap<>();
        for (Field field : Activity.class.getDeclaredFields()) {
            if (!field.getType().equals(Activity.class)) continue;

            try {
                Activity activity = (Activity) field.get(null);
                activities.put(activity.getName(), activity);
            } catch (IllegalAccessException ignored) {
            }
        }
        ACTIVITIES = Collections.unmodifiableMap(activities);
    }

    public NMSConverter(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<IVillagerNPC> getNPC(org.bukkit.entity.LivingEntity living) {
        return Optional.ofNullable(((CraftEntity) living).getHandle() instanceof IVillagerNPC npc ? npc : null);
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    @Override
    public void registerEntities() {
        try {
            // "factory" field.
            Field field = EntityType.class.getDeclaredField("bA");
            Reflection.setFieldUsingUnsafe(
                    field,
                    EntityType.VILLAGER,
                    (EntityType.EntityFactory<net.minecraft.world.entity.npc.Villager>) (type, level) -> {
                        if (level.getLevelData() instanceof PrimaryLevelData data && plugin.isEnabledIn(data.getLevelName())) {
                            return new VillagerNPC(EntityType.VILLAGER, level);
                        }
                        return new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER, level);
                    });
            Reflection.setFieldUsingUnsafe(
                    field,
                    EntityType.WANDERING_TRADER,
                    (EntityType.EntityFactory<WanderingTrader>) (type, level) -> {
                        if (level.getLevelData() instanceof PrimaryLevelData data && plugin.isEnabledIn(data.getLevelName())) {
                            return new WanderingTraderNPC(EntityType.WANDERING_TRADER, level);
                        }
                        return new WanderingTrader(EntityType.WANDERING_TRADER, level);
                    });
            Reflection.setFieldUsingUnsafe(field, EntityType.DONKEY, (EntityType.EntityFactory<Donkey>) PetDonkey::new);
            Reflection.setFieldUsingUnsafe(field, EntityType.HORSE, (EntityType.EntityFactory<Horse>) PetHorse::new);
            Reflection.setFieldUsingUnsafe(field, EntityType.MULE, (EntityType.EntityFactory<Mule>) PetMule::new);
            Reflection.setFieldUsingUnsafe(field, EntityType.CAT, (EntityType.EntityFactory<Cat>) PetCat::new);
            Reflection.setFieldUsingUnsafe(field, EntityType.PARROT, (EntityType.EntityFactory<Parrot>) PetParrot::new);
            Reflection.setFieldUsingUnsafe(field, EntityType.WOLF, (EntityType.EntityFactory<Wolf>) PetWolf::new);
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public String getNPCTag(org.bukkit.entity.LivingEntity entity, boolean isInfection) {
        if (entity instanceof AbstractVillager villager) {
            CompoundTag tag = new CompoundTag();

            Optional<IVillagerNPC> optional = getNPC(villager);
            if (optional.isEmpty()) return null;

            VillagerNPC npc = (VillagerNPC) optional.get();
            npc.setWasInfected(isInfection);
            npc.savePluginData(tag);

            return tag.get(plugin.getNpcValuesKey().toString()).toString();
        } else if (entity instanceof ZombieVillager) {
            PersistentDataContainer container = entity.getPersistentDataContainer();
            return container.getOrDefault(plugin.getZombieTransformKey(), PersistentDataType.STRING, "");
        }
        return null;
    }

    @Override
    public boolean isSeekGoatHorn(@NotNull ItemStack item) {
        return item.getItemMeta() instanceof MusicInstrumentMeta meta && MusicInstrument.SEEK.equals(meta.getInstrument());
    }

    @Override
    public void createBaby(@NotNull Location location, String name, String sex, UUID motherUUID, Player father) {
        Preconditions.checkNotNull(location.getWorld());
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        VillagerNPC baby = new VillagerNPC(
                EntityType.VILLAGER,
                level,
                VillagerType.byBiome(level.getBiome(((CraftBlock) location.getBlock()).getPosition())));

        loadDataFromTag(baby.getBukkitEntity(), "");
        baby.setVillagerName(name);
        baby.setSex(sex);

        baby.setAge(-24000);
        baby.moveTo(location.getX(), location.getY(), location.getZ(), 0.0f, 0.0f);

        // Shouldn't be null unless the mother is dead.
        IVillagerNPC mother = plugin.getTracker().getOffline(motherUUID);
        if (mother != null) {
            org.bukkit.entity.LivingEntity bukkitMother = plugin.getUnloadedOffline(mother);

            VillagerNPC nmsMother = bukkitMother != null ? ((VillagerNPC) ((CraftVillager) bukkitMother).getHandle()) : null;
            if (nmsMother != null) {
                nmsMother.setAge(6000);
                nmsMother.getChildrens().add(baby.getOffline());
                baby.setMother(nmsMother.getOffline());
            }
        }

        UUID fatherUUID = father.getUniqueId();
        baby.setFather(VillagerNPC.dummyPlayerOffline(fatherUUID));
        baby.setFatherVillager(false);

        GossipContainer gossips = baby.getGossips();
        for (GossipType gossipType : GossipType.values()) {
            gossips.remove(gossipType);
        }

        int reputation = Math.min(Config.INITIAL_REPUTATION_AT_BIRTH.asInt(), 75);
        if (reputation > 1) gossips.add(fatherUUID, GossipType.MINOR_POSITIVE, reputation);

        level.addFreshEntityWithPassengers(baby, CreatureSpawnEvent.SpawnReason.BREEDING);
        level.broadcastEntityEvent(baby, (byte) 12);
    }

    @Override
    public void loadDataFromTag(org.bukkit.entity.LivingEntity living, @NotNull String tag) {
        try {
            CompoundTag villagerTag = tag.isEmpty() ? new CompoundTag() : TagParser.parseTag(tag);

            Optional<IVillagerNPC> npc = getNPC(living);
            if (npc.isEmpty()) return;

            if (npc.get() instanceof VillagerNPC temp) {
                temp.loadPluginData(villagerTag);
            } else if (npc.get() instanceof WanderingTraderNPC temp) {
                temp.loadPluginData(villagerTag);
            }
        } catch (CommandSyntaxException exception) {
            exception.printStackTrace();
        }
    }

    @Override
    public UUID getPartnerUUIDFromPlayerNBT(File file) {
        try (FileInputStream stream = new FileInputStream(file)) {
            CompoundTag tag = NbtIo.readCompressed(stream);

            CompoundTag bukkit = getOrCreateBukkitTag(tag);
            if (bukkit.hasUUID(plugin.getMarriedWith().toString())) {
                return bukkit.getUUID(plugin.getMarriedWith().toString());
            } else {
                return null;
            }
        } catch (IOException exception) {
            return null;
        }
    }

    @Override
    public void removePartnerFromPlayerNBT(File file) {
        try (FileInputStream stream = new FileInputStream(file)) {
            CompoundTag tag = NbtIo.readCompressed(stream);

            CompoundTag bukkit = getOrCreateBukkitTag(tag);
            bukkit.remove(plugin.getMarriedWith().toString());

            if (bukkit.isEmpty()) {
                tag.remove("BukkitValues");
            } else {
                tag.put("BukkitValues", bukkit);
            }

            NbtIo.writeCompressed(tag, file);
        } catch (IOException exception) {
            plugin.getLogger().warning("Error loading player data!");
        }
    }

    @Override
    public void loadData() {
        File root = plugin.getServer().getWorldContainer();

        File[] worlds = root.listFiles(file -> {
            String[] files;
            return file.isDirectory()
                    && (files = file.list()) != null
                    && ArrayUtils.contains(files, "level.dat");
        });

        if (worlds == null) return;

        for (File world : worlds) {
            File entitiesFolder = new File(world, "entities");

            File[] entitiesFiles = entitiesFolder.listFiles(DATA_FILE_FILTER);
            if (entitiesFiles == null) continue;

            // Iterate through all .mca files in "entities" folder.
            for (File entityFile : entitiesFiles) {
                checkMCAFile(entityFile);
            }
        }
    }

    @Override
    public Raid getRaidAt(@NotNull Location location) {
        if (location.getWorld() == null) return null;

        BlockPos pos = BlockPos.containing(location.getX(), location.getY(), location.getZ());
        net.minecraft.world.entity.raid.Raid raid = ((CraftWorld) location.getWorld()).getHandle().getRaidAt(pos);

        return raid != null ? new CraftRaid(raid) : null;
    }

    public static void updateTamedData(@NotNull RealisticVillagers plugin, CompoundTag tag, LivingEntity living, boolean tamedByVillager) {
        CompoundTag bukkit = NMSConverter.getOrCreateBukkitTag(tag);

        NamespacedKey byVillager = plugin.getTamedByVillagerKey();
        bukkit.putBoolean(byVillager.toString(), tamedByVillager);

        // Remove previous key, not used anymore.
        bukkit.remove(plugin.getTamedByPlayerKey().toString());

        tag.put("BukkitValues", bukkit);

        updateBukkitValues(tag, byVillager.getNamespace(), living);
    }

    @Override
    public GameProfile getPlayerProfile(Player player) {
        return ((CraftPlayer) player).getHandle().getGameProfile();
    }

    @Override
    public void refreshSchedules() {
        refreshSchedule("baby");
        refreshSchedule("default");
    }

    @Override
    public IVillagerNPC getNPCFromTag(String tag) {
        try {
            return OfflineVillagerNPC.from(TagParser.parseTag(tag));
        } catch (CommandSyntaxException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    @Override
    public void spawnFromTag(@NotNull Location location, String tag) {
        Preconditions.checkArgument(location.getWorld() != null && !tag.isEmpty(), "Either world is null or tag is empty!");

        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();
        VillagerNPC villager = new VillagerNPC(EntityType.VILLAGER, level);

        loadDataFromTag(villager.getBukkitEntity(), tag);

        float health = (float) Math.min(villager.getMaxHealth(), Config.REVIVE_SPAWN_VALUES_HEALTH.asDouble());
        villager.setHealth(health);

        int foodLevel = Math.min(20, Config.REVIVE_SPAWN_VALUES_FOOD_LEVEL.asInt());
        villager.setFoodLevel(foodLevel);

        for (String effectString : Config.REVIVE_SPAWN_VALUES_POTION_EFFECTS.asStringList()) {
            if (Strings.isNullOrEmpty(effectString)) continue;
            String[] data = PluginUtils.splitData(effectString);

            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(data[0].toLowerCase(Locale.ROOT)));
            if (type != null) {
                // Default = 5 seconds, level 1 (amplifier 0).
                int duration = data.length > 1 ? PluginUtils.getRangedAmount(data[1]) : 100;
                int amplifier = data.length > 2 ? PluginUtils.getRangedAmount(data[2]) - 1 : 0;
                villager.getBukkitEntity().addPotionEffect(new PotionEffect(type, duration, amplifier));
            }
        }

        villager.moveTo(location.getX(), location.getY(), location.getZ(), location.getYaw(), 0.0f);
        villager.setRevivingTicks(60);

        level.addFreshEntity(villager, CreatureSpawnEvent.SpawnReason.DEFAULT);
    }

    @Override
    public void addGameRuleListener(World world) {
        try {
            GameRules.Key<GameRules.BooleanValue> rule = GameRules.RULE_MOBGRIEFING;

            String warn = "The rule {" + rule.getId() + "} has been disabled in the world {" + world.getName() + "}, this will not allow villagers to pick up items.";

            GameRules.BooleanValue nmsRule = ((CraftWorld) world).getHandle().getGameRules().getRule(rule);
            if (!nmsRule.get()) {
                plugin.getLogger().warning(warn);
            }

            Object type = RULE_TYPE.invoke(nmsRule);

            Reflection.setFieldUsingUnsafe(
                    RULE_CALLBACK,
                    type,
                    (BiConsumer<ServerLevel, GameRules.BooleanValue>) (level, value) -> {
                        if (value.get()) return;
                        plugin.getLogger().warning(warn);
                    }
            );
        } catch (Throwable exception) {
            exception.printStackTrace();
        }
    }

    private void refreshSchedule(@NotNull String name) {
        try {
            Schedule schedule = name.equals("baby") ? VillagerNPC.VILLAGER_BABY : VillagerNPC.VILLAGER_DEFAULT;

            ConfigurationSection section = plugin.getConfig().getConfigurationSection("schedules." + name);
            if (section == null) return;

            Map<Activity, Timeline> timelines = (Map<Activity, Timeline>) TIMELINES.invoke(schedule);
            timelines.clear();

            ScheduleBuilder builder = new ScheduleBuilder(schedule);
            for (int time : section.getKeys(false).stream().filter(NumberUtils::isCreatable).map(Integer::valueOf).sorted().toList()) {
                Activity activity = ACTIVITIES.get(plugin.getConfig().getString("schedules." + name + "." + time, "").toLowerCase(Locale.ROOT));
                if (activity != null) builder.changeActivityAt(time, activity);
            }

            builder.build();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private void checkMCAFile(@NotNull File entityFile) {
        try {
            RegionFile region = new RegionFile(entityFile.toPath(), entityFile.getParentFile().toPath(), false);
            String world = entityFile.getParentFile().getParentFile().getName();

            for (int x = 0; x < 32; x++) {
                for (int z = 0; z < 32; z++) {
                    checkRegion(region, world, x, z);
                }
            }

            region.close();
        } catch (IOException | IllegalArgumentException ignored) {
            // Invalid region file, caused by the server software in most of the cases; ignoring to prevent spam.
        }
    }

    private void checkRegion(@NotNull RegionFile region, String world, int x, int z) throws IOException {
        ChunkPos chunkPos = new ChunkPos(x, z);
        if (!region.hasChunk(chunkPos)) return;

        DataInputStream stream = region.getChunkDataInputStream(chunkPos);
        if (stream == null) return;

        CompoundTag chunkTag = NbtIo.read(stream);

        stream.close();

        for (Tag tag : chunkTag.getList("Entities", 10)) {
            if (!(tag instanceof CompoundTag compound)) continue;
            if (!compound.getString("id").equals("minecraft:villager")) continue;
            if (!compound.hasUUID("UUID")) continue;

            CompoundTag bukkit = getOrCreateBukkitTag(compound);
            CompoundTag data = bukkit.getCompound(plugin.getNpcValuesKey().toString());
            if (data.isEmpty()) continue;

            ListTag pos = compound.getList("Pos", 6);
            double xc = pos.getDouble(0);
            double yc = pos.getDouble(1);
            double zc = pos.getDouble(2);

            UUID uuid = compound.getUUID("UUID");

            Set<IVillagerNPC> offlines = plugin.getTracker().getOfflineVillagers();
            if (offlines.stream().noneMatch(npc -> npc.getUniqueId().equals(uuid))) {
                offlines.add(OfflineVillagerNPC.from(uuid, data, world, xc, yc, zc));
            }
        }
    }

    public static <T> void unfreezeRegistry(Registry<T> registry) {
        Preconditions.checkNotNull(INSTRUSIVE_HOLDER_CACHE);
        Preconditions.checkNotNull(FROZEN);

        try {
            INSTRUSIVE_HOLDER_CACHE.invoke(registry, null);
            FROZEN.invoke(registry, false);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static <T> T register(Registry<? super T> registry, String name, T value) {
        unfreezeRegistry(registry);
        try {
            return Registry.register(registry, new ResourceLocation(name), value);
        } finally {
            registry.freeze();
        }
    }

    @SuppressWarnings("unused")
    public static @NotNull ScheduleBuilder registerSchedule(String name) {
        Schedule schedule = register(BuiltInRegistries.SCHEDULE, name, new Schedule());
        return new ScheduleBuilder(schedule);
    }

    public static <U> MemoryModuleType<U> registerMemoryType(String name) {
        return registerMemoryType(name, null);
    }

    public static <U> @Nullable MemoryModuleType<U> registerMemoryType(String name, Codec<U> codec) {
        Preconditions.checkNotNull(MEMORY_MODULE_TYPE);
        try {
            return register(BuiltInRegistries.MEMORY_MODULE_TYPE, name, (MemoryModuleType<U>) MEMORY_MODULE_TYPE.invoke(Optional.ofNullable(codec)));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static <U extends Sensor<?>> @Nullable SensorType<U> registerSensor(String name, Supplier<U> sensor) {
        Preconditions.checkNotNull(SENSOR_TYPE);
        try {
            return register(BuiltInRegistries.SENSOR_TYPE, name, (SensorType<U>) SENSOR_TYPE.invoke(sensor));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static @Nullable Activity registerActivity(String name) {
        Preconditions.checkNotNull(ACTIVITY);
        try {
            return register(BuiltInRegistries.ACTIVITY, name, (Activity) ACTIVITY.invoke(name));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static void registerAttribute(LivingEntity entity, Attribute attribute, double value) {
        Preconditions.checkNotNull(ATTRIBUTES);
        try {
            Map<Attribute, AttributeInstance> attributes = (Map<Attribute, AttributeInstance>) ATTRIBUTES.invoke(entity.getAttributes());
            if (attributes == null) return;

            @SuppressWarnings("ResultOfMethodCallIgnored") Consumer<AttributeInstance> onDirty = AttributeInstance::getAttribute;
            attributes.put(attribute, new AttributeInstance(attribute, onDirty));

            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) instance.setBaseValue(value);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static CompoundTag getOrCreateBukkitTag(@NotNull CompoundTag base) {
        return base.get("BukkitValues") instanceof CompoundTag tag ? tag : new CompoundTag();
    }

    public static void updateBukkitValues(CompoundTag tag, String namespace, @NotNull LivingEntity living) {
        // Remove previous data associated from THIS plugin only in the container.
        PersistentDataContainer container = living.getBukkitEntity().getPersistentDataContainer();
        for (NamespacedKey key : container.getKeys()) {
            if (key.getNamespace().equalsIgnoreCase(namespace)) {
                container.remove(key);
            }
        }

        // Save data in craft entity to prevent data-loss.
        living.getBukkitEntity().readBukkitValues(tag);
    }

    public static void leaveVehicle(@NotNull Entity entity) {
        Entity vehicle = entity.getVehicle();
        if (vehicle != null
                && !(vehicle instanceof Boat)
                && !(vehicle instanceof Minecart)) {
            entity.stopRiding();
        }
    }
}