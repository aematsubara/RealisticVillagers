package me.matsubara.realisticvillagers.handler.protocol;

import com.cryptomorin.xseries.particles.XParticle;
import com.cryptomorin.xseries.reflection.XReflection;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.SimplePacketListenerAbstract;
import com.github.retrooper.packetevents.event.simple.PacketPlaySendEvent;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.handler.npc.NPCHandler;
import me.matsubara.realisticvillagers.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public class VillagerHandler extends SimplePacketListenerAbstract {

    private final RealisticVillagers plugin;
    private final @Getter Set<UUID> allowSpawn = ConcurrentHashMap.newKeySet();
    private final List<PacketType.Play.Server> listenTo;

    /* VILLAGER METADATA
    ID = 15 | ACCESSOR ID = 15 | VALUE TYPE = Byte | CLAZZ = BYTE (MOB) | NoAI/Is left handed/Is aggresive
    ID = 16 | ACCESSOR ID = 16 | VALUE TYPE = Boolean | CLAZZ = BOOLEAN (AGEABLE MOB) Is baby
    ID = 17 | ACCESSOR ID = 17 | VALUE TYPE = Integer | CLAZZ = INT (ABSTRACT VILLAGER) | Head shake timer
    ID = 18 | ACCESSOR ID = 18 | VALUE TYPE = VillagerData | CLAZZ = VILLAGER_DATA (VILLAGER) | Villager Data
    PLAYER METADATA
    ID = 15 | ACCESSOR ID = 15 | VALUE TYPE = Float | CLAZZ = FLOAT | Additional Hearts
    ID = 16 | ACCESSOR ID = 16 | VALUE TYPE = Integer | CLAZZ = INT | Score
    ID = 17 | ACCESSOR ID = 17 | VALUE TYPE = Byte | CLAZZ = BYTE | The Displayed Skin Parts bit mask that is sent in Client Settings
    ID = 18 | ACCESSOR ID = 18 | VALUE TYPE = Byte | CLAZZ = BYTE | Main hand (0 : Left, 1 : Right)
    ID = 19 | ACCESSOR ID = 19 | VALUE TYPE = TagCompound | CLAZZ = COMPOUND_TAG | Left shoulder entity data (for occupying parrot)
    ID = 20 | ACCESSOR ID = 20 | VALUE TYPE = TagCompound | CLAZZ = COMPOUND_TAG | Right shoulder entity data (for occupying parrot)
    */
    private static final Predicate<EntityData> REMOVE_METADATA = data -> {
        // Data between 0-14 is the same for players and villagers.
        int index = data.getIndex();
        if (index <= 14) return false;

        // 15 & 16 is unnecessary.
        if (index == 15 || index == 16) return true;

        // 17: Keep skin state (over head shake timer).
        if (index == 17 && data.getType() != EntityDataTypes.BYTE) return true;

        // 19 & 20 only exists for players, they shouldn't collide with anything.
        return data.getType() == EntityDataTypes.VILLAGER_DATA;
    };

    private static final Set<PacketType.Play.Server> MOVEMENT_PACKETS = Sets.newHashSet(
            PacketType.Play.Server.ENTITY_ROTATION,
            PacketType.Play.Server.ENTITY_HEAD_LOOK,
            PacketType.Play.Server.ENTITY_TELEPORT,
            PacketType.Play.Server.ENTITY_VELOCITY,
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE,
            PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION);

    public VillagerHandler(RealisticVillagers plugin) {
        super(PacketListenerPriority.HIGHEST);
        this.plugin = plugin;
        this.listenTo = ImmutableList.builder()
                .addAll(MOVEMENT_PACKETS)
                .add(
                        PacketType.Play.Server.SPAWN_ENTITY,
                        PacketType.Play.Server.SPAWN_LIVING_ENTITY,
                        PacketType.Play.Server.ENTITY_STATUS,
                        PacketType.Play.Server.ENTITY_METADATA)
                .build()
                .stream()
                .map(object -> (PacketType.Play.Server) object)
                .toList();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onPacketPlaySend(@NotNull PacketPlaySendEvent event) {
        if (event.isCancelled()
                || !listenTo.contains(event.getPacketType())
                || !(event.getPlayer() instanceof Player player)) return;

        PacketType.Play.Server type = event.getPacketType();
        boolean isMetadata = type == PacketType.Play.Server.ENTITY_METADATA;

        World world;
        int id;
        Entity entity;
        try {
            world = player.getWorld();
            id = getEntityIdFromPacket(event);
            entity = id != -1 ? SpigotReflectionUtil.getEntityById(world, id) : null;
        } catch (Exception ignored) {
            // Should "fix" → UnsupportedOperationException: The method getWorld is not supported for temporary players.
            // Should "fix" → IOException: Unknown nbt type id X.
            // Should "fix" → NullPointerException: null (entity)
            if (isMetadata) event.setCancelled(true);
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

        if (type == PacketType.Play.Server.ENTITY_STATUS && EntityType.VILLAGER == villager.getType()) {
            WrapperPlayServerEntityStatus status = new WrapperPlayServerEntityStatus(event);
            plugin.getConverter().getNPC(villager).ifPresent(temp -> handleStatus(temp, (byte) status.getStatus()));
        }

        if (isMetadata) {
            // Cancel metadata packets for players using 1.7 (or lower).
            if (plugin.getCompatibilityManager().shouldCancelMetadata(player)) {
                event.setCancelled(true);
            }

            ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
            if (version.isNewerThanOrEquals(ServerVersion.V_1_20_4)) {
                try {
                    // Fix issues with ViaVersion.
                    WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);

                    List<EntityData> metadata = wrapper.getEntityMetadata();
                    metadata.removeIf(REMOVE_METADATA);

                    wrapper.setEntityMetadata(metadata);

                    // Adapt villager scale using the new scale attribute.
                    // This was added to 1.20.5, but that version was quickly replaced by 1.20.6.
                    if (version.isNewerThanOrEquals(ServerVersion.V_1_20_5)
                            && npc.isPresent()
                            && npc.get().getSpawnCustomizer() instanceof NPCHandler handler) {
                        handler.adaptScale(player, npc.get());
                    }
                } catch (Exception ignored) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (npc.isEmpty()) return;

        // Don't modify location while reviving.
        if (plugin.getConverter().getNPC(villager)
                .map(IVillagerNPC::isReviving)
                .orElse(false)) return;

        if (MOVEMENT_PACKETS.contains(type)) rotateBody(event, villager);
    }

    private int getEntityIdFromPacket(@NotNull PacketPlaySendEvent event) {
        PacketType.Play.Server type = event.getPacketType();
        if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.SPAWN_LIVING_ENTITY) {
            WrapperPlayServerSpawnLivingEntity wrapper = new WrapperPlayServerSpawnLivingEntity(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_STATUS) {
            WrapperPlayServerEntityStatus wrapper = new WrapperPlayServerEntityStatus(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_METADATA) {
            WrapperPlayServerEntityMetadata wrapper = new WrapperPlayServerEntityMetadata(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_ROTATION) {
            WrapperPlayServerEntityRotation wrapper = new WrapperPlayServerEntityRotation(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            WrapperPlayServerEntityHeadLook wrapper = new WrapperPlayServerEntityHeadLook(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport wrapper = new WrapperPlayServerEntityTeleport(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_VELOCITY) {
            WrapperPlayServerEntityVelocity wrapper = new WrapperPlayServerEntityVelocity(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove wrapper = new WrapperPlayServerEntityRelativeMove(event);
            return wrapper.getEntityId();
        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation wrapper = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            return wrapper.getEntityId();
        }
        return -1;
    }

    private void rotateBody(@NotNull PacketPlaySendEvent event, @NotNull AbstractVillager villager) {
        PacketType.Play.Server type = event.getPacketType();
        if (type != PacketType.Play.Server.ENTITY_HEAD_LOOK) return;

        WrapperPlayServerEntityHeadLook headLook = new WrapperPlayServerEntityHeadLook(event);

        Location location = villager.getLocation();

        float pitch = location.getPitch();

        // Rotate the body with the head.
        if (plugin.getConverter().getNPC(villager)
                .map(IVillagerNPC::isShakingHead)
                .orElse(false)) return;

        WrapperPlayServerEntityRelativeMoveAndRotation rotation = new WrapperPlayServerEntityRelativeMoveAndRotation(
                villager.getEntityId(),
                0.0d,
                0.0d,
                0.0d,
                headLook.getHeadYaw(),
                pitch,
                false);

        PacketEvents.getAPI().getProtocolManager().sendPacket(event.getChannel(), rotation);
    }

    private void handleStatus(@NotNull IVillagerNPC npc, byte status) {
        LivingEntity bukkit = npc.bukkit();

        XParticle particle;
        switch (status) {
            case 12 -> particle = XParticle.HEART;
            case 13 -> particle = XParticle.ANGRY_VILLAGER;
            case 14 -> particle = XParticle.HAPPY_VILLAGER;
            case 42 -> {
                Raid raid = plugin.getConverter().getRaidAt(bukkit.getLocation());
                particle = raid != null && raid.getStatus() == Raid.RaidStatus.ONGOING ? null : XParticle.SPLASH;
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
                particle.get(),
                x,
                y,
                z,
                1,
                random.nextGaussian() * 0.02d,
                random.nextGaussian() * 0.02d,
                random.nextGaussian() * 0.02d);
    }

    private boolean isCancellableSpawnPacket(@NotNull PacketPlaySendEvent event) {
        PacketType.Play.Server type = event.getPacketType();
        if (type == PacketType.Play.Server.SPAWN_LIVING_ENTITY) return true;

        if (!XReflection.supports(19) || type != PacketType.Play.Server.SPAWN_ENTITY) return false;

        WrapperPlayServerSpawnEntity wrapper = new WrapperPlayServerSpawnEntity(event);
        EntityType entityType = SpigotConversionUtil.toBukkitEntityType(wrapper.getEntityType());

        return PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_2)
                || entityType == EntityType.VILLAGER
                || entityType == EntityType.WANDERING_TRADER;
    }
}