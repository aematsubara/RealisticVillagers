package me.matsubara.realisticvillagers.nms.v1_18_r2;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.apache.commons.lang.ArrayUtils;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Raid;
import org.bukkit.craftbukkit.v1_18_R2.CraftRaid;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftVillager;
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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

    private static final Random RANDOM = new Random();

    public NMSConverter(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<IVillagerNPC> getNPC(Villager villager) {
        return Optional.ofNullable(((CraftVillager) villager).getHandle() instanceof VillagerNPC npc ? npc : null);
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    @Override
    public void registerEntity() {
        try {
            // "factory" field.
            Field field = EntityType.class.getDeclaredField("bn");
            Reflection.setFieldUsingUnsafe(
                    field,
                    EntityType.VILLAGER,
                    (EntityType.EntityFactory<net.minecraft.world.entity.npc.Villager>) (type, level) -> {
                        if (PluginUtils.spawnCustom() && level.getWorld() != null && plugin.isEnabledIn(level.getWorld())) {
                            return new VillagerNPC(EntityType.VILLAGER, level);
                        } else {
                            return new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER, level);
                        }
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
    public void createBaby(Location location, String name, String sex, UUID motherUUID, Player father) {
        Preconditions.checkNotNull(location.getWorld());
        ServerLevel level = ((CraftWorld) location.getWorld()).getHandle();

        VillagerNPC baby = new VillagerNPC(
                EntityType.VILLAGER,
                level,
                VillagerType.byBiome(level.getBiome(((CraftBlock) location.getBlock()).getPosition())));
        baby.finalizeSpawn(level, level.getCurrentDifficultyAt(baby.blockPosition()), MobSpawnType.BREEDING, null, null);

        baby.loadPluginData(new CompoundTag());
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
    public void loadDataFromTag(Villager villager, String tag) {
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

            File[] entitiesFiles = entitiesFolder.listFiles();
            if (entitiesFiles == null) continue;

            // Iterate through all .mca files in "entities" folder.
            for (File entityFile : entitiesFiles) {
                try {
                    checkMCAFile(entityFile);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
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
    public Raid getRaidAt(Location location) {
        if (location.getWorld() == null) return null;

        BlockPos pos = new BlockPos(location.getX(), location.getY(), location.getZ());
        net.minecraft.world.entity.raid.Raid raid = ((CraftWorld) location.getWorld()).getHandle().getRaidAt(pos);

        return raid != null ? new CraftRaid(raid) : null;
    }

    private void checkMCAFile(File entityFile) throws IOException {
        RegionFile region = new RegionFile(entityFile.toPath(), entityFile.getParentFile().toPath(), false);
        String world = entityFile.getParentFile().getParentFile().getName();

        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                checkRegion(region, world, x, z);
            }
        }

        region.close();
    }

    private void checkRegion(RegionFile region, String world, int x, int z) throws IOException {
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
            OfflineVillagerNPC npc = OfflineVillagerNPC.from(uuid, data, world, xc, yc, zc);
            plugin.getTracker().getOfflineVillagers().add(npc);
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
    public static ScheduleBuilder registerSchedule(String name) {
        Schedule schedule = register(Registry.SCHEDULE, name, new Schedule());
        return new ScheduleBuilder(schedule);
    }

    @SuppressWarnings("unused")
    public static <U> MemoryModuleType<U> registerMemoryType(String name) {
        return registerMemoryType(name, null);
    }

    public static <U> MemoryModuleType<U> registerMemoryType(String name, Codec<U> codec) {
        Preconditions.checkNotNull(MEMORY_MODULE_TYPE);
        try {
            return register(Registry.MEMORY_MODULE_TYPE, name, (MemoryModuleType<U>) MEMORY_MODULE_TYPE.invoke(Optional.ofNullable(codec)));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static <U extends Sensor<?>> SensorType<U> registerSensor(String name, Supplier<U> sensor) {
        Preconditions.checkNotNull(SENSOR_TYPE);
        try {
            return register(Registry.SENSOR_TYPE, name, (SensorType<U>) SENSOR_TYPE.invoke(sensor));
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return null;
        }
    }

    public static Activity registerActivity(String name) {
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

    public static CompoundTag getOrCreateBukkitTag(CompoundTag base) {
        return base.get("BukkitValues") instanceof CompoundTag tag ? tag : new CompoundTag();
    }

    public static void updateTamedData(CompoundTag tag, NamespacedKey key, LivingEntity living, boolean tamedByPlayer) {
        CompoundTag bukkit = NMSConverter.getOrCreateBukkitTag(tag);
        bukkit.putBoolean(key.toString(), tamedByPlayer);

        tag.put("BukkitValues", bukkit);

        updateBukkitValues(tag, key.getNamespace(), living);
    }

    public static void updateBukkitValues(CompoundTag tag, String namespace, LivingEntity living) {
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