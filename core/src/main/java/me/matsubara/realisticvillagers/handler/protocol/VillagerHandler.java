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
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static com.comphenix.protocol.PacketType.Play.Server.*;

@SuppressWarnings({"OptionalGetWithoutIsPresent", "deprecation"})
public class VillagerHandler extends PacketAdapter {

    private final RealisticVillagers plugin;
    private final @Getter Set<UUID> allowSpawn = ConcurrentHashMap.newKeySet();

    private static final Set<PacketType> MOVEMENT_PACKETS = Sets.newHashSet(
            ENTITY_HEAD_ROTATION,
            ENTITY_LOOK,
            ENTITY_TELEPORT,
            ENTITY_VELOCITY,
            REL_ENTITY_MOVE,
            REL_ENTITY_MOVE_LOOK);

    public VillagerHandler(RealisticVillagers plugin, Collection<PacketType> packets) {
        super(plugin, ListenerPriority.HIGHEST, ImmutableList.builder()
                .addAll(MOVEMENT_PACKETS)
                .addAll(packets)
                .build()
                .stream()
                .map(object -> (PacketType) object)
                .filter(PacketType::isSupported)
                .toArray(PacketType[]::new));
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

        if (!(entity instanceof AbstractVillager villager) || plugin.getTracker().isInvalid(villager)) return;

        UUID uuid = entity.getUniqueId();
        int entityId = entity.getEntityId();

        Optional<NPC> npc = plugin.getTracker().getNPC(entityId);

        if (isCancellableSpawnPacket(event)) {
            if (!allowSpawn.contains(uuid)) {
                event.setCancelled(true);
                npc.ifPresent(value -> rotateBody(event, villager));
            }
            return;
        }

        StructureModifier<Byte> bytes = packet.getBytes();

        PacketType type = event.getPacketType();
        if (type == ENTITY_STATUS && EntityType.VILLAGER == villager.getType()) {
            handleStatus(plugin.getConverter().getNPC(villager).get(), bytes.readSafely(0));
        }

        // Cancel metadata packet for players using 1.7 (or lower).
        if (type == ENTITY_METADATA && plugin.getCompatibilityManager().shouldCancelMetadata(player)) {
            event.setCancelled(true);
        }

        if (npc.isEmpty()) return;

        // Dont modify location while reviving.
        if (plugin.getConverter().getNPC(villager)
                .map(IVillagerNPC::isReviving)
                .orElse(false)) return;

        if (MOVEMENT_PACKETS.contains(type)) rotateBody(event, villager);
    }

    private void rotateBody(@NotNull PacketEvent event, @NotNull AbstractVillager villager) {
        PacketContainer packet = event.getPacket();

        PacketType type = packet.getType();
        if (type != ENTITY_HEAD_ROTATION) return;

        StructureModifier<Byte> bytes = packet.getBytes();
        Location location = villager.getLocation();

        float pitch = location.getPitch();

        // Rotate body with the head.
        if (plugin.getConverter().getNPC(villager).get().isShakingHead()) return;

        PacketContainer moveLook = new PacketContainer(REL_ENTITY_MOVE_LOOK);
        moveLook.getIntegers().write(0, villager.getEntityId());
        moveLook.getBytes().write(0, bytes.read(0));
        moveLook.getBytes().write(1, (byte) (pitch * 256f / 360f));

        ProtocolLibrary.getProtocolManager().sendServerPacket(event.getPlayer(), moveLook);
    }

    private void handleStatus(@NotNull IVillagerNPC npc, byte status) {
        LivingEntity bukkit = npc.bukkit();

        Particle particle;
        switch (status) {
            case 12 -> particle = Particle.HEART;
            case 13 -> particle = Particle.VILLAGER_ANGRY;
            case 14 -> particle = Particle.VILLAGER_HAPPY;
            case 42 -> {
                Raid raid = plugin.getConverter().getRaidAt(bukkit.getLocation());
                particle = raid != null && raid.getStatus() == Raid.RaidStatus.ONGOING ? null : Particle.WATER_SPLASH;
            }
            default -> particle = null;
        }
        if (particle == null) return;

        Location location = bukkit.getLocation();
        BoundingBox box = bukkit.getBoundingBox();

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = location.getX() + box.getWidthX() * ((2.0d * random.nextDouble() - 1.0d) * 1.05d);
        double y = location.getY() + box.getHeight() * random.nextDouble() + 1.15d;
        double z = location.getZ() + box.getWidthZ() * ((2.0d * random.nextDouble() - 1.0d) * 1.05d);

        bukkit.getWorld().spawnParticle(
                particle,
                x,
                y,
                z,
                1,
                random.nextGaussian() * 0.02d,
                random.nextGaussian() * 0.02d,
                random.nextGaussian() * 0.02d);
    }

    private boolean isCancellableSpawnPacket(@NotNull PacketEvent event) {
        PacketType type = event.getPacketType();
        if (type == SPAWN_ENTITY_LIVING) return true;

        if (!ReflectionUtils.supports(19) || type != SPAWN_ENTITY) return false;

        EntityType entityType = event.getPacket().getEntityTypeModifier().read(0);
        return !PluginUtils.IS_1_20_2_OR_NEW || entityType == EntityType.VILLAGER || entityType == EntityType.WANDERING_TRADER;
    }
}