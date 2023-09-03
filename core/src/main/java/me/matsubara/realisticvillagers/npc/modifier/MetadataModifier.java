package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.cryptomorin.xseries.ReflectionUtils;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class MetadataModifier extends NPCModifier {

    private final List<WrappedWatchableObject> metadata = new ArrayList<>();

    public MetadataModifier(NPC npc) {
        super(npc);
    }

    public <I, O> MetadataModifier queue(@NotNull EntityMetadata<I, O> metadata, I value) {
        if (!metadata.getAvailabilitySupplier().get()) return this;

        for (EntityMetadata<I, Object> relatedMetadata : metadata.getRelatedMetadata()) {
            if (relatedMetadata.getAvailabilitySupplier().get()) queue(
                    relatedMetadata.getIndex(),
                    relatedMetadata.getMapper().apply(value),
                    relatedMetadata.getOutputType());
        }
        return queue(metadata.getIndex(), metadata.getMapper().apply(value), metadata.getOutputType());
    }

    public <T> MetadataModifier queue(int index, T value, Class<T> clazz) {
        return queue(index, value, MINECRAFT_VERSION < 9 ? null : WrappedDataWatcher.Registry.get(clazz));
    }

    public <T> MetadataModifier queue(int index, T value, WrappedDataWatcher.Serializer serializer) {
        metadata.add(serializer == null ?
                new WrappedWatchableObject(index, value) :
                new WrappedWatchableObject(new WrappedDataWatcher.WrappedDataWatcherObject(index, serializer), value));
        return this;
    }

    @Override
    public void send(@NotNull Iterable<? extends Player> players) {
        queueInstantly((npc, layer) -> {
            PacketContainer container = new PacketContainer(Server.ENTITY_METADATA);
            container.getIntegers().write(0, npc.getEntityId());

            if (PluginUtils.IS_1_19_3_OR_NEW) {
                List<WrappedDataValue> wrappedDataValues = new ArrayList<>(metadata.size());
                for (WrappedWatchableObject object : metadata) {
                    if (object != null) wrappedDataValues.add(new WrappedDataValue(
                            object.getIndex(),
                            object.getWatcherObject().getSerializer(),
                            object.getRawValue()));
                }
                container.getDataValueCollectionModifier().write(0, wrappedDataValues);
            } else {
                container.getWatchableCollectionModifier().write(0, metadata);
            }

            return container;
        });

        super.send(players);
    }

    @SuppressWarnings("unchecked")
    public static class EntityMetadata<I, O> {

        private static final Class<?> COMPOUND_TAG_CLASS = ReflectionUtils.getNMSClass("nbt", "NBTTagCompound");

        @SuppressWarnings("unused")
        public static final EntityMetadata<Boolean, Byte> SNEAKING = new EntityMetadata<>(
                0,
                Byte.class,
                Collections.emptyList(),
                input -> (byte) (input ? 0x02 : 0),
                // With 1.16+, we have to change the pose too to make the NPC sneak.
                new EntityMetadata<>(
                        6,
                        (Class<Object>) EnumWrappers.getEntityPoseClass(),
                        Collections.emptyList(),
                        input -> (input ? EnumWrappers.EntityPose.CROUCHING : EnumWrappers.EntityPose.STANDING).toNms(),
                        () -> MINECRAFT_VERSION >= 14));

        public static final EntityMetadata<Boolean, Byte> SKIN_LAYERS = new EntityMetadata<>(
                10,
                Byte.class,
                Arrays.asList(9, 9, 10, 14, 14, 15, 17),
                input -> (byte) (input ? 126 : 0));

        public static final EntityMetadata<EnumWrappers.EntityPose, Object> POSE = new EntityMetadata<>(
                6,
                (Class<Object>) EnumWrappers.getEntityPoseClass(),
                Collections.emptyList(),
                EnumWrappers.EntityPose::toNms,
                () -> MINECRAFT_VERSION >= 14);

        public static final EntityMetadata<Object, Object> SHOULDER_ENTITY_LEFT = new EntityMetadata<>(
                19,
                (Class<Object>) COMPOUND_TAG_CLASS,
                Collections.emptyList(),
                object -> object,
                () -> MINECRAFT_VERSION >= 18);

        public static final EntityMetadata<Object, Object> SHOULDER_ENTITY_RIGHT = new EntityMetadata<>(
                20,
                (Class<Object>) COMPOUND_TAG_CLASS,
                Collections.emptyList(),
                object -> object,
                () -> MINECRAFT_VERSION >= 18);

        private final int baseIndex;
        private final Class<O> outputType;
        private final Function<I, O> mapper;
        private final Collection<Integer> shiftVersions;
        private final Supplier<Boolean> availabilitySupplier;
        private final Collection<EntityMetadata<I, Object>> relatedMetadata;

        public EntityMetadata(int baseIndex,
                              Class<O> outputType,
                              Collection<Integer> shiftVersions,
                              Function<I, O> mapper,
                              Supplier<Boolean> availabilitySupplier,
                              EntityMetadata<I, Object>... relatedMetadata) {
            this.baseIndex = baseIndex;
            this.outputType = outputType;
            this.shiftVersions = shiftVersions;
            this.mapper = mapper;
            this.availabilitySupplier = availabilitySupplier;
            this.relatedMetadata = Arrays.asList(relatedMetadata);
        }

        public EntityMetadata(int baseIndex, Class<O> outputType, Collection<Integer> shiftVersions, Function<I, O> mapper, EntityMetadata<I, Object>... relatedMetadata) {
            this(baseIndex, outputType, shiftVersions, mapper, () -> true, relatedMetadata);
        }

        public int getIndex() {
            return baseIndex + Math.toIntExact(shiftVersions.stream().filter(minor -> MINECRAFT_VERSION >= minor).count());
        }

        public Class<O> getOutputType() {
            return outputType;
        }

        public Function<I, O> getMapper() {
            return mapper;
        }

        public Supplier<Boolean> getAvailabilitySupplier() {
            return availabilitySupplier;
        }

        public Collection<EntityMetadata<I, Object>> getRelatedMetadata() {
            return relatedMetadata;
        }
    }
}