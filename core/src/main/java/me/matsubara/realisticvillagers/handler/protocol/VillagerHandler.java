package me.matsubara.realisticvillagers.handler.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.google.common.collect.Sets;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.util.ReflectionUtils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.comphenix.protocol.PacketType.Play.Server.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class VillagerHandler extends PacketAdapter {

    private final RealisticVillagers plugin;
    private final @Getter Set<UUID> sleeping = ConcurrentHashMap.newKeySet();
    private final @Getter Set<UUID> allowSpawn = ConcurrentHashMap.newKeySet();

    public static final Set<PacketType> MOVEMENT_PACKETS = Sets.newHashSet(
            ENTITY_VELOCITY,
            REL_ENTITY_MOVE,
            ENTITY_LOOK,
            ENTITY_TELEPORT,
            ENTITY_HEAD_ROTATION,
            REL_ENTITY_MOVE_LOOK);

    public VillagerHandler(RealisticVillagers plugin) {
        super(
                plugin,
                ListenerPriority.HIGHEST,
                SPAWN_ENTITY,
                NAMED_ENTITY_SPAWN,
                ANIMATION,
                BLOCK_BREAK_ANIMATION,
                ENTITY_STATUS,
                REL_ENTITY_MOVE,
                REL_ENTITY_MOVE_LOOK,
                ENTITY_LOOK,
                ENTITY_HEAD_ROTATION,
                CAMERA,
                ENTITY_METADATA,
                ATTACH_ENTITY,
                ENTITY_VELOCITY,
                ENTITY_EQUIPMENT,
                MOUNT,
                ENTITY_SOUND,
                COLLECT,
                ENTITY_TELEPORT,
                UPDATE_ATTRIBUTES,
                ENTITY_EFFECT);
        this.plugin = plugin;
    }

    @Override
    public void onPacketSending(@NotNull PacketEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        World world;
        try {
            world = player.getWorld();
        } catch (UnsupportedOperationException exception) {
            // Should "fix" -> UnsupportedOperationException: The method getWorld is not supported for temporary players.
            return;
        }

        PacketContainer packet = event.getPacket();

        Entity entity = packet.getEntityModifier(world).readSafely(0);
        if (!(entity instanceof Villager villager) || plugin.getTracker().isInvalid(villager)) return;

        UUID uuid = entity.getUniqueId();

        PacketType type = event.getPacketType();
        if (isSpawnPacket(type)) {
            if (!allowSpawn.contains(uuid)) event.setCancelled(true);
            return;
        }

        StructureModifier<Byte> bytes = packet.getBytes();

        if (type == ENTITY_STATUS) {
            // No need to check if it's invalid, already checked above.
            IVillagerNPC npc = plugin.getConverter().getNPC((Villager) entity).get();
            handleStatus(npc, bytes.readSafely(0));
        }

        // Cancel metadata packet for players using 1.7 (or lower).
        if (type == ENTITY_METADATA
                && plugin.getServer().getPluginManager().getPlugin("ViaVersion") != null
                && plugin.getCompatibilityManager().shouldCancelMetadata(player)) {
            event.setCancelled(true);
        }

        int entityId = entity.getEntityId();

        Optional<NPC> npc = plugin.getTracker().getNPC(entityId);
        if (npc.isEmpty()) return;

        boolean isSleeping = ((Villager) entity).isSleeping();

        if (!sleeping.isEmpty() && !sleeping.contains(player.getUniqueId()) && isSleeping) {
            event.setCancelled(true);
            return;
        }

        // Dont modify locaiton while reviving.
        if (plugin.getConverter().getNPC((Villager) entity)
                .map(IVillagerNPC::isReviving)
                .orElse(false)) return;

        if (MOVEMENT_PACKETS.contains(type)) handleNPCLocation(event, (Villager) entity, npc.get());
    }

    public void handleNPCLocation(@NotNull PacketEvent event, @NotNull Villager villager, NPC npc) {
        PacketContainer packet = event.getPacket();
        PacketType type = packet.getType();

        StructureModifier<Byte> bytes = packet.getBytes();
        Location location = villager.getLocation();
        if (type == ENTITY_HEAD_ROTATION) {
            float yaw = (bytes.read(0) * 360.f) / 256.0f;
            float pitch = location.getPitch();

            location.setYaw(yaw);
            location.setPitch(pitch);

            boolean shakingHead = plugin.getConverter().getNPC(villager).get().isShakingHead();

            // Rotate body with the head.
            if (!villager.isSleeping() && !shakingHead) {
                PacketContainer moveLook = new PacketContainer(REL_ENTITY_MOVE_LOOK);
                moveLook.getIntegers().write(0, villager.getEntityId());
                moveLook.getBytes().write(0, bytes.read(0));
                moveLook.getBytes().write(1, (byte) (pitch * 256f / 360f));

                ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), moveLook);
            }
        } else if (type == ENTITY_LOOK || type == REL_ENTITY_MOVE || type == REL_ENTITY_MOVE_LOOK) {
            StructureModifier<Short> shorts = packet.getShorts();
            double changeInX = shorts.read(0) / 4096.0d;
            double changeInY = shorts.read(1) / 4096.0d;
            double changeInZ = shorts.read(2) / 4096.0d;

            boolean hasRot = packet.getBooleans().read(0);
            float yaw = hasRot ? (bytes.read(0) * 360.f) / 256.0f : location.getYaw();
            float pitch = hasRot ? (bytes.read(1) * 360.f) / 256.0f : location.getPitch();

            location.add(changeInX, changeInY, changeInZ);
            location.setYaw(yaw);
            location.setPitch(pitch);
        } else if (type == ENTITY_TELEPORT) {
            StructureModifier<Double> doubles = packet.getDoubles();
            location.setX(doubles.read(0));
            location.setY(doubles.read(1));
            location.setZ(doubles.read(2));

            location.setYaw((bytes.read(0) * 360.f) / 256.0f);
            location.setPitch((bytes.read(1) * 360.f) / 256.0f);
        } else {
            // Velocity packet.
            double velocityX = packet.getIntegers().read(1) / 8000.0d;
            double velocityY = packet.getIntegers().read(2) / 8000.0d;
            double velocityZ = packet.getIntegers().read(3) / 8000.0d;
            location.add(velocityX, velocityY, velocityZ);
        }

        npc.setLocation(location);
    }

    private void handleStatus(IVillagerNPC npc, byte status) {
        Particle particle;
        switch (status) {
            case 12 -> particle = Particle.HEART;
            case 13 -> particle = Particle.VILLAGER_ANGRY;
            case 14 -> particle = Particle.VILLAGER_HAPPY;
            case 42 -> {
                Raid raid = plugin.getConverter().getRaidAt(npc.bukkit().getLocation());
                particle = raid != null && raid.getStatus() == Raid.RaidStatus.ONGOING ? null : Particle.WATER_SPLASH;
            }
            default -> particle = null;
        }
        if (particle != null) npc.spawnEntityEventParticle(particle);
    }

    @SuppressWarnings("deprecation")
    private boolean isSpawnPacket(PacketType type) {
        return type == SPAWN_ENTITY_LIVING || (ReflectionUtils.supports(19) && type == SPAWN_ENTITY);
    }
}