package me.matsubara.realisticvillagers.tracker;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerRemoveEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.listener.spawn.BukkitSpawnListeners;
import me.matsubara.realisticvillagers.listener.spawn.PaperSpawnListeners;
import me.matsubara.realisticvillagers.util.ReflectionUtils;
import me.matsubara.realisticvillagers.util.npc.NPC;
import me.matsubara.realisticvillagers.util.npc.NPCPool;
import me.matsubara.realisticvillagers.util.npc.SpawnCustomizer;
import me.matsubara.realisticvillagers.util.npc.modifier.MetadataModifier;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.entity.memory.MemoryKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

import static com.comphenix.protocol.PacketType.Play.Server.*;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@Getter
public final class VillagerTracker implements Listener {

    private final RealisticVillagers plugin;
    private final NPCPool pool;
    private final BukkitSpawnListeners spawnListeners;
    private final Map<UUID, String> transformations = new HashMap<>();
    private final Map<UUID, Integer> portalTransform = new HashMap<>();
    private final Set<UUID> playerSleepFix = new HashSet<>();
    private final Set<VillagerInfo> villagers = new HashSet<>();

    private File file;
    private FileConfiguration configuration;
    private boolean teamCleared;

    private final static int RENDER_DISTANCE = 32;
    private final static String NAMETAG_TEAM_NAME = "RVNametag";
    private final static Predicate<Entity> APPLY_FOR_TRANSFORM = entity -> entity instanceof Villager || entity instanceof ZombieVillager;
    private final static Set<PacketType> MOVEMENT_PACKETS = Sets.newHashSet(
            ENTITY_VELOCITY,
            REL_ENTITY_MOVE,
            ENTITY_LOOK,
            ENTITY_TELEPORT,
            ENTITY_HEAD_ROTATION,
            REL_ENTITY_MOVE_LOOK);

    public VillagerTracker(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.pool = NPCPool
                .builder(plugin)
                .spawnDistance(RENDER_DISTANCE)
                .actionDistance(RENDER_DISTANCE)
                .tabListRemoveTicks(40)
                .build();
        this.spawnListeners = new BukkitSpawnListeners(plugin);

        PluginManager manager = plugin.getServer().getPluginManager();

        PaperSpawnListeners paperListener = new PaperSpawnListeners(plugin);
        if (!paperListener.isRegistered()) manager.registerEvents(spawnListeners, plugin);

        manager.registerEvents(this, plugin);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(plugin,
                ListenerPriority.HIGHEST,
                SPAWN_ENTITY,
                SPAWN_ENTITY_EXPERIENCE_ORB,
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
                ENTITY_EFFECT) {
            @Override
            public void onPacketSending(PacketEvent event) {
                Player player = event.getPlayer();
                Entity entity = event.getPacket().getEntityModifier(player.getWorld()).readSafely(0);
                if (isInvalidEntity(entity)) return;

                PacketType type = event.getPacketType();
                if (isSpawnPacket(type)) {
                    event.setCancelled(true);
                    return;
                }

                if (type == ENTITY_STATUS) {
                    IVillagerNPC npc = VillagerTracker.this.plugin.getConverter().getNPC((Villager) entity).get();
                    handleStatus(npc, event.getPacket().getBytes().readSafely(0));
                }

                int entityId = entity.getEntityId();

                Optional<NPC> npc = pool.getNpc(entityId);
                if (npc.isEmpty()) return;

                boolean isSleeping = ((Villager) entity).isSleeping();

                if (!playerSleepFix.isEmpty() && !playerSleepFix.contains(player.getUniqueId()) && isSleeping) {
                    event.setCancelled(true);
                    return;
                }

                if (!MOVEMENT_PACKETS.contains(type)) return;

                Location location = entity.getLocation();

                if (type == ENTITY_HEAD_ROTATION) {
                    float yaw = (event.getPacket().getBytes().read(0) * 360.f) / 256.0f;
                    float pitch = location.getPitch();

                    PacketContainer moveLook = new PacketContainer(REL_ENTITY_MOVE_LOOK);
                    moveLook.getIntegers().write(0, entityId);
                    moveLook.getBytes().write(0, event.getPacket().getBytes().read(0));
                    moveLook.getBytes().write(1, (byte) (pitch * 256f / 360f));

                    location.setYaw(yaw);
                    location.setPitch(pitch);

                    if (!isSleeping) ProtocolLibrary.getProtocolManager().sendServerPacket(player, moveLook);
                } else if (type == ENTITY_LOOK || type == REL_ENTITY_MOVE || type == REL_ENTITY_MOVE_LOOK) {
                    double changeInX = event.getPacket().getShorts().read(0) / 4096.0d;
                    double changeInY = event.getPacket().getShorts().read(1) / 4096.0d;
                    double changeInZ = event.getPacket().getShorts().read(2) / 4096.0d;

                    boolean hasRot = event.getPacket().getBooleans().read(0);
                    float yaw = hasRot ? (event.getPacket().getBytes().read(0) * 360.f) / 256.0f : location.getYaw();
                    float pitch = hasRot ? (event.getPacket().getBytes().read(1) * 360.f) / 256.0f : location.getPitch();

                    location.add(changeInX, changeInY, changeInZ);
                    location.setYaw(yaw);
                    location.setPitch(pitch);
                }

                npc.get().setLocation(location);
            }

            private void handleStatus(IVillagerNPC npc, byte status) {
                Particle particle;
                switch (status) {
                    case 12 -> particle = Particle.HEART;
                    case 13 -> particle = Particle.VILLAGER_ANGRY;
                    case 14 -> particle = Particle.VILLAGER_HAPPY;
                    case 42 -> particle = npc.canAttack() ? null : Particle.WATER_SPLASH;
                    default -> particle = null;
                }
                if (particle != null) npc.spawnEntityEventParticle(particle);
            }

            private boolean isInvalidEntity(Entity entity) {
                return entity == null || entity.isDead() || !(entity instanceof Villager villager) || isInvalid(villager);
            }
        });

        load();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void load() {
        file = new File(plugin.getDataFolder(), "villagers.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        configuration = new YamlConfiguration();
        try {
            configuration.load(file);
            update();
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
    }

    private void update() {
        villagers.clear();

        ConfigurationSection section = configuration.getConfigurationSection("villagers");
        if (section == null) return;

        for (String path : section.getKeys(false)) {
            UUID uuid = UUID.fromString(path);
            String lastKnownName = configuration.getString("villagers." + path + ".last-known-name");
            long lastSeen = configuration.getLong("villagers." + path + ".last-seen");
            villagers.add(new VillagerInfo(uuid, -1, lastKnownName, lastSeen));
        }
    }

    public void add(Villager villager) {
        UUID uuid = villager.getUniqueId();
        int id = villager.getEntityId();

        String name = plugin.getConverter().getNPC(villager)
                .map(IVillagerNPC::getVillagerName)
                .orElse(villager.getName());

        long seen = System.currentTimeMillis();

        VillagerInfo info = getInfoByUUID(uuid);
        if (info != null) {
            info.setId(id);
            info.setLastKnownName(name);
            info.setLastSeen(seen);
        } else {
            villagers.add(new VillagerInfo(uuid, id, name));
        }

        configuration.set("villagers." + uuid + ".last-known-name", name);
        configuration.set("villagers." + uuid + ".last-seen", seen);

        saveConfig();
    }

    private VillagerInfo getInfoByUUID(UUID uuid) {
        for (VillagerInfo info : villagers) {
            if (info.getUUID().equals(uuid)) return info;
        }
        return null;
    }

    public VillagerInfo get(UUID uuid) {
        if (uuid == null) return null;

        Entity entity = Bukkit.getEntity(uuid);
        if (entity instanceof Villager villager) {
            VillagerInfo info = getInfoByUUID(uuid);
            if (info != null) {
                info.updateLastSeen();
                saveConfig();
            } else add(villager);
        }

        return getInfoByUUID(uuid);
    }

    private void markAsDeath(Villager villager) {
        handlePartner(villager);

        VillagerInfo info = getInfoByUUID(villager.getUniqueId());
        if (info == null) return;

        info.setLastSeen(-1L);
        configuration.set("villagers." + info.getUUID() + ".last-seen", -1L);
        configuration.set("villagers." + info.getUUID() + ".death", System.currentTimeMillis());
        saveConfig();
    }

    private void handlePartner(Villager villager) {
        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;

        UUID partner = npc.getPartner();
        if (partner == null) return;

        Player player = Bukkit.getPlayer(partner);
        if (player != null && player.isOnline()) {
            player.getPersistentDataContainer().remove(plugin.getMarriedWith());
        }
    }

    private void saveConfig() {
        try {
            configuration.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity.getType() != EntityType.VILLAGER) continue;
            VillagerInfo info = get(entity.getUniqueId());
            if (info != null && info.getId() != -1) {
                removeNPC(entity.getEntityId());
                info.setId(-1);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        Entity entity = event.getEntity();
        EntityTransformEvent.TransformReason reason = event.getTransformReason();

        if (entity.getType() == EntityType.VILLAGER && reason == EntityTransformEvent.TransformReason.LIGHTNING) {
            removeNPC(event.getEntity().getEntityId());
            return;
        }

        if (!APPLY_FOR_TRANSFORM.test(entity)) return;

        Entity transformed = event.getTransformedEntity();
        if (!APPLY_FOR_TRANSFORM.test(transformed)) return;

        boolean isInfection = reason == EntityTransformEvent.TransformReason.INFECTION;
        if (!isInfection && reason != EntityTransformEvent.TransformReason.CURED) return;

        transformations.put(transformed.getUniqueId(), plugin.getConverter().getNPCTag((LivingEntity) entity));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        markAsDeath(villager);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeNPC(event.getEntity().getEntityId()), 40L);
    }

    @EventHandler
    public void onVillagerRemove(VillagerRemoveEvent event) {
        if (event.getReason() != VillagerRemoveEvent.RemovalReason.DISCARDED) return;
        markAsDeath(event.getNPC().bukkit());
        removeNPC(event.getNPC().bukkit().getEntityId());
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            portalTransform.put(villager.getUniqueId(), villager.getEntityId());
        }
    }

    @EventHandler
    public void onEntityPortalEnter(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;

        int previous = portalTransform.getOrDefault(villager.getUniqueId(), -1);
        if (previous == -1) return;

        removeNPC(previous);

        Optional<IVillagerNPC> npc = plugin.getConverter().getNPC(villager);
        if (npc.isPresent()) spawnNPC(villager);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        PersistentDataContainer container = event.getPlayer().getPersistentDataContainer();

        String partner = container.get(plugin.getMarriedWith(), PersistentDataType.STRING);
        if (partner == null) return;

        UUID uuid = UUID.fromString(partner);
        for (VillagerInfo info : villagers) {
            if (!info.getUUID().equals(uuid)) continue;
            if (!info.isDead()) continue;
            container.remove(plugin.getMarriedWith());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerSleepFix.remove(event.getPlayer().getUniqueId());
    }

    public void removeNPC(int entityId) {
        pool.getNpc(entityId).ifPresent(npc -> pool.removeNPC(npc.getEntityId()));
    }

    public boolean hasNPC(int entityId) {
        return pool.getNpc(entityId).isPresent();
    }

    @SuppressWarnings("deprecation")
    private boolean isSpawnPacket(PacketType type) {
        return type == SPAWN_ENTITY_LIVING || (ReflectionUtils.supports(19) && type == SPAWN_ENTITY);
    }

    public boolean isInvalid(Villager villager) {
        return Config.DISABLE_SKINS.asBool()
                || !plugin.isEnabledIn(villager.getWorld())
                || villager.hasMetadata("shopkeeper")
                || plugin.getConverter().getNPC(villager).isEmpty();
    }

    public void spawnNPC(Villager villager) {
        if (isInvalid(villager)) return;

        int entityId = villager.getEntityId();
        if (hasNPC(entityId)) return;

        WrappedSignedProperty textures = getTextures(villager);
        Preconditions.checkArgument(textures != null, "Invalid textures!");

        String name = plugin.getConverter().getNPC(villager)
                .map(IVillagerNPC::getVillagerName)
                .orElse(villager.getName());

        WrappedGameProfile profile = new WrappedGameProfile(UUID.randomUUID(), name);
        profile.getProperties().put("textures", textures);

        NPC.builder()
                .profile(profile)
                .location(villager.getLocation())
                .spawnCustomizer(new SpawnHandler(plugin, villager))
                .entityId(entityId)
                .usePlayerProfiles(false)
                .lookAtPlayer(false)
                .imitatePlayer(false)
                .build(pool);

        if (!Config.DISABLE_NAMETAGS.asBool()) return;

        Team hideTeam = getOrCreateNametagTeam();
        hideTeam.addEntry(name);
    }

    public Team getOrCreateNametagTeam() {
        @SuppressWarnings("ConstantConditions") Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        Team team = scoreboard.getTeam(NAMETAG_TEAM_NAME);

        if (team != null) {
            if (teamCleared) return team;
            try {
                team.getEntries().forEach(team::removeEntry);
                teamCleared = true;
            } catch (IllegalStateException ignore) {
            }
        } else {
            team = scoreboard.registerNewTeam(NAMETAG_TEAM_NAME);
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }

        return team;
    }

    public WrappedSignedProperty getTextures(Villager villager) {
        IVillagerNPC npc = plugin.getConverter().getNPC(villager).get();

        File file = new File(plugin.getSkinFolder(), npc.getSex() + ".yml");
        if (!file.exists()) return null;

        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }

        String profession = villager.getProfession().name().toLowerCase();

        ConfigurationSection section = config.getConfigurationSection(profession);
        if (section == null) return null;

        int which = getSkinId(npc, section.getKeys(false).size());

        String texture = config.getString(profession + "." + which + ".texture");
        String signature = config.getString(profession + "." + which + ".signature");
        return texture != null && signature != null ? new WrappedSignedProperty("textures", texture, signature) : null;
    }

    private int getSkinId(IVillagerNPC npc, int amount) {
        if (npc.getSkinTextureId() == -1) {
            int which = ThreadLocalRandom.current().nextInt(1, amount + 1);
            npc.setSkinTextureId(which);
        }
        return npc.getSkinTextureId();
    }

    public boolean fixSleep() {
        return !playerSleepFix.isEmpty();
    }

    private static class SpawnHandler implements SpawnCustomizer {

        private final RealisticVillagers plugin;
        private final IVillagerNPC villager;

        // Show everything except cape.
        private final static MetadataModifier.EntityMetadata<Boolean, Byte> SKIN_LAYERS = new MetadataModifier.EntityMetadata<>(
                10,
                Byte.class,
                Arrays.asList(9, 9, 10, 14, 14, 15, 17),
                input -> (byte) (input ? 126 : 0));

        public SpawnHandler(RealisticVillagers plugin, Villager villager) {
            this.plugin = plugin;
            this.villager = plugin.getConverter().getNPC(villager).get();
        }

        @Override
        public void handleSpawn(@NotNull NPC npc, @NotNull Player player) {
            Location location = villager.bukkit().getLocation();

            npc.rotation().queueRotate(location.getYaw(), location.getPitch()).send(player);
            npc.metadata().queue(SKIN_LAYERS, true).send(player);

            if (villager.bukkit().isSleeping()) {
                Location home = villager.bukkit().getMemory(MemoryKey.HOME);

                plugin.getVillagerTracker().getPlayerSleepFix().add(player.getUniqueId());

                npc.teleport().queueTeleport(location, villager.bukkit().isOnGround()).send(player);
                npc.metadata().queue(MetadataModifier.EntityMetadata.POSE, EnumWrappers.EntityPose.SLEEPING).send(player);

                villager.bukkit().wakeup();

                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (home != null) villager.bukkit().sleep(home);
                    plugin.getVillagerTracker().getPlayerSleepFix().remove(player.getUniqueId());
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
}