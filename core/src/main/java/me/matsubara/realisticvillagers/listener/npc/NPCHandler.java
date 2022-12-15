package me.matsubara.realisticvillagers.listener.npc;

import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.npc.NPC;
import me.matsubara.realisticvillagers.util.npc.SpawnCustomizer;
import me.matsubara.realisticvillagers.util.npc.modifier.MetadataModifier;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class NPCHandler implements SpawnCustomizer {

    private final RealisticVillagers plugin;
    private final IVillagerNPC villager;

    private NbtCompound shoulderEntityLeft;
    private NbtCompound shoulderEntityRight;

    public NPCHandler(RealisticVillagers plugin, Villager villager) {
        this.plugin = plugin;
        this.villager = plugin.getConverter().getNPC(villager).orElse(null);
    }

    @Override
    public void handleSpawn(@NotNull NPC npc, @NotNull Player player) {
        Location location = villager.bukkit().getLocation();

        npc.rotation().queueRotate(location.getYaw(), location.getPitch()).send(player);

        MetadataModifier metadata = npc.metadata();
        metadata.queue(MetadataModifier.EntityMetadata.SKIN_LAYERS, true).send(player);
        metadata.queue(MetadataModifier.EntityMetadata.SHOULDER_ENTITY_LEFT, villager.getShoulderEntityLeft()).send(player);
        metadata.queue(MetadataModifier.EntityMetadata.SHOULDER_ENTITY_RIGHT, villager.getShoulderEntityRight()).send(player);

        if (villager.bukkit().isSleeping()) {
            Location home = villager.bukkit().getMemory(MemoryKey.HOME);

            Set<UUID> sleeping = plugin.getTracker().getHandler().getSleeping();
            sleeping.add(player.getUniqueId());

            npc.teleport().queueTeleport(location, villager.bukkit().isOnGround()).send(player);
            metadata.queue(MetadataModifier.EntityMetadata.POSE, EnumWrappers.EntityPose.SLEEPING).send(player);

            villager.bukkit().wakeup();

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (home != null) villager.bukkit().sleep(home);
                sleeping.remove(player.getUniqueId());
            }, 2L);
        }

        EntityEquipment equipment = villager.bukkit().getEquipment();
        if (equipment == null) return;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            npc.equipment().queue(slotToWrapper(slot), equipment.getItem(slot)).send(player);
        }
    }

    private EnumWrappers.ItemSlot slotToWrapper(EquipmentSlot slot) {
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