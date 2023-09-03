package me.matsubara.realisticvillagers.nms.v1_18_r2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_18_r2.PetCat;
import me.matsubara.realisticvillagers.entity.v1_18_r2.PetParrot;
import me.matsubara.realisticvillagers.entity.v1_18_r2.PetWolf;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.OfflineVillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_18_r2.villager.VillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Reflection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.nbt.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
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
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.entity.schedule.Timeline;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Raid;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_18_R2.CraftRaid;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftVillager;
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Supplier;

@SuppressWarnings("unchecked")
public class NMSConverter implements INMSConverter {

    private final RealisticVillagers plugin;

    // Registry fields.
    private static final MethodHandle INSTRUSIVE_HOLDER_CACHE = Reflection.getFieldSetter(MappedRegistry.class, "bN");
    private static final MethodHandle FROZEN = Reflection.getFieldSetter(MappedRegistry.class, "bL");
    private static final MethodHandle ATTRIBUTES = Reflection.getFieldGetter(AttributeMap.class, "b");

    // Constructors.
    private static final MethodHandle MEMORY_MODULE_TYPE = Reflection.getConstructor(MemoryModuleType.class, Optional.class);
    private static final MethodHandle SENSOR_TYPE = Reflection.getConstructor(SensorType.class, Supplier.class);
    private static final MethodHandle ACTIVITY = Reflection.getConstructor(Activity.class, String.class);

    // Other.
    private static final MethodHandle TRACKED_ENTITY_FIELD = Reflection.getFieldGetter(ChunkMap.TrackedEntity.class, "c");
    private static final MethodHandle TIMELINES = Reflection.getFieldGetter(Schedule.class, "g");

    private static final Random RANDOM = new Random();
    private static final Map<String, Activity> ACTIVITIES;

    static {
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
    public Optional<IVillagerNPC> getNPC(Villager villager) {
        return Optional.ofNullable(((CraftVillager) villager).getHandle() instanceof VillagerNPC npc ? npc : null);
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    @Override
    public void registerEntities() {
        try {
            // "factory" field.
            Field field = EntityType.class.getDeclaredField("bn");
            Reflection.setFieldUsingUnsafe(
                    field,
                    EntityType.VILLAGER,
                    (EntityType.EntityFactory<net.minecraft.world.entity.npc.Villager>) (type, level) -> {
                        if (level.getLevelData() instanceof PrimaryLevelData data && plugin.isEnabledIn(data.getLevelName())) {
                            return new VillagerNPC(EntityType.VILLAGER, level);
                        }
                        return new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER, level);
                    });
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
        if (entity instanceof Villager villager) {
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
    public boolean isSeekGoatHorn(ItemStack item) {
        return false;
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
            Villager bukkitMother = plugin.getUnloadedOffline(mother);

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
    public void loadDataFromTag(Villager villager, @NotNull String tag) {
        try {
            CompoundTag villagerTag = tag.isEmpty() ? new CompoundTag() : TagParser.parseTag(tag);

            Optional<IVillagerNPC> npc = getNPC(villager);
            if (npc.isEmpty()) return;

            ((VillagerNPC) npc.get()).loadPluginData(villagerTag);
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

            File[] entitiesFiles = entitiesFolder.listFiles(
                    (directory, name) -> new File(directory, name).isFile() && name.endsWith(".mca") && !name.contains("backup") && !name.contains("mcc"));
            if (entitiesFiles == null) continue;

            // Iterate through all .mca files in "entities" folder.
            for (File entityFile : entitiesFiles) {
                checkMCAFile(entityFile);
            }
        }
    }

    @Override
    public ItemStack randomVanillaEnchantments(Location location, ItemStack item) {
        if (item == null || item.getType().isAir()) return item;
        if (location.getWorld() == null) return item;

        float multiplier = ((CraftWorld) location.getWorld()).getHandle()
                .getCurrentDifficultyAt(new BlockPos(location.getX(), location.getY(), location.getZ()))
                .getSpecialMultiplier();

        double chance = ItemStackUtils.getSlotByItem(item) != null ? 0.5d : 0.25f;
        if (Math.random() > chance * multiplier) return item;

        return CraftItemStack.asBukkitCopy(EnchantmentHelper.enchantItem(
                RANDOM,
                CraftItemStack.asNMSCopy(item),
                (int) (5.0f + multiplier * (float) RANDOM.nextInt(18)),
                false));
    }

    @Override
    public Raid getRaidAt(@NotNull Location location) {
        if (location.getWorld() == null) return null;

        BlockPos pos = new BlockPos(location.getX(), location.getY(), location.getZ());
        net.minecraft.world.entity.raid.Raid raid = ((CraftWorld) location.getWorld()).getHandle().getRaidAt(pos);

        return raid != null ? new CraftRaid(raid) : null;
    }

    @SuppressWarnings("deprecation")
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

    @SuppressWarnings("WhileLoopReplaceableByForEach")
    @Override
    public boolean isBeingTracked(Player player, int villagerId) {
        ServerPlayer handle = ((CraftPlayer) player).getHandle();

        ObjectIterator<ChunkMap.TrackedEntity> entityIterator = handle.getLevel().getChunkSource().chunkMap.entityMap.values().iterator();
        while (entityIterator.hasNext()) {
            try {
                ChunkMap.TrackedEntity tracked = entityIterator.next();
                if (tracked == null) continue;

                Entity entity = (Entity) TRACKED_ENTITY_FIELD.invoke(tracked);
                if (entity.getId() != villagerId) continue;

                Iterator<ServerPlayerConnection> seenByIterator = tracked.seenBy.iterator();
                while (seenByIterator.hasNext()) {
                    ServerPlayerConnection connection = seenByIterator.next();
                    if (connection == null) continue;
                    if (connection.getPlayer().is(handle)) return true;
                }
            } catch (Throwable ignored) {

            }
        }
        return false;
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

            PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(data[0].toLowerCase()));
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

    private void refreshSchedule(@NotNull String name) {
        try {
            Schedule schedule = name.equals("baby") ? VillagerNPC.VILLAGER_BABY : VillagerNPC.VILLAGER_DEFAULT;

            ConfigurationSection section = plugin.getConfig().getConfigurationSection("schedules." + name);
            if (section == null) return;

            Map<Activity, Timeline> timelines = (Map<Activity, Timeline>) TIMELINES.invoke(schedule);
            timelines.clear();

            ScheduleBuilder builder = new ScheduleBuilder(schedule);
            for (int time : section.getKeys(false).stream().filter(NumberUtils::isCreatable).map(Integer::valueOf).sorted().toList()) {
                Activity activity = ACTIVITIES.get(plugin.getConfig().getString("schedules." + name + "." + time, "").toLowerCase());
                if (activity != null) builder.changeActivityAt(time, activity);
            }

            builder.build();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    private void sendPacket(ServerPlayer player, Packet<?> @NotNull ... packets) {
        for (Packet<?> packet : packets) {
            player.connection.send(packet);
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
            INSTRUSIVE_HOLDER_CACHE.invoke(registry, new IdentityHashMap<>());
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
        Schedule schedule = register(Registry.SCHEDULE, name, new Schedule());
        return new ScheduleBuilder(schedule);
    }

    public static <U> MemoryModuleType<U> registerMemoryType(String name) {
        return registerMemoryType(name, null);
    }

    public static <U> @Nullable MemoryModuleType<U> registerMemoryType(String name, Codec<U> codec) {
        Preconditions.checkNotNull(MEMORY_MODULE_TYPE);
        try {
            return register(Registry.MEMORY_MODULE_TYPE, name, (MemoryModuleType<U>) MEMORY_MODULE_TYPE.invoke(Optional.ofNullable(codec)));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static <U extends Sensor<?>> @Nullable SensorType<U> registerSensor(String name, Supplier<U> sensor) {
        Preconditions.checkNotNull(SENSOR_TYPE);
        try {
            return register(Registry.SENSOR_TYPE, name, (SensorType<U>) SENSOR_TYPE.invoke(sensor));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static @Nullable Activity registerActivity(String name) {
        Preconditions.checkNotNull(ACTIVITY);
        try {
            return register(Registry.ACTIVITY, name, (Activity) ACTIVITY.invoke(name));
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

            attributes.put(attribute, new AttributeInstance(attribute, AttributeInstance::getAttribute));

            AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) instance.setBaseValue(value);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static CompoundTag getOrCreateBukkitTag(@NotNull CompoundTag base) {
        return base.get("BukkitValues") instanceof CompoundTag tag ? tag : new CompoundTag();
    }

    @Override
    public PropertyMap changePlayerSkin(@NotNull Player player, String texture, String signature) {
        GameProfile profile = ((CraftPlayer) player).getProfile();

        // Keep copy of old properties.
        PropertyMap oldProperties = new PropertyMap();
        oldProperties.putAll("textures", profile.getProperties().get("textures"));

        profile.getProperties().removeAll("textures");
        profile.getProperties().put("textures", new Property("textures", texture, signature));

        ServerPlayer handle = ((CraftPlayer) player).getHandle();
        ServerLevel level = handle.getLevel();

        ClientboundRespawnPacket respawnPacket = new ClientboundRespawnPacket(
                level.dimensionTypeRegistration(),
                level.dimension(),
                BiomeManager.obfuscateSeed(level.getSeed()),
                handle.gameMode.getGameModeForPlayer(),
                handle.gameMode.getPreviousGameModeForPlayer(),
                level.isDebug(),
                level.isFlat(),
                true);

        Location location = player.getLocation();

        ClientboundPlayerPositionPacket positionPacket = new ClientboundPlayerPositionPacket(
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch(),
                new HashSet<>(),
                0,
                false);

        GameMode gameMode = player.getGameMode();
        boolean allowFlight = player.getAllowFlight();
        boolean flying = player.isFlying();
        int xpLevel = player.getLevel();
        float xpPoints = player.getExp();
        int heldSlot = player.getInventory().getHeldItemSlot();

        sendPacket(handle, new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.REMOVE_PLAYER, handle));
        sendPacket(handle, new ClientboundPlayerInfoPacket(ClientboundPlayerInfoPacket.Action.ADD_PLAYER, handle));
        sendPacket(handle, respawnPacket);
        sendPacket(handle, positionPacket);

        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(flying);
        player.teleport(location);
        player.updateInventory();
        player.setLevel(xpLevel);
        player.setExp(xpPoints);
        player.getInventory().setHeldItemSlot(heldSlot);

        ((CraftPlayer) player).updateScaledHealth();
        handle.onUpdateAbilities();
        handle.resetSentInfo();

        return oldProperties;
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
}