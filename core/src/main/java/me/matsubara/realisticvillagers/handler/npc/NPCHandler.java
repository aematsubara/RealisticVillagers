package me.matsubara.realisticvillagers.handler.npc;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.protocol.ProtocolManager;
import com.github.retrooper.packetevents.protocol.attribute.Attributes;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
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

import java.util.Collections;
import java.util.List;

public record NPCHandler(RealisticVillagers plugin) implements SpawnCustomizer {

    public NPCHandler(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handleSpawn(@NotNull NPC npc, @NotNull Player player) {
        IVillagerNPC villager = npc.getNpc();

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

        EntityPose poseWrapper;
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
            metadata.queue(MetadataModifier.EntityMetadata.BED_POS, new Vector3i(home.getBlockX(), home.getBlockY(), home.getBlockZ()));
        }

        metadata.queue(MetadataModifier.EntityMetadata.SKIN_LAYERS, true).send(player);

        if (villager.validShoulderEntityLeft()) {
            metadata.queueShoulderEntity(true, villager.getShoulderEntityLeft());
        }

        if (villager.validShoulderEntityRight()) {
            metadata.queueShoulderEntity(false, villager.getShoulderEntityRight());
        }

        metadata.send(player);

        ProtocolManager manager = PacketEvents.getAPI().getProtocolManager();
        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);

        // Mount vehicles.
        if (bukkit.getVehicle() instanceof Vehicle vehicle) {
            int[] passengers = vehicle.getPassengers().stream().mapToInt(Entity::getEntityId).toArray();
            WrapperPlayServerSetPassengers wrapper = new WrapperPlayServerSetPassengers(vehicle.getEntityId(), passengers);
            manager.sendPacket(channel, wrapper);
        }

        EntityEquipment equipment = bukkit.getEquipment();
        if (equipment == null) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            com.github.retrooper.packetevents.protocol.player.EquipmentSlot itemSlot = slotToWrapper(slot);
            if (itemSlot == null) continue;

            npc.equipment().queue(itemSlot, equipment.getItem(slot)).send(player);
        }

        adaptScale(player, npc);
    }

    public void adaptScale(Player player, @NotNull NPC npc) {
        if (!(npc.getNpc().bukkit() instanceof Villager villager)) return;

        WrapperPlayServerUpdateAttributes wrapper = new WrapperPlayServerUpdateAttributes(npc.getEntityId(), List.of(
                new WrapperPlayServerUpdateAttributes.Property(Attributes.GENERIC_SCALE, villager.isAdult() ? 1.0d : 0.5d, Collections.emptyList())));

        Object channel = PacketEvents.getAPI().getPlayerManager().getChannel(player);
        PacketEvents.getAPI().getProtocolManager().sendPacket(channel, wrapper);
    }

    @Contract(pure = true)
    private @Nullable EntityPose poseToWrapper(@NotNull Pose pose) {
        if (pose == Pose.SNEAKING) return EntityPose.CROUCHING;
        return PluginUtils.getOrNull(EntityPose.class, pose.name());
    }

    @SuppressWarnings("UnnecessaryDefault")
    @Contract(pure = true)
    private @Nullable com.github.retrooper.packetevents.protocol.player.EquipmentSlot slotToWrapper(@NotNull EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.HELMET;
            case CHEST -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.CHEST_PLATE;
            case LEGS -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.LEGGINGS;
            case FEET -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BOOTS;
            case HAND -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.MAIN_HAND;
            case OFF_HAND -> com.github.retrooper.packetevents.protocol.player.EquipmentSlot.OFF_HAND;
            default -> null; // We need to keep this for EquipmentSlot#BODY.
        };
    }
}