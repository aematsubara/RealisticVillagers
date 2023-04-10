package me.matsubara.realisticvillagers.tracker;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.VillagerRemoveEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.listener.npc.NPCHandler;
import me.matsubara.realisticvillagers.listener.protocol.VillagerHandler;
import me.matsubara.realisticvillagers.listener.spawn.BukkitSpawnListeners;
import me.matsubara.realisticvillagers.listener.spawn.PaperSpawnListeners;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.npc.NPCPool;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

@Getter
public final class VillagerTracker implements Listener {

    private final RealisticVillagers plugin;
    private final NPCPool pool;
    private final BukkitSpawnListeners spawnListeners;
    private final Map<UUID, String> transformations = new HashMap<>();
    private final Map<UUID, Integer> portalTransform = new HashMap<>();
    private final Set<IVillagerNPC> offlineVillagers = ConcurrentHashMap.newKeySet();
    private final Map<String, Pair<File, FileConfiguration>> files = new HashMap<>();
    private final VillagerHandler handler;

    private static final int RENDER_DISTANCE = 32;
    private static final String NAMETAG_TEAM_NAME = "RVNametag";
    private static final String HIDE_NAMETAG_NAME = "abcdefghijklmnño";
    private static final Predicate<Entity> APPLY_FOR_TRANSFORM = entity -> entity instanceof Villager || entity instanceof ZombieVillager;

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
        ProtocolLibrary.getProtocolManager().addPacketListener(handler = new VillagerHandler(plugin));
    }

    public IVillagerNPC getOfflineByUUID(UUID uuid) {
        for (IVillagerNPC info : offlineVillagers) {
            if (info.getUniqueId().equals(uuid)) return info;
        }
        return null;
    }

    public IVillagerNPC getOffline(UUID uuid) {
        if (uuid == null) return null;

        Entity entity = Bukkit.getEntity(uuid);

        Optional<IVillagerNPC> npc;
        if (entity instanceof Villager villager
                && (npc = plugin.getConverter().getNPC(villager)).isPresent()) {
            IVillagerNPC current = getOfflineByUUID(uuid);
            if (current != null) offlineVillagers.remove(current);

            IVillagerNPC newNPC = npc.get().getOffline();
            offlineVillagers.add(newNPC);

            return newNPC;
        }

        return getOfflineByUUID(uuid);
    }

    private void markAsDeath(Villager villager) {
        handlePartner(villager);

        IVillagerNPC info = getOfflineByUUID(villager.getUniqueId());
        if (info != null) offlineVillagers.remove(info);
    }

    private void handlePartner(Villager villager) {
        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(villager);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;

        // Can be a player or a villager (there's no chance a villager and a player share the same UUID).
        IVillagerNPC partner = npc.getPartner();
        if (partner == null) return;

        Player player = Bukkit.getPlayer(partner.getUniqueId());
        if (player != null && player.isOnline()) {
            player.getPersistentDataContainer().remove(plugin.getMarriedWith());
            return;
        }

        // Player is offline, we need to modify the NBT file (if possible).
        File playerFile = getPlayerNBTFile(partner.getUniqueId());
        if (playerFile != null) plugin.getConverter().removePartnerFromPlayerNBT(playerFile);
    }

    public File getPlayerNBTFile(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            File data = new File(world.getWorldFolder(), "playerdata");

            File playerFile = new File(data, uuid.toString() + ".dat");
            if (!playerFile.exists()) continue;
            return playerFile;
        }
        return null;
    }

    @EventHandler
    public void onEntitiesUnload(EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof Villager villager) || isInvalid(villager)) continue;
            updateData(villager);
            removeNPC(entity.getEntityId());
        }
    }

    private void updateData(Villager villager) {
        // Update the data after being unloaded.
        Optional<IVillagerNPC> npc = plugin.getConverter().getNPC(villager);
        if (npc.isPresent()) {
            IVillagerNPC currentOffline = getOfflineByUUID(villager.getUniqueId());
            if (currentOffline != null) offlineVillagers.remove(currentOffline);

            offlineVillagers.add(npc.get().getOffline());
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

        // If villager isn't a custom one, the tag will be null.
        transformations.put(transformed.getUniqueId(), plugin.getConverter().getNPCTag((LivingEntity) entity, isInfection));
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        markAsDeath(villager);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeNPC(event.getEntity().getEntityId()), 40L);
    }

    @EventHandler
    public void onVillagerRemove(VillagerRemoveEvent event) {
        VillagerRemoveEvent.RemovalReason reason = event.getReason();
        if (reason != VillagerRemoveEvent.RemovalReason.DISCARDED) {
            if (reason != VillagerRemoveEvent.RemovalReason.KILLED) updateData(event.getNPC().bukkit());
            return;
        }
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

        IVillagerNPC offline = getOffline(uuid);
        if (offline == null) container.remove(plugin.getMarriedWith());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        handler.getSleeping().remove(event.getPlayer().getUniqueId());
    }

    public void removeNPC(int entityId) {
        getNPC(entityId).ifPresent(npc -> pool.removeNPC(npc.getEntityId()));
    }

    public boolean hasNPC(int entityId) {
        return getNPC(entityId).isPresent();
    }

    public Optional<NPC> getNPC(int entityId) {
        return pool.getNPC(entityId);
    }

    public boolean isInvalid(Villager villager, boolean ignoreSkinsState) {
        PersistentDataContainer container = villager.getPersistentDataContainer();
        return (!ignoreSkinsState && Config.DISABLE_SKINS.asBool())
                || villager.hasMetadata("shopkeeper")
                || container.has(plugin.getIgnoreVillagerKey(), PersistentDataType.INTEGER)
                || !plugin.isEnabledIn(villager.getWorld())
                || plugin.getConverter().getNPC(villager).isEmpty();
    }

    public boolean isInvalid(Villager villager) {
        return isInvalid(villager, false);
    }

    public void refreshNPC(Villager villager) {
        removeNPC(villager.getEntityId());
        spawnNPC(villager);
    }

    public void spawnNPC(Villager villager) {
        if (isInvalid(villager)) return;

        int entityId = villager.getEntityId();
        if (hasNPC(entityId)) return;

        WrappedSignedProperty textures = getTextures(villager);
        if (textures.getName().equals("error")) {
            plugin.getLogger().severe(textures.getValue());
            return;
        }

        String defaultName = plugin.getConverter().getNPC(villager)
                .map(IVillagerNPC::getVillagerName)
                .orElse(villager.getName());

        String name;
        if (Config.DISABLE_NAMETAGS.asBool() || defaultName.equals(HIDE_NAMETAG_NAME)) {
            name = HIDE_NAMETAG_NAME;
            checkNametagTeam();
        } else {
            name = defaultName;
        }

        WrappedGameProfile profile = new WrappedGameProfile(UUID.randomUUID(), name);
        profile.getProperties().put("textures", textures);

        NPC.builder()
                .profile(profile)
                .location(villager.getLocation())
                .spawnCustomizer(new NPCHandler(plugin, villager))
                .entityId(entityId)
                .usePlayerProfiles(false)
                .lookAtPlayer(false)
                .imitatePlayer(false)
                .build(pool);
    }

    private void checkNametagTeam() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getMainScoreboard();

        Team team = getNametagTeam(scoreboard);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addEntry(HIDE_NAMETAG_NAME);
    }

    private Team getNametagTeam(Scoreboard scoreboard) {
        Team team = scoreboard.getTeam(NAMETAG_TEAM_NAME);
        return team != null ? team : scoreboard.registerNewTeam(NAMETAG_TEAM_NAME);
    }

    public WrappedSignedProperty getTextures(Villager villager) {
        IVillagerNPC npc = plugin.getConverter().getNPC(villager).orElse(null);
        if (npc == null) {
            return error("Invalid textures! The villager {" + villager.getEntityId() + "} isn't a custom one.");
        }

        Pair<File, FileConfiguration> pair = getFile(plugin.getSkinFolder(), npc.getSex() + ".yml");
        FileConfiguration config = pair.getSecond();

        String profession = villager.getProfession().name().toLowerCase();

        ConfigurationSection section = config.getConfigurationSection(profession);
        if (section == null) {
            return error("Invalid textures! No section found for {" + profession + "}.");
        }

        int which = getSkinId(npc, section.getKeys(false));
        return getTextures(config, profession, which);
    }

    public WrappedSignedProperty getTextures(String sex, String profession, int which) {
        Pair<File, FileConfiguration> pair = getFile(plugin.getSkinFolder(), sex + ".yml");
        FileConfiguration config = pair.getSecond();

        return getTextures(config, profession, which);
    }

    private WrappedSignedProperty getTextures(FileConfiguration config, String profession, int which) {
        String texture = config.getString(profession + "." + which + ".texture");
        String signature = config.getString(profession + "." + which + ".signature");

        if (texture != null && signature != null) {
            return new WrappedSignedProperty("textures", texture, signature);
        }

        return error("Invalid textures! No skin found for id {" + which + "}.");
    }

    private WrappedSignedProperty error(String message) {
        return WrappedSignedProperty.fromValues("error", message, "");
    }

    private int getSkinId(IVillagerNPC npc, Set<String> ids) {
        int id = npc.getSkinTextureId();

        if (id == -1 || !ids.contains(id + "")) {
            int which = ThreadLocalRandom.current().nextInt(1, ids.size() + 1);
            npc.setSkinTextureId(which);
            return which;
        }

        return id;
    }

    public boolean fixSleep() {
        return !handler.getSleeping().isEmpty();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private Pair<File, FileConfiguration> loadFile(@Nullable String folder, String fileName) {
        File file = new File(folder != null ? folder : plugin.getDataFolder().getPath(), fileName);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        }
        FileConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }
        return new Pair<>(file, configuration);
    }

    public Pair<File, FileConfiguration> getFile(String fileName) {
        return getFile(null, fileName);
    }

    public Pair<File, FileConfiguration> getFile(@Nullable String folder, String fileName) {
        return files.computeIfAbsent(fileName, name -> loadFile(folder, fileName));
    }

    public String getRandomNameBySex(String sex) {
        Pair<File, FileConfiguration> pair = getFile("names.yml");
        FileConfiguration config = pair.getSecond();

        List<String> names = config.getStringList(sex);

        String name = "";
        do {
            if (names.isEmpty()) break;
            int index = ThreadLocalRandom.current().nextInt(names.size());
            name = names.remove(index);
        } while (!isValidName(name));

        return isValidName(name) ? name : HIDE_NAMETAG_NAME;
    }

    public boolean isValidName(String name) {
        return name.matches("\\w{3,16}");
    }
}