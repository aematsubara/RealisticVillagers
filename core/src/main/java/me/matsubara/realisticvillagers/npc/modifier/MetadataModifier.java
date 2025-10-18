package me.matsubara.realisticvillagers.npc.modifier;

import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.player.SkinSection;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

public class MetadataModifier extends NPCModifier {

    private final List<EntityData<?>> metadata = new ArrayList<>();

    public MetadataModifier(NPC npc) {
        super(npc);
    }

    public <I, O> MetadataModifier queue(@NotNull EntityMetadata<I, O> metadata, I value) {
        this.metadata.add(new EntityData<>(metadata.index(), metadata.outputType(), metadata.mapper().apply(value)));
        return this;
    }

    @Override
    public void send(@NotNull Iterable<? extends Player> players) {
        queueInstantly((npc, layer) -> new WrapperPlayServerEntityMetadata(npc.getEntityId(), metadata));
        super.send(players);
    }

    @SuppressWarnings("UnusedReturnValue")
    public @NotNull MetadataModifier queueShoulderEntity(boolean left, Object entity) {
        if (XReflection.supports(21, 9)) {
            // Since 1.21.9 we just have to send the variant id.
            queue(left ?
                    EntityMetadata.SHOULDER_ENTITY_LEFT_INT :
                    EntityMetadata.SHOULDER_ENTITY_RIGHT_INT, entity);
            return this;
        }

        // Before 1.21.9 we need to send a compound tag.
        queue(left ?
                EntityMetadata.SHOULDER_ENTITY_LEFT_NBT :
                EntityMetadata.SHOULDER_ENTITY_RIGHT_NBT, entity);
        return this;
    }

    public void updateShoulderEntities() {
        IVillagerNPC npc = this.npc.getNpc();
        queueShoulderEntity(true, npc.getShoulderEntityLeft()).send();
        queueShoulderEntity(false, npc.getShoulderEntityRight()).send();
    }

    public record EntityMetadata<I, O>(int index, EntityDataType<O> outputType, Function<I, O> mapper) {

        private static final byte ALL_BUT_CAPE = SkinSection.JACKET // Cape is excluded.
                .combine(SkinSection.LEFT_SLEEVE)
                .combine(SkinSection.RIGHT_SLEEVE)
                .combine(SkinSection.LEFT_PANTS)
                .combine(SkinSection.RIGHT_PANTS)
                .combine(SkinSection.HAT)
                .getMask();

        public static @NotNull EntityMetadata<Byte, Byte> ENTITY_DATA = new EntityMetadata<>(
                0,
                EntityDataTypes.BYTE,
                input -> input);

        public static final EntityMetadata<EntityPose, EntityPose> POSE = new EntityMetadata<>(
                6,
                EntityDataTypes.ENTITY_POSE,
                pose -> pose);

        public static EntityMetadata<Integer, Integer> TICKS_FROZEN = new EntityMetadata<>(
                7,
                EntityDataTypes.INT,
                integer -> integer);

        public static EntityMetadata<Byte, Byte> HAND_DATA = new EntityMetadata<>(
                8,
                EntityDataTypes.BYTE,
                input -> input);

        public static EntityMetadata<Integer, Integer> EFFECT_COLOR = new EntityMetadata<>(
                10,
                EntityDataTypes.INT,
                integer -> integer);

        public static EntityMetadata<Boolean, Boolean> EFFECT_AMBIENCE = new EntityMetadata<>(
                11,
                EntityDataTypes.BOOLEAN,
                bool -> bool);

        public static EntityMetadata<Integer, Integer> ARROW_COUNT = new EntityMetadata<>(
                12,
                EntityDataTypes.INT,
                integer -> integer);

        public static EntityMetadata<Integer, Integer> BEE_STINGER = new EntityMetadata<>(
                13,
                EntityDataTypes.INT,
                integer -> integer);

        public static EntityMetadata<Vector3i, Optional<Vector3i>> BED_POS = new EntityMetadata<>(
                14,
                EntityDataTypes.OPTIONAL_BLOCK_POSITION,
                Optional::of);

        public static final EntityMetadata<Boolean, Byte> SKIN_LAYERS = new EntityMetadata<>(
                XReflection.supports(21, 9) ? 16 : 17,
                EntityDataTypes.BYTE,
                input -> input ? ALL_BUT_CAPE : 0);

        public static final EntityMetadata<Object, NBTCompound> SHOULDER_ENTITY_LEFT_NBT = new EntityMetadata<>(
                19,
                EntityDataTypes.NBT,
                SpigotReflectionUtil::fromMinecraftNBT);

        public static final EntityMetadata<Object, NBTCompound> SHOULDER_ENTITY_RIGHT_NBT = new EntityMetadata<>(
                20,
                EntityDataTypes.NBT,
                SpigotReflectionUtil::fromMinecraftNBT);

        private static final EntityMetadata<Object, Optional<Integer>> SHOULDER_ENTITY_LEFT_INT = new EntityMetadata<>(
                19,
                EntityDataTypes.OPTIONAL_INT,
                variant -> variant instanceof Integer integer ? Optional.of(integer) : Optional.empty());

        private static final EntityMetadata<Object, Optional<Integer>> SHOULDER_ENTITY_RIGHT_INT = new EntityMetadata<>(
                20,
                EntityDataTypes.OPTIONAL_INT,
                variant -> variant instanceof Integer integer ? Optional.of(integer) : Optional.empty());
    }
}