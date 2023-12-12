package me.matsubara.realisticvillagers.handler.protocol;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import com.cryptomorin.xseries.ReflectionUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.comphenix.protocol.PacketType.Play.Server.*;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "deprecation"})
public class VillagerHandler extends PacketAdapter {

    private final RealisticVillagers plugin;
    private final @Getter Set<UUID> sleeping = ConcurrentHashMap.newKeySet();
    private final @Getter Set<UUID> allowSpawn = ConcurrentHashMap.newKeySet();

    private static final Set<PacketType> MOVEMENT_PACKETS = Sets.newHashSet(
            ENTITY_HEAD_ROTATION,
            ENTITY_LOOK,
            ENTITY_TELEPORT,
            ENTITY_VELOCITY,
            REL_ENTITY_MOVE,
            REL_ENTITY_MOVE_LOOK);

    private static final PacketType[] LISTEN_PACKETS = ImmutableList.builder()
            .addAll(MOVEMENT_PACKETS)
            .add(SPAWN_ENTITY, SPAWN_ENTITY_LIVING, ENTITY_STATUS, ENTITY_METADATA)
            .build()
            .stream()
            .map(object -> (PacketType) object)
            .filter(PacketType::isSupported)
            .toArray(PacketType[]::new);

    public VillagerHandler(RealisticVillagers plugin) {
        super(plugin, ListenerPriority.HIGHEST, LISTEN_PACKETS);
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

        Entity entity;
        try {
            entity = packet.getEntityModifier(world).readSafely(0);
        } catch (Exception exception) {
            // Should "fix" -> FieldAccessException/RuntimeException: Cannot find entity from ID (X?).
            return;
        }

        if (!(entity instanceof Villager villager) || plugin.getTracker().isInvalid(villager)) return;

        UUID uuid = entity.getUniqueId();
        int entityId = entity.getEntityId();

        Optional<NPC> npc = plugin.getTracker().getNPC(entityId);

        if (isCancellableSpawnPacket(event)) {
            if (!allowSpawn.contains(uuid)) {
                event.setCancelled(true);
                npc.ifPresent(value -> handleNPCLocation(event, villager, value));
            }
            return;
        }

        StructureModifier<Byte> bytes = packet.getBytes();

        PacketType type = event.getPacketType();
        if (type == ENTITY_STATUS) {
            handleStatus(plugin.getConverter().getNPC(villager).get(), bytes.readSafely(0));
        }

        // Cancel metadata packet for players using 1.7 (or lower).
        if (type == ENTITY_METADATA
                && plugin.getServer().getPluginManager().getPlugin("ViaVersion") != null
                && plugin.getCompatibilityManager().shouldCancelMetadata(player)) {
            event.setCancelled(true);
        }

        if (npc.isEmpty()) return;

        boolean isSleeping = (villager).isSleeping();

        if (!sleeping.isEmpty() && !sleeping.contains(player.getUniqueId()) && isSleeping) {
            event.setCancelled(true);
            return;
        }

        // Dont modify locaiton while reviving.
        if (plugin.getConverter().getNPC(villager)
                .map(IVillagerNPC::isReviving)
                .orElse(false)) return;

        if (MOVEMENT_PACKETS.contains(type)) handleNPCLocation(event, villager, npc.get());
    }

    public void handleNPCLocation(@NotNull PacketEvent event, @NotNull Villager villager, NPC npc) {
        PacketContainer packet = event.getPacket();
        PacketType type = packet.getType();

        StructureModifier<Byte> bytes = packet.getBytes();
        Location location = villager.getLocation();

        boolean isTeleport;
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
        } else if ((isTeleport = type == ENTITY_TELEPORT) || type == SPAWN_ENTITY_LIVING || type == SPAWN_ENTITY) {
            StructureModifier<Double> doubles = packet.getDoubles();
            location.setX(doubles.read(0));
            location.setY(doubles.read(1));
            location.setZ(doubles.read(2));

            if (isTeleport) {
                location.setYaw((bytes.read(0) * 360.f) / 256.0f);
                location.setPitch((bytes.read(1) * 360.f) / 256.0f);
            }
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

    private boolean isCancellableSpawnPacket(@NotNull PacketEvent event) {
        PacketType type = event.getPacketType();
        if (type == SPAWN_ENTITY_LIVING) return true;

        if (!ReflectionUtils.supports(19) || type != SPAWN_ENTITY) return false;

        return !PluginUtils.IS_1_20_2_OR_NEW || event.getPacket().getEntityTypeModifier().read(0) == EntityType.VILLAGER;
    }
}