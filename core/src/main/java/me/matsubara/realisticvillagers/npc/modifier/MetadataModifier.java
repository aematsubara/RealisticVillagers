package me.matsubara.realisticvillagers.npc.modifier;

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

    private final List<EntityData> metadata = new ArrayList<>();

    public MetadataModifier(NPC npc) {
        super(npc);
    }

    public <I, O> MetadataModifier queue(@NotNull EntityMetadata<I, O> metadata, I value) {
        this.metadata.add(new EntityData(metadata.index(), metadata.outputType(), metadata.mapper().apply(value)));
        return this;
    }

    @Override
    public void send(@NotNull Iterable<? extends Player> players) {
        queueInstantly((npc, layer) -> new WrapperPlayServerEntityMetadata(npc.getEntityId(), metadata));
        super.send(players);
    }

    public void updateShoulderEntities() {
        IVillagerNPC npc = this.npc.getNpc();
        queue(MetadataModifier.EntityMetadata.SHOULDER_ENTITY_LEFT, npc.getShoulderEntityLeft()).send();
        queue(MetadataModifier.EntityMetadata.SHOULDER_ENTITY_RIGHT, npc.getShoulderEntityRight()).send();
    }

    public record EntityMetadata<I, O>(int index, EntityDataType<O> outputType, Function<I, O> mapper) {

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
                17,
                EntityDataTypes.BYTE,
                input -> input ? SkinSection.ALL.getMask() : 0);

        public static final EntityMetadata<Object, NBTCompound> SHOULDER_ENTITY_LEFT = new EntityMetadata<>(
                19,
                EntityDataTypes.NBT,
                SpigotReflectionUtil::fromMinecraftNBT);

        public static final EntityMetadata<Object, NBTCompound> SHOULDER_ENTITY_RIGHT = new EntityMetadata<>(
                20,
                EntityDataTypes.NBT,
                SpigotReflectionUtil::fromMinecraftNBT);

    }
}