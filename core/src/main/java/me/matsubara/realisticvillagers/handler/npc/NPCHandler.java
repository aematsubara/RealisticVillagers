package me.matsubara.realisticvillagers.handler.npc;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BlockPosition;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.npc.SpawnCustomizer;
import me.matsubara.realisticvillagers.npc.modifier.MetadataModifier;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record NPCHandler(RealisticVillagers plugin) implements SpawnCustomizer {

    public NPCHandler(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleSpawn(@NotNull NPC npc, @NotNull Player player) {
        IVillagerNPC villager = npc.getVillager();

        LivingEntity bukkit = villager.bukkit();
        if (bukkit == null) return;

        Location location = bukkit.getLocation();
        npc.rotation().queueHeadRotation(location.getYaw()).send(player);

        MetadataModifier metadata = npc.metadata();

        byte data = 0;

        if (bukkit.isVisualFire() || bukkit.getFireTicks() > 0) {
            data |= 0x01;
        }

        Pose pose = bukkit.getPose();
        if (pose == Pose.SNEAKING) {
            data |= 0x02;
        }

        if (bukkit.isInvisible()) {
            data |= 0x20;
        }

        if (bukkit.isGlowing()) {
            data |= 0x40;
        }

        if (data != 0) {
            metadata.queue(MetadataModifier.EntityMetadata.ENTITY_DATA, data);
        }

        EnumWrappers.EntityPose poseWrapper;
        if (pose != Pose.STANDING && (poseWrapper = poseToWrapper(pose)) != null) {
            metadata.queue(MetadataModifier.EntityMetadata.POSE, poseWrapper);
        }

        int freezeTicks = bukkit.getFreezeTicks();
        if (freezeTicks != 0) {
            metadata.queue(MetadataModifier.EntityMetadata.TICKS_FROZEN, freezeTicks);
        }

        byte handData = villager.getHandData();
        if (handData != 0) {
            metadata.queue(MetadataModifier.EntityMetadata.HAND_DATA, handData);
        }

        int effectColor = villager.getEffectColor();
        if (effectColor != 0) {
            metadata.queue(MetadataModifier.EntityMetadata.EFFECT_COLOR, effectColor);
        }

        if (villager.getEffectAmbience()) {
            metadata.queue(MetadataModifier.EntityMetadata.EFFECT_AMBIENCE, true);
        }

        int arrowsInBody = bukkit.getArrowsInBody();
        if (arrowsInBody != 0) {
            metadata.queue(MetadataModifier.EntityMetadata.ARROW_COUNT, arrowsInBody);
        }

        int beeStingers = villager.getBeeStingers();
        if (beeStingers != 0) {
            metadata.queue(MetadataModifier.EntityMetadata.BEE_STINGER, beeStingers);
        }

        Location home;
        if (bukkit.isSleeping() && bukkit instanceof Villager && (home = bukkit.getMemory(MemoryKey.HOME)) != null) {
            metadata.queue(
                    MetadataModifier.EntityMetadata.BED_POS,
                    WrappedDataWatcher.Registry.getBlockPositionSerializer(true),
                    new BlockPosition(home.toVector()));
        }

        metadata.queue(MetadataModifier.EntityMetadata.SKIN_LAYERS, true).send(player);

        if (villager.validShoulderEntityLeft()) {
            metadata.queue(MetadataModifier.EntityMetadata.SHOULDER_ENTITY_LEFT, villager.getShoulderEntityLeft());
        }

        if (villager.validShoulderEntityRight()) {
            metadata.queue(MetadataModifier.EntityMetadata.SHOULDER_ENTITY_RIGHT, villager.getShoulderEntityRight());
        }

        metadata.send(player);

        // Mount vehicles.
        if (bukkit.getVehicle() instanceof Vehicle vehicle) {
            PacketContainer mount = new PacketContainer(PacketType.Play.Server.MOUNT);
            mount.getIntegers().write(0, vehicle.getEntityId());
            mount.getIntegerArrays().write(0, vehicle.getPassengers().stream().mapToInt(Entity::getEntityId).toArray());
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, mount);
        }

        EntityEquipment equipment = bukkit.getEquipment();
        if (equipment == null) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            npc.equipment().queue(slotToWrapper(slot), equipment.getItem(slot)).send(player);
        }
    }

    @Contract(pure = true)
    private EnumWrappers.@Nullable EntityPose poseToWrapper(@NotNull Pose pose) {
        if (pose == Pose.SNEAKING) return EnumWrappers.EntityPose.CROUCHING;
        return PluginUtils.getOrNull(EnumWrappers.EntityPose.class, pose.name());
    }

    @Contract(pure = true)
    private EnumWrappers.ItemSlot slotToWrapper(@NotNull EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> EnumWrappers.ItemSlot.HEAD;
            case CHEST -> EnumWrappers.ItemSlot.CHEST;
            case LEGS -> EnumWrappers.ItemSlot.LEGS;
            case FEET -> EnumWrappers.ItemSlot.FEET;
            case HAND -> EnumWrappers.ItemSlot.MAINHAND;
            case OFF_HAND -> EnumWrappers.ItemSlot.OFFHAND;
        };
    }
}