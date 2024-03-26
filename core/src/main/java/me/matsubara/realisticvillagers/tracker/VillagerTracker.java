package me.matsubara.realisticvillagers.tracker;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.comphenix.protocol.wrappers.WrappedSignedProperty;
import com.google.common.collect.Lists;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.event.RealisticRemoveEvent;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.handler.npc.NPCHandler;
import me.matsubara.realisticvillagers.handler.protocol.VillagerHandler;
import me.matsubara.realisticvillagers.listener.spawn.BukkitSpawnListeners;
import me.matsubara.realisticvillagers.listener.spawn.PaperSpawnListeners;
import me.matsubara.realisticvillagers.manager.NametagManager;
import me.matsubara.realisticvillagers.npc.NPC;
import me.matsubara.realisticvillagers.npc.NPCPool;
import me.matsubara.realisticvillagers.task.PreviewTask;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mineskin.MineskinClient;
import org.mineskin.SkinOptions;
import org.mineskin.Variant;
import org.mineskin.Visibility;
import org.mineskin.data.Skin;
import org.mineskin.data.Texture;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Logger;

import static com.comphenix.protocol.PacketType.Play.Server.*;

@Getter
public final class VillagerTracker implements Listener {

    private final RealisticVillagers plugin;
    private final NPCPool pool;
    private final BukkitSpawnListeners spawnListeners;
    private final Map<UUID, String> transformations = new HashMap<>();
    private final Map<UUID, Integer> portalTransform = new HashMap<>();
    private final Set<IVillagerNPC> offlineVillagers = ConcurrentHashMap.newKeySet();
    private final Map<String, Pair<File, FileConfiguration>> files = new HashMap<>();
    private final Map<UUID, String> selectedProfession = new HashMap<>();
    private final Map<UUID, PreviewTask> previews = new HashMap<>();
    private final VillagerHandler handler;
    private final MineskinClient mineskinClient;
    private final Random random = new Random();

    private static final String NAMETAG_TEAM_NAME = "RVNametag";
    public static final String HIDE_NAMETAG_NAME = "abcdefghijklmnño";
    private static final Predicate<Entity> APPLY_FOR_TRANSFORM = entity -> entity instanceof Villager || entity instanceof ZombieVillager;
    private static final List<String> WITHOUT_HAT = Arrays.asList("cleric", "leatherworker", "mason", "nitwit", "toolsmith");

    public VillagerTracker(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.pool = new NPCPool(plugin);
        this.spawnListeners = new BukkitSpawnListeners(plugin);

        this.mineskinClient = new MineskinClient("MineSkin-JavaClient");
        updateMineskinApiKey();

        PluginManager manager = plugin.getServer().getPluginManager();

        PaperSpawnListeners paperListener = new PaperSpawnListeners(plugin);
        if (!paperListener.isRegistered()) manager.registerEvents(spawnListeners, plugin);

        manager.registerEvents(this, plugin);

        @SuppressWarnings("deprecation") ArrayList<PacketType> types = Lists.newArrayList(SPAWN_ENTITY, SPAWN_ENTITY_LIVING, ENTITY_STATUS);
        if (plugin.getServer().getPluginManager().getPlugin("ViaVersion") != null) {
            types.add(ENTITY_METADATA);
        }

        ProtocolManager protocol = ProtocolLibrary.getProtocolManager();
        protocol.addPacketListener(handler = new VillagerHandler(plugin, types));
    }

    public void updateMineskinApiKey() {
        String apiKey = Config.MINESKIN_API_KEY.asString();
        mineskinClient.setApiKey(apiKey.isEmpty() ? null : apiKey);
    }

    public @Nullable IVillagerNPC getOfflineByUUID(UUID uuid) {
        for (IVillagerNPC info : offlineVillagers) {
            if (info.getUniqueId().equals(uuid)) return info;
        }
        return null;
    }

    private void removeData(@NotNull LivingEntity living) {
        IVillagerNPC info = getOfflineByUUID(living.getUniqueId());
        if (info != null) offlineVillagers.remove(info);
    }

    public IVillagerNPC getOffline(UUID uuid) {
        if (uuid == null) return null;

        // Try to send updated data.
        if (Bukkit.getEntity(uuid) instanceof AbstractVillager villager) {
            return updateData(villager);
        }

        return getOfflineByUUID(uuid);
    }

    private void markAsDeath(LivingEntity living) {
        handlePartner(living);
        removeData(living);
    }

    private void handlePartner(LivingEntity living) {
        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(living);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;

        // Can be a player or a villager (there's no chance a villager and a player share the same UUID).
        IVillagerNPC partner = npc.getPartner();
        if (partner == null) return;

        if (npc.isPartnerVillager()) {
            handleVillagerPartner(npc, partner);
            return;
        }

        Player player = Bukkit.getPlayer(partner.getUniqueId());
        if (player != null && player.isOnline()) {
            player.getPersistentDataContainer().remove(plugin.getMarriedWith());
            return;
        }

        // Player is offline, we need to modify the NBT file (if possible).
        File playerFile = getPlayerNBTFile(partner.getUniqueId());
        if (playerFile != null) plugin.getConverter().removePartnerFromPlayerNBT(playerFile);
    }

    private void handleVillagerPartner(IVillagerNPC deadNPC, @NotNull IVillagerNPC partner) {
        if (!(Bukkit.getEntity(partner.getUniqueId()) instanceof Villager villager)) return;

        plugin.getConverter().getNPC(villager).ifPresent(partnerNPC -> {
            partnerNPC.getPartners().add(deadNPC.getOffline());
            partnerNPC.setPartner(null, false);
        });
    }

    public @Nullable File getPlayerNBTFile(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            File data = new File(world.getWorldFolder(), "playerdata");

            File playerFile = new File(data, uuid.toString() + ".dat");
            if (!playerFile.exists()) continue;
            return playerFile;
        }
        return null;
    }

    @EventHandler
    public void onEntitiesUnload(@NotNull EntitiesUnloadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (!(entity instanceof AbstractVillager villager) || isInvalid(villager, true)) continue;
            updateData(villager);
            removeNPC(entity.getEntityId());
        }
    }

    public @Nullable IVillagerNPC updateData(LivingEntity living) {
        // Update the data after being unloaded.
        Optional<IVillagerNPC> npc = plugin.getConverter().getNPC(living);
        if (npc.isEmpty()) return null;

        removeData(living);

        IVillagerNPC offline = npc.get().getOffline();
        offlineVillagers.add(offline);
        return offline;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityTransform(@NotNull EntityTransformEvent event) {
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

        // If villager isn't custom, the tag will be null.
        transformations.put(transformed.getUniqueId(), plugin.getConverter().getNPCTag((LivingEntity) entity, isInfection));
    }

    @EventHandler
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        if (!(event.getEntity() instanceof AbstractVillager villager)) return;
        markAsDeath(villager);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> removeNPC(event.getEntity().getEntityId()), 40L);
    }

    @EventHandler
    public void onVillagerRemove(@NotNull RealisticRemoveEvent event) {
        IVillagerNPC npc = event.getNPC();
        handler.getAllowSpawn().remove(npc.getUniqueId());

        LivingEntity bukkit = npc.bukkit();
        if (isInvalid(bukkit, true)) return;

        RealisticRemoveEvent.RemovalReason reason = event.getReason();
        if (reason != RealisticRemoveEvent.RemovalReason.DISCARDED) {
            if (reason != RealisticRemoveEvent.RemovalReason.KILLED) updateData(bukkit);
            return;
        }

        markAsDeath(bukkit);
        removeNPC(bukkit.getEntityId());
    }

    @EventHandler
    public void onEntityPortal(@NotNull EntityPortalEvent event) {
        if (event.getEntity() instanceof AbstractVillager villager) {
            portalTransform.put(villager.getUniqueId(), villager.getEntityId());
        }
    }

    @EventHandler
    public void onEntityPortalEnter(@NotNull EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof AbstractVillager villager)) return;

        int previous = portalTransform.getOrDefault(villager.getUniqueId(), -1);
        if (previous == -1) return;

        removeNPC(previous);

        Optional<IVillagerNPC> npc = plugin.getConverter().getNPC(villager);
        if (npc.isPresent()) spawnNPC(villager);
    }

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        PersistentDataContainer container = event.getPlayer().getPersistentDataContainer();

        String partner = container.get(plugin.getMarriedWith(), PersistentDataType.STRING);
        if (partner == null) return;

        UUID uuid = UUID.fromString(partner);

        IVillagerNPC offline = getOffline(uuid);
        if (offline == null) container.remove(plugin.getMarriedWith());
    }

    @EventHandler
    public void onEntityPotionEffect(@NotNull EntityPotionEffectEvent event) {
        if (!event.getModifiedType().equals(PotionEffectType.INVISIBILITY)) return;

        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (isInvalid(living)) return;

        NametagManager nametagManager = plugin.getNametagManager();
        if (nametagManager == null) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getConverter().getNPC(living).ifPresent(npc -> {
            EntityPotionEffectEvent.Action action = event.getAction();
            if (action == EntityPotionEffectEvent.Action.CLEARED || action == EntityPotionEffectEvent.Action.REMOVED) {
                nametagManager.resetNametag(npc, null, true);
            } else {
                nametagManager.remove(npc);
            }
        }));
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID uniqueId = event.getPlayer().getUniqueId();
        selectedProfession.remove(uniqueId);
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

    public boolean isInvalid(@NotNull LivingEntity living, boolean ignoreSkinState) {
        return (!ignoreSkinState && Config.DISABLE_SKINS.asBool())
                || (!(living instanceof WanderingTrader) && (!(living instanceof Villager villager) || !plugin.getCompatibilityManager().shouldTrack(villager)))
                || plugin.isDisabledIn(living.getWorld())
                || plugin.getConverter().getNPC(living).isEmpty();
    }

    public boolean isInvalid(LivingEntity living) {
        return isInvalid(living, false);
    }

    public void spawnNPC(LivingEntity living) {
        if (isInvalid(living)) return;

        int entityId = living.getEntityId();
        if (hasNPC(entityId)) return;

        WrappedSignedProperty textures = getTextures(living);
        if (textures.getName().equals("error")) {
            CompletableFuture<Skin> creator = getCreator(living, textures);
            if (creator != null)
                creator.thenAcceptAsync(skin -> spawnNPC(living), mineskinClient.getRequestExecutor());
            return;
        }

        Optional<IVillagerNPC> optional = plugin.getConverter().getNPC(living);

        IVillagerNPC npc = optional.orElse(null);
        if (npc == null) return;

        String defaultName = npc.getVillagerName();

        String name;
        if (Config.DISABLE_NAMETAGS.asBool()
                || defaultName.equals(HIDE_NAMETAG_NAME)
                || isInvalidNametag(defaultName)) {
            name = HIDE_NAMETAG_NAME;
            checkNametagTeam();
        } else {
            // Only show nametag if it's valid.
            name = defaultName;
        }

        WrappedGameProfile profile = new WrappedGameProfile(UUID.randomUUID(), name);
        profile.getProperties().put("textures", textures);

        NPC.builder()
                .profile(profile)
                .spawnCustomizer(new NPCHandler(plugin))
                .entityId(entityId)
                .entity(npc)
                .build(pool);
    }

    public void checkNametagTeam() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;

        Scoreboard scoreboard = manager.getMainScoreboard();

        Team team = getNametagTeam(scoreboard);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        team.addEntry(HIDE_NAMETAG_NAME);
    }

    private @NotNull Team getNametagTeam(@NotNull Scoreboard scoreboard) {
        Team team = scoreboard.getTeam(NAMETAG_TEAM_NAME);
        return team != null ? team : scoreboard.registerNewTeam(NAMETAG_TEAM_NAME);
    }

    public @NotNull WrappedSignedProperty getTextures(LivingEntity living) {
        SkinRelatedData data = getRelatedData(living, null, false);

        WrappedSignedProperty property = data.property();
        if (property != null && property.getName().equals("error")) {
            return property;
        }

        return getTextures(data.storage(), data.sex(), data.profession(), data.id());
    }

    @Contract("_, _ -> new")
    public @NotNull SkinRelatedData getRelatedData(LivingEntity living, @Nullable String differentProfession) {
        return getRelatedData(living, differentProfession, true);
    }

    @Contract("_, _, _ -> new")
    public @NotNull SkinRelatedData getRelatedData(LivingEntity living, @Nullable String differentProfession, boolean random) {
        IVillagerNPC npc = plugin.getConverter().getNPC(living).orElse(null);
        if (npc == null) {
            return new SkinRelatedData(null, null, -1, null, null, error("Invalid textures! The villager {" + living.getEntityId() + "} isn't a custom one.", "true"));
        }

        String sex = npc.getSex();
        String profession = PluginUtils.getProfessionOrType(living);

        String sexFile = sex + ".yml";
        Pair<File, FileConfiguration> pair = getFile(sexFile);
        FileConfiguration config = pair.getSecond();

        ConfigurationSection section = config.getConfigurationSection(differentProfession != null ? differentProfession : profession);
        if (section == null) {
            return new SkinRelatedData(null, null, -1, null, null, error(
                    "Invalid textures! No section found for {" + profession + "} in {" + sexFile + "}.",
                    profession.equals("none") ? "true" : "false"));
        }

        Set<String> originalIds = section.getKeys(false);
        int which = getSkinId(npc, originalIds, getModifiedKeys(config, living, originalIds), random);
        return new SkinRelatedData(sex, profession, which, config, section, null);
    }

    private Set<String> getModifiedKeys(FileConfiguration config, @NotNull LivingEntity living, Set<String> keys) {
        Set<String> newKeys = new LinkedHashSet<>(keys);

        boolean isAdult = !(living instanceof Villager villager) || villager.isAdult();
        newKeys.removeIf(key -> {
            boolean forBabies = config.getBoolean("none." + key + ".for-babies");
            return (isAdult && forBabies) || (!isAdult && !forBabies);
        });

        return newKeys.isEmpty() ? keys : newKeys;
    }

    public @NotNull WrappedSignedProperty getTextures(String sex, String profession, int which) {
        Pair<File, FileConfiguration> pair = getFile(sex + ".yml");
        FileConfiguration config = pair.getSecond();

        return getTextures(config, sex, profession, which);
    }

    private @NotNull WrappedSignedProperty getTextures(@NotNull FileConfiguration config, String sex, String profession, int which) {
        String texture = config.getString(profession + "." + which + ".texture");
        String signature = config.getString(profession + "." + which + ".signature");

        if (texture != null && signature != null) {
            return new WrappedSignedProperty("textures", texture, signature);
        }

        return error("Invalid textures! No skin found for id {" + which + "} with profession of {" + profession + "} and sex of {" + sex + "}.", "false");
    }

    @Contract("_, _ -> new")
    private @NotNull WrappedSignedProperty error(String message, String severe) {
        return WrappedSignedProperty.fromValues("error", message, severe);
    }

    private int getSkinId(@NotNull IVillagerNPC npc, Set<String> originalIds, @NotNull Set<String> ids, boolean random) {
        int id = npc.getSkinTextureId(), kidId = npc.getKidSkinTextureId();

        String idAsString = String.valueOf(id);
        if (ids.contains(idAsString)) return id;
        if (id == kidId && originalIds.contains(idAsString)) return id;

        if (!random) return -1;

        int newId = Integer.parseInt(new ArrayList<>(ids).get(this.random.nextInt(0, ids.size())));
        npc.setSkinTextureId(newId);
        if (kidId == -1) npc.setKidSkinTextureId(newId);

        return newId;
    }

    public Pair<File, FileConfiguration> getFile(String fileName) {
        return files.get(fileName);
    }

    public String getRandomNameBySex(String sex) {
        Pair<File, FileConfiguration> pair = getFile("names.yml");
        FileConfiguration config = pair.getSecond();

        List<String> names = config.getStringList(sex);

        String name = "";
        do {
            if (names.isEmpty()) break;
            int index = random.nextInt(names.size());
            name = names.remove(index);
        } while (name.length() < 3);

        return name.length() >= 3 ? name : HIDE_NAMETAG_NAME;
    }

    @Contract(pure = true)
    public boolean isInvalidNametag(@NotNull String name) {
        return !name.matches("\\w{3,16}");
    }

    public void addNewSkin(CommandSender sender, Integer id, String profession, String sex, boolean isAdult, String texture, String signature) {
        Pair<File, FileConfiguration> pair = getFile(sex + ".yml");
        FileConfiguration config = pair.getSecond();

        // Find available id.
        int key = -1;
        if (id != null) {
            key = id;
        } else {
            // "none" is the root of new skins.
            ConfigurationSection section = config.getConfigurationSection("none");
            if (section != null) {
                Set<String> keys = section.getKeys(false);
                int size = keys.size();
                do {
                    key = random.nextInt(1, (size == 0 ? 1 : size) * 2 + 1);
                } while (key == -1 || keys.contains(String.valueOf(key)));
            }

            if (key == -1) {
                plugin.getLogger().severe("Couldn't find valid free id for a new skin!");
                return;
            }
        }

        config.set(profession + "." + key + ".texture", texture);
        config.set(profession + "." + key + ".signature", signature);

        // This data is only important for none.
        if (profession.equalsIgnoreCase("none")) {
            config.set("none." + key + ".added-by", sender instanceof Player player ? player.getUniqueId().toString() : "Console");
            config.set("none." + key + ".when", System.currentTimeMillis());
            if (!isAdult) config.set("none." + key + ".for-babies", true);
        }

        try {
            int finalKey = key;
            config.save(pair.getFirst());
            plugin.getMessages().send(sender, Messages.Message.SKIN_ADDED, string -> string
                    .replace("%id%", String.valueOf(finalKey))
                    .replace("%profession%", plugin.getProfessionFormatted(profession))
                    .replace("%sex%", (sex.equals("male") ? Config.MALE : Config.FEMALE).asString())
                    .replace("%age-stage%", (isAdult ? Config.ADULT : Config.KID).asString()));
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public @Nullable CompletableFuture<Skin> createSkin(CommandSender sender, String sex, boolean isAdult, String profession, int id) {
        try {
            Messages messages = plugin.getMessages();
            Logger logger = plugin.getLogger();

            WrappedSignedProperty textures = getTextures(sex, "none", id);
            if (textures.getName().equals("error")) {
                messages.send(sender, Messages.Message.SKIN_ERROR);
                logger.severe(textures.getValue());
                return null;
            }

            String url = PluginUtils.getURLFromTexture(textures.getValue());

            BufferedImage image = PluginUtils.convertTo64x64(ImageIO.read(new URL(url)));

            int pixelColorTop = image.getRGB(55, 20);
            int pixelColorBottom = image.getRGB(55, 52);

            Color colorTop = new Color(pixelColorTop);
            Color colorBottom = new Color(pixelColorBottom);

            String fileName = profession.replace("-", "_");

            BufferedImage professionOverlay = null;
            boolean isBaseClassic = false;
            if (PluginUtils.isSteveSkin(colorTop) || PluginUtils.isSteveSkin(colorBottom)) {
                isBaseClassic = true;
                InputStream professionResource = plugin.getResource("overlay/" + fileName + "_steve.png");
                if (professionResource != null) professionOverlay = ImageIO.read(professionResource);
            } else {
                InputStream professionResource = plugin.getResource("overlay/" + fileName + "_alex.png");
                if (professionResource != null) professionOverlay = ImageIO.read(professionResource);
            }

            if (professionOverlay == null) {
                String extra;
                if (profession.equalsIgnoreCase("fisherman")) {
                    // There are 2 variants of fisherman.
                    extra = random.nextBoolean() ? "_cod" : "_salmon";
                } else extra = "";

                String overlayPath = "overlay/" + fileName + extra + ".png";
                InputStream resource = plugin.getResource(overlayPath);
                if (resource == null) {
                    if (!profession.equals("none")) {
                        messages.send(sender, Messages.Message.SKIN_ERROR);
                        logger.severe("Couldn't find an overlay for {" + overlayPath + "}!");
                    }
                    return null;
                }
                professionOverlay = ImageIO.read(resource);
            }

            BufferedImage combined = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);

            Graphics2D graphics = combined.createGraphics();

            // First we draw the default skin (removing the skin hat if necessary).
            graphics.drawImage(!WITHOUT_HAT.contains(profession) ? PluginUtils.removeHat(image) : image, 0, 0, null);

            // Then, we draw the profession custome.
            graphics.drawImage(PluginUtils.convertTo64x64(professionOverlay), 0, 0, null);
            graphics.dispose();

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            ImageIO.write(combined, "PNG", stream);

            return mineskinClient.generateUpload(
                            combined,
                            SkinOptions.create(String.format("%s-%s", profession, id), isBaseClassic ? Variant.CLASSIC : Variant.SLIM, Visibility.PRIVATE))
                    .thenApplyAsync(skin -> {
                        Texture texture = skin.data.texture;
                        addNewSkin(sender, id, profession, sex, isAdult, texture.value, texture.signature);
                        return skin;
                    }, mineskinClient.getRequestExecutor());
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    public boolean shouldRename(@NotNull String name) {
        return name.isEmpty() || name.equals(VillagerTracker.HIDE_NAMETAG_NAME);
    }

    public void refreshNPCSkin(LivingEntity living, boolean happyParticles) {
        // If the skin doesn't exist, first we create it, THEN we refresh.
        WrappedSignedProperty textures = getTextures(living);
        if (!textures.getName().equals("error")) {
            refreshNPC(living);
            return;
        }

        CompletableFuture<Skin> creator = getCreator(living, textures);
        if (creator == null) return;

        creator.thenAcceptAsync(skin -> refreshNPC(living), mineskinClient.getRequestExecutor());

        if (!happyParticles) return;

        // Spawn happy particles every 0.5 seconds.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (creator.isDone()) {
                    cancel();
                    return;
                }
                if (random.nextInt(35) == 0) living.playEffect(EntityEffect.VILLAGER_HAPPY);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private @Nullable CompletableFuture<Skin> getCreator(LivingEntity living, @NotNull WrappedSignedProperty textures) {
        Logger logger = plugin.getLogger();

        // Only log if error is severe.
        if (textures.getSignature().equalsIgnoreCase("true")) {
            logger.severe(textures.getValue());
            return null;
        }

        VillagerTracker.SkinRelatedData data = getRelatedData(living, "none");

        WrappedSignedProperty property = data.property();
        if (property != null && property.getName().equals("error")) {
            logger.severe(property.getValue());
            return null;
        }

        // If skin already exists, it'll be used in the next iteration.
        if (!getTextures(data.sex(), data.profession(), data.id()).getName().equals("error")) {
            return CompletableFuture.supplyAsync(() -> null);
        }

        CompletableFuture<Skin> future = createSkin(plugin.getServer().getConsoleSender(), data.sex(), !(living instanceof Villager villager) || villager.isAdult(), data.profession(), data.id());
        if (future == null) {
            if (!data.profession().equals("none")) {
                logger.severe("Failed to generate a new skin when trying to spawn/refresh: " + data + "!");
            }
            return null;
        }

        return future;
    }

    private void refreshNPC(@NotNull LivingEntity living) {
        removeNPC(living.getEntityId());
        spawnNPC(living);
    }

    public record SkinRelatedData(String sex,
                                  String profession,
                                  int id,
                                  FileConfiguration storage,
                                  ConfigurationSection section,
                                  WrappedSignedProperty property) {

        @Contract(pure = true)
        @Override
        public @NotNull String toString() {
            return "{" + sex + ":" + profession + ":" + id + "}";
        }
    }
}