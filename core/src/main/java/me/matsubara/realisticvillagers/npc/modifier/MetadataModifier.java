package me.matsubara.realisticvillagers.npc.modifier;

import com.comphenix.protocol.PacketType.Play.Server;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.*;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class MetadataModifier extends NPCModifier {

    private final List<WrappedWatchableObject> metadata = new ArrayList<>();

    public MetadataModifier(NPC npc) {
        super(npc);
    }

    public <I, O> MetadataModifier queue(@NotNull EntityMetadata<I, O> metadata, I value) {
        return queue(metadata, null, value);
    }

    public <I, O> MetadataModifier queue(@NotNull EntityMetadata<I, O> metadata, @Nullable WrappedDataWatcher.Serializer serializer, I value) {
        int index = metadata.index();
        O object = metadata.mapper().apply(value);
        return serializer != null ? queue(index, object, serializer) : queue(index, object, metadata.outputType());
    }

    public <T> MetadataModifier queue(int index, T value, Class<T> clazz) {
        return queue(index, value, WrappedDataWatcher.Registry.get(clazz));
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
    public record EntityMetadata<I, O>(int index, Class<O> outputType, Function<I, O> mapper) {

        public static @NotNull EntityMetadata<Byte, Byte> ENTITY_DATA = new EntityMetadata<>(
                0,
                Byte.class,
                input -> input);

        public static final EntityMetadata<EnumWrappers.EntityPose, Object> POSE = new EntityMetadata<>(
                6,
                (Class<Object>) EnumWrappers.getEntityPoseClass(),
                EnumWrappers.EntityPose::toNms);

        public static EntityMetadata<Integer, Integer> TICKS_FROZEN = new EntityMetadata<>(
                7,
                Integer.class,
                integer -> integer);

        public static EntityMetadata<Byte, Byte> HAND_DATA = new EntityMetadata<>(
                8,
                Byte.class,
                input -> input);

        public static EntityMetadata<Integer, Integer> EFFECT_COLOR = new EntityMetadata<>(
                10,
                Integer.class,
                integer -> integer);

        public static EntityMetadata<Boolean, Boolean> EFFECT_AMBIENCE = new EntityMetadata<>(
                11,
                Boolean.class,
                bool -> bool);

        public static EntityMetadata<Integer, Integer> ARROW_COUNT = new EntityMetadata<>(
                12,
                Integer.class,
                integer -> integer);

        public static EntityMetadata<Integer, Integer> BEE_STINGER = new EntityMetadata<>(
                13,
                Integer.class,
                integer -> integer);

        @SuppressWarnings("rawtypes")
        public static EntityMetadata<BlockPosition, Optional> BED_POS = new EntityMetadata<>(
                14,
                Optional.class,
                position -> Optional.of(BlockPosition.getConverter().getGeneric(position)));

        public static final EntityMetadata<Boolean, Byte> SKIN_LAYERS = new EntityMetadata<>(
                17,
                Byte.class,
                input -> (byte) (input ? 126 : 0));

        public static final EntityMetadata<Object, Object> SHOULDER_ENTITY_LEFT = new EntityMetadata<>(
                19,
                (Class<Object>) MinecraftReflection.getNBTCompoundClass(),
                object -> object);

        public static final EntityMetadata<Object, Object> SHOULDER_ENTITY_RIGHT = new EntityMetadata<>(
                20,
                (Class<Object>) MinecraftReflection.getNBTCompoundClass(),
                object -> object);

    }
}