package me.matsubara.realisticvillagers.nms.v1_18_r2;

import com.google.common.base.Preconditions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.entity.v1_18_r2.VillagerNPC;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.util.Reflection;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R2.block.CraftBlock;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftVillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@SuppressWarnings({"ClassCanBeRecord", "unchecked"})
public class NMSConverter implements INMSConverter {

    private final RealisticVillagers plugin;

    // Registry fields.
    private final static MethodHandle INSTRUSIVE_HOLDER_CACHE = Reflection.getFieldSetter(MappedRegistry.class, "bN");
    private final static MethodHandle FROZEN = Reflection.getFieldSetter(MappedRegistry.class, "bL");
    private final static MethodHandle ATTRIBUTES = Reflection.getFieldGetter(AttributeMap.class, "b");

    // Constructors.
    private final static MethodHandle MEMORY_MODULE_TYPE = Reflection.getConstructor(MemoryModuleType.class, Optional.class);
    private final static MethodHandle SENSOR_TYPE = Reflection.getConstructor(SensorType.class, Supplier.class);
    private final static MethodHandle ACTIVITY = Reflection.getConstructor(Activity.class, String.class);

    public NMSConverter(RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @Override
    public Optional<IVillagerNPC> getNPC(Villager villager) {
        return Optional.ofNullable(((CraftVillager) villager).getHandle() instanceof VillagerNPC npc ? npc : null);
    }

    @Override
    public void registerEntity() {
        try {
            // "factory" field.
            Field field = EntityType.VILLAGER.getClass().getDeclaredField("bn");
            Reflection.setFieldUsingUnsafe(field, EntityType.VILLAGER, (EntityType.EntityFactory<Entity>) (type, level) -> {
                if (plugin.isEnabledIn(level.getWorld())) {
                    return new VillagerNPC(EntityType.VILLAGER, level);
                } else {
                    return new net.minecraft.world.entity.npc.Villager(EntityType.VILLAGER, level);
                }
            });
        } catch (ReflectiveOperationException exception) {
            exception.printStackTrace();
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public String getNPCTag(org.bukkit.entity.LivingEntity entity) {
        if (entity instanceof Villager villager) {
            CompoundTag tag = new CompoundTag();

            Optional<IVillagerNPC> npc = getNPC(villager);
            if (npc.isEmpty()) return null;

            ((VillagerNPC) npc.get()).savePluginData(tag);
            return tag.get(plugin.getValuesKey().toString()).toString();
        } else if (entity instanceof ZombieVillager) {
            PersistentDataContainer container = entity.getPersistentDataContainer();
            return container.get(plugin.getZombieTransformKey(), PersistentDataType.STRING);
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

        Optional<Entity> optional;
        optional = Optional.ofNullable(level.getEntity(motherUUID));

        if (optional.isPresent()) {
            if (optional.get().isAlive() && optional.get() instanceof VillagerNPC mother) {
                mother.setAge(6000);
                mother.getChildrens().add(baby.getUUID());
                baby.setMother(mother.getUUID());
            }
        }

        baby.setFather(father.getUniqueId());
        baby.setFatherVillager(false);

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
}