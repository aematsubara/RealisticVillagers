package me.matsubara.realisticvillagers;

import me.matsubara.realisticvillagers.village.BorderVisualizer;
import me.matsubara.realisticvillagers.village.ElectionManager;
import me.matsubara.realisticvillagers.village.MayorManager;
import me.matsubara.realisticvillagers.village.VillageManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jeff_media.morepersistentdatatypes.datatypes.serializable.ConfigurationSerializableDataType;
import com.tchristofferson.configupdater.ConfigUpdater;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.command.GenderCommand;
import me.matsubara.realisticvillagers.command.MainCommand;
import me.matsubara.realisticvillagers.compatibility.*;
import me.matsubara.realisticvillagers.data.ItemLoot;
import me.matsubara.realisticvillagers.data.serialization.GossipEntryWrapper;
import me.matsubara.realisticvillagers.data.serialization.OfflineDataWrapper;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.hologram.HologramListener;
import me.matsubara.realisticvillagers.hologram.HologramManager;
import net.wesjd.anvilgui.AnvilGUI;
import me.matsubara.realisticvillagers.gui.types.WhistleGUI;
import me.matsubara.realisticvillagers.listener.*;
import me.matsubara.realisticvillagers.manager.AnnoyingMeterManager;
import me.matsubara.realisticvillagers.manager.ChestManager;
import me.matsubara.realisticvillagers.manager.ExpectingManager;
import me.matsubara.realisticvillagers.manager.InteractCooldownManager;
import me.matsubara.realisticvillagers.manager.gift.Gift;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.manager.gift.GiftManager;
import me.matsubara.realisticvillagers.manager.revive.ReviveManager;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.*;
import me.matsubara.realisticvillagers.util.customblockdata.CustomBlockData;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bstats.bukkit.Metrics;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.inventory.*;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.Metadatable;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.function.*;
import java.util.logging.Logger;

@Getter
public final class RealisticVillagers extends JavaPlugin {

    private final NamespacedKey giftKey = key("GiftUUID");
    private final NamespacedKey marriedWith = key("MarriedWith");
    private final NamespacedKey procreationKey = key("Procreation");
    private final NamespacedKey motherUUIDKey = key("MotherUUID");
    private final NamespacedKey isRingKey = key("IsRing");
    private final NamespacedKey isWhistleKey = key("IsWhistle");
    private final NamespacedKey isCrossKey = key("IsCross");
    private final NamespacedKey entityTypeKey = key("EntityType");
    private final NamespacedKey chatInteractionTypeKey = key("ChatInteractionType");
    private final NamespacedKey childNameKey = key("ChildName");
    private final NamespacedKey childSexKey = key("ChildSex");
    private final NamespacedKey zombieTransformKey = key("ZombieTransform");
    private final NamespacedKey fishedKey = key("Fished");
    private final NamespacedKey bedVillagerKey = key("BedVillager");
    private final NamespacedKey playerSexKey = key("PlayerSex");
    private final @Getter(AccessLevel.NONE) NamespacedKey shapeRollKey = key("PlayerShapeRoll");

    @ApiStatus.Internal
    private final @Getter(AccessLevel.NONE) NamespacedKey valuesKey = key("RValues"); // New main key.
    private final NamespacedKey inventoryKey = key("RInventory");
    @ApiStatus.Internal
    private final @Getter(AccessLevel.NONE) NamespacedKey npcValuesKey = key("VillagerNPCValues"); // Previous main key.

    public NamespacedKey getNpcValuesKey() {
        VersionMatcher matcher = VersionMatcher.getByMinecraftVersion();
        return matcher != null && matcher.higherOrEqualThan(VersionMatcher.v1_21_8) ? valuesKey
                : getLegacyNpcValuesKey();
    }

    @ApiStatus.Internal
    public NamespacedKey getLegacyNpcValuesKey() {
        return npcValuesKey;
    }

    private final @ApiStatus.Internal NamespacedKey tamedByPlayerKey = key("TamedByPlayer");
    private final NamespacedKey tamedByVillagerKey = key("TamedByVillager");
    private final NamespacedKey isBeingLootedKey = key("IsBeingLooted");
    private final @ApiStatus.Internal NamespacedKey ignoreVillagerKey = key("IgnoreVillager");
    private final NamespacedKey villagerUUIDKey = key("VillagerUUID");
    private final NamespacedKey divorcePapersKey = key("DivorcePapers");
    private final NamespacedKey raidStatsKey = key("RaidStats");
    private final NamespacedKey skinDataKey = key("SkinDataID");
    private final NamespacedKey ignoreItemKey = key("IgnoreItem");
    private final NamespacedKey playerUUIDKey = new NamespacedKey(this, "PlayerUUID");
    private final NamespacedKey itemIdKey = new NamespacedKey(this, "ItemID");

    private InventoryListeners inventoryListeners;
    private OtherListeners otherListeners;
    private PlayerListeners playerListeners;
    private VillagerListeners villagerListeners;
    private HologramManager hologramManager;

    private VillagerTracker tracker;
    private @Setter Shape ring;
    private @Setter Shape whistle;
    private @Setter Shape cross;

    private AnnoyingMeterManager annoyingManager;
    private ReviveManager reviveManager;
    private GiftManager giftManager;
    private ChestManager chestManager;
    private ExpectingManager expectingManager;
    private InteractCooldownManager cooldownManager;
    private CompatibilityManager compatibilityManager;
    private VillageManager villageManager;
    private MayorManager mayorManager;
    private ElectionManager electionManager;
    private BorderVisualizer borderVisualizer;
    private me.matsubara.realisticvillagers.task.VillagePresence villagePresence;
    private me.matsubara.realisticvillagers.task.VillageEconomy villageEconomy;
    private me.matsubara.realisticvillagers.task.VillageHarvest villageHarvest;
    private me.matsubara.realisticvillagers.village.Blueprints blueprints;
    private me.matsubara.realisticvillagers.village.VillageDiscovery villageDiscovery;
    private me.matsubara.realisticvillagers.task.HoverNametagTask hoverNametagTask;
    private me.matsubara.realisticvillagers.appearance.ShapeEquipment shapeEquipment;
    private me.matsubara.realisticvillagers.appearance.PlayerAppearanceManager playerAppearanceManager;
    private me.matsubara.realisticvillagers.appearance.PackServer packServer;

    /**
     * Whether the pack the figures are made of is being served at all.
     * <p>
     * Height does not need it — a villager is simply built taller or shorter, and the client draws
     * that on its own — but every figure does, because a figure is an item whose model lives in
     * the pack. A client that has not got the pack does not draw nothing in its place: it draws the
     * placeholder it uses for any model it cannot resolve, which reads as a figure that came out
     * badly rather than as a pack that never arrived. So when the pack cannot be served, the part
     * that depends on it turns itself off and the heights stay.
     */
    private boolean shapesServed = true;
    private me.matsubara.realisticvillagers.task.AppearanceTask appearanceTask;
    private me.matsubara.realisticvillagers.task.GraveManager graveManager;
    private me.matsubara.realisticvillagers.village.VillageCrafting villageCrafting;
    private me.matsubara.realisticvillagers.village.ConstructionManager constructionManager;
    private me.matsubara.realisticvillagers.village.BuildPreview buildPreview;

    private FileConfiguration guiConfig, lootConfig, variableTextConfig, hologramConfig, giftsConfig;

    private Messages messages;
    private INMSConverter converter;

    private final List<String> defaultTargets = new ArrayList<>();
    private final Set<Gift> wantedItems = new HashSet<>();
    private final Map<String, List<ItemLoot>> loots = new HashMap<>();
    private final Consumer<File> loadConsumer = file -> tracker.getFiles().put(file.getName(),
            Pair.of(file, YamlConfiguration.loadConfiguration(file)));

    private List<String> worlds;

    private static final String VILLAGER_HEAD_TEXTURE = "4ca8ef2458a2b10260b8756558f7679bcb7ef691d41f534efea2ba75107315cc";
    private static final String UNKNOWN_HEAD_TEXTURE = "badc048a7ce78f7dad72a07da27d85c0916881e5522eeed1e3daf217a38c1a";

    // We want ListenMode to ignore our entities.
    public static final BiConsumer<JavaPlugin, Metadatable> LISTEN_MODE_IGNORE = (plugin, living) -> living
            .setMetadata("RemoveGlow", new FixedMetadataValue(plugin, true));

    public static final List<AnvilGUI.ResponseAction> CLOSE_RESPONSE = Collections
            .singletonList(AnvilGUI.ResponseAction.close());

    private static final List<String> FILTER_TYPES = List.of("WHITELIST", "BLACKLIST");
    // Sections in config.yml that ConfigUpdater must not overwrite (user-defined
    // items/data).
    private static final Set<String> SPECIAL_SECTIONS = Sets.newHashSet(
            "baby",
            "wedding-ring",
            "whistle",
            "divorce-papers",
            "cross",
            "change-skin",
            "default-wanted-items",
            "revive.head-item");
    // Sections in gui.yml that ConfigUpdater must not overwrite.
    private static final Set<String> GUI_SPECIAL_SECTIONS = Sets.newHashSet("gui.main.frame");
    private static final List<String> GUI_TYPES = List.of("main", "equipment", "combat", "whistle", "skin", "new-skin");
    private static final int BSTATS_ID = 27463;
    private static final @SuppressWarnings("UnstableApiUsage") NamespacedKey MM_KEY = new NamespacedKey("mythicmobs",
            "type");

    static {
        // Register our data serializators.
        ConfigurationSerialization.registerClass(GossipEntryWrapper.class);
        ConfigurationSerialization.registerClass(OfflineDataWrapper.class);
    }

    public static final PersistentDataType<byte[], OfflineDataWrapper> VILLAGER_DATA = new ConfigurationSerializableDataType<>(
            OfflineDataWrapper.class);

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();

        long now = System.nanoTime();

        Logger logger = getLogger();
        logger.info("****************************************");
        logger.info("Loading compatibilities...");

        compatibilityManager = new CompatibilityManager();

        // Shopkeeper, Citizens & (probably) RainbowsPro; for VillagerMarket, the
        // villager shouldn't have AI to work properly.
        compatibilityManager.addCompatibility(getName(),
                villager -> villager.hasAI() && !villager.hasMetadata("shopkeeper") && !villager.hasMetadata("NPC"));

        // General compatibilities.
        addCompatibility("EliteMobs", EMCompatibility::new);
        addCompatibility("ViaVersion", ViaCompatibility::new);
        addCompatibility("VillagerTradeLimiter", VTLCompatibility::new);
        addCompatibility("ValhallaMMO", me.matsubara.realisticvillagers.compatibility.ValhallaCompatibility::new);
        addCompatibility("MythicMobs",
                () -> villager -> !villager.getPersistentDataContainer().has(MM_KEY, PersistentDataType.STRING));

        logger.info("Compatibilities loaded!");
        logger.info("");
        logger.info("Registering custom entities...");

        String currentMC = Bukkit.getBukkitVersion().split("-")[0];
        VersionMatcher matcher = VersionMatcher.getByMinecraftVersion();
        if (matcher == null) {
            logger.severe(
                    "NMSConverter couldn't find a valid implementation for this server version (" + currentMC + ").");
        } else {
            if (!VersionMatcher.isExactMatch()) {
                logger.warning("Server version " + currentMC
                        + " is not officially supported. Using latest known NMS as fallback — some features may not work correctly.");
            }
        }
        if (matcher != null)
            try {
                Class<?> converterClass = Class.forName(
                        INMSConverter.class.getPackageName() + "." + matcher.getPackageName() + ".NMSConverter");
                Constructor<?> converterConstructor = converterClass.getConstructor(getClass());
                converter = (INMSConverter) converterConstructor.newInstance(this);
                converter.registerEntities();
                // Load default variable-text.yml from classpath so refreshSchedules() can read
                // schedules.
                try (java.io.InputStream stream = getResource("configs/variable-text.yml")) {
                    if (stream != null) {
                        variableTextConfig = YamlConfiguration.loadConfiguration(
                                new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8));
                        variableTextConfig.setDefaults(new MemoryConfiguration());
                    }
                } catch (java.io.IOException ignored) {
                }
                converter.refreshSchedules(); // Build timelines BEFORE WorldInitEvent fires.
            } catch (ReflectiveOperationException exception) {
                logger.severe("NMSConverter failed to load for server version " + currentMC + " (fallback NMS: "
                        + matcher.getPackageName() + ").");
                exception.printStackTrace();
            }

        logger.info("Custom entities registered!");
        logger.info("");

        logLoadingTime(true, now);

        logger.info("****************************************");
    }

    private void addCompatibility(String name, Supplier<Compatibility> supplier) {
        PluginManager manager = getServer().getPluginManager();
        if (manager.getPlugin(name) == null)
            return;

        compatibilityManager.addCompatibility(name, supplier.get());
    }

    public Messages getMessages() {
        return messages;
    }

    public INMSConverter getConverter() {
        return converter;
    }

    public VillagerTracker getTracker() {
        return tracker;
    }

    public AnnoyingMeterManager getAnnoyingManager() {
        return annoyingManager;
    }

    public ReviveManager getReviveManager() {
        return reviveManager;
    }

    public GiftManager getGiftManager() {
        return giftManager;
    }

    public ChestManager getChestManager() {
        return chestManager;
    }

    public ExpectingManager getExpectingManager() {
        return expectingManager;
    }

    public InteractCooldownManager getCooldownManager() {
        return cooldownManager;
    }

    public FileConfiguration getGuiConfig() {
        return guiConfig;
    }

    public FileConfiguration getLootConfig() {
        return lootConfig;
    }

    public FileConfiguration getVariableTextConfig() {
        return variableTextConfig;
    }

    public FileConfiguration getHologramConfig() {
        return hologramConfig;
    }

    public FileConfiguration getGiftsConfig() {
        return giftsConfig;
    }

    public CompatibilityManager getCompatibilityManager() {
        return compatibilityManager;
    }

    public List<String> getDefaultTargets() {
        return defaultTargets;
    }

    public Set<Gift> getWantedItems() {
        return wantedItems;
    }

    public Map<String, List<ItemLoot>> getLoots() {
        return loots;
    }

    public List<String> getWorlds() {
        return worlds;
    }

    public NamespacedKey getGiftKey() {
        return giftKey;
    }

    public NamespacedKey getMarriedWith() {
        return marriedWith;
    }

    public NamespacedKey getProcreationKey() {
        return procreationKey;
    }

    public NamespacedKey getMotherUUIDKey() {
        return motherUUIDKey;
    }

    public NamespacedKey getIsRingKey() {
        return isRingKey;
    }

    public NamespacedKey getIsWhistleKey() {
        return isWhistleKey;
    }

    public NamespacedKey getIsCrossKey() {
        return isCrossKey;
    }

    public NamespacedKey getEntityTypeKey() {
        return entityTypeKey;
    }

    public NamespacedKey getChatInteractionTypeKey() {
        return chatInteractionTypeKey;
    }

    public NamespacedKey getChildNameKey() {
        return childNameKey;
    }

    public NamespacedKey getChildSexKey() {
        return childSexKey;
    }

    public NamespacedKey getZombieTransformKey() {
        return zombieTransformKey;
    }

    public NamespacedKey getFishedKey() {
        return fishedKey;
    }

    public NamespacedKey getBedVillagerKey() {
        return bedVillagerKey;
    }

    public NamespacedKey getPlayerSexKey() {
        return playerSexKey;
    }

    /**
     * Records a player's gender, and draws her a new body whenever it actually
     * changes.
     * <p>
     * The count beside the gender is the whole of it. A villager's figure comes
     * from her id alone,
     * which is right for someone who is born once — but a player decides, and can
     * decide again, and
     * an answer fixed to an account would mean the only route to a different figure
     * was a different
     * account. Counting the changes and folding that count into the draw means
     * switching away and
     * back gives her somebody new, while sitting still gives her the same woman
     * every time.
     * <p>
     * Everything that sets a player's gender goes through here, so none of them
     * have to know this.
     *
     * @return whether the gender is different from what it was.
     */
    public boolean setPlayerSex(@NotNull org.bukkit.entity.Player player, @NotNull String sex) {
        var data = player.getPersistentDataContainer();

        String current = data.get(playerSexKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (sex.equalsIgnoreCase(current))
            return false;

        data.set(playerSexKey, org.bukkit.persistence.PersistentDataType.STRING, sex);
        data.set(shapeRollKey, org.bukkit.persistence.PersistentDataType.INTEGER, playerShapeRoll(player) + 1);

        // Said again straight away rather than left for the next pass, so the command
        // that changed
        // this is a command whose effect can be seen.
        if (shapeEquipment != null)
            shapeEquipment.refresh(player);

        return true;
    }

    /**
     * How many times this player's body has been drawn before. Zero until she first
     * changes it.
     */
    public int playerShapeRoll(@NotNull org.bukkit.entity.Player player) {
        return player.getPersistentDataContainer()
                .getOrDefault(shapeRollKey, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
    }

    public NamespacedKey getInventoryKey() {
        return inventoryKey;
    }

    public NamespacedKey getTamedByPlayerKey() {
        return tamedByPlayerKey;
    }

    public NamespacedKey getTamedByVillagerKey() {
        return tamedByVillagerKey;
    }

    public NamespacedKey getIsBeingLootedKey() {
        return isBeingLootedKey;
    }

    public NamespacedKey getIgnoreVillagerKey() {
        return ignoreVillagerKey;
    }

    public NamespacedKey getVillagerUUIDKey() {
        return villagerUUIDKey;
    }

    public NamespacedKey getDivorcePapersKey() {
        return divorcePapersKey;
    }

    public NamespacedKey getRaidStatsKey() {
        return raidStatsKey;
    }

    public NamespacedKey getSkinDataKey() {
        return skinDataKey;
    }

    public NamespacedKey getIgnoreItemKey() {
        return ignoreItemKey;
    }

    public NamespacedKey getPlayerUUIDKey() {
        return playerUUIDKey;
    }

    public NamespacedKey getItemIdKey() {
        return itemIdKey;
    }

    public Shape getRing() {
        return ring;
    }

    public Shape getWhistle() {
        return whistle;
    }

    public Shape getCross() {
        return cross;
    }

    public void setRing(Shape ring) {
        this.ring = ring;
    }

    public void setWhistle(Shape whistle) {
        this.whistle = whistle;
    }

    public void setCross(Shape cross) {
        this.cross = cross;
    }

    @Override
    public void onEnable() {
        long now = System.nanoTime();

        Logger logger = getLogger();
        logger.info("****************************************");

        PluginManager manager = getServer().getPluginManager();
        if (manager.getPlugin("packetevents") == null) {
            getLogger().severe("This plugin requires PacketEvents, disabling...");
            manager.disablePlugin(this);
            return;
        }

        if (converter == null) {
            logger.severe(
                    "NMSConverter failed to initialize — this server version is not supported. Disabling RealisticVillagers.");
            manager.disablePlugin(this);
            return;
        }

        // Enable bStats so we can track which versions we should keep supporting.
        new Metrics(this, BSTATS_ID);

        logger.info("Loading skin files...");

        saveResource("skins/female.yml");
        saveResource("skins/male.yml");

        logger.info("Skins loaded!");
        logger.info("");

        saveResource("configs/names/default.yml");

        saveDefaultConfig();
        saveResource("configs/gui.yml");
        saveResource("configs/loot.yml");
        saveResource("configs/variable-text.yml");
        saveResource("configs/holograms.yml");
        saveResource("configs/gifts.yml");
        messages = new Messages(this);

        logger.info("Updating configuration files...");

        // This may take some time at startup, but it's necessary only once.
        updateConfigs();

        logger.info("Configuration files updated!");
        logger.info("");
        logger.info("Creating managers...");

        annoyingManager = new AnnoyingMeterManager(this);
        reviveManager = new ReviveManager(this);
        giftManager = new GiftManager(this);
        chestManager = new ChestManager(this);
        expectingManager = new ExpectingManager(this);
        cooldownManager = new InteractCooldownManager(this);
        villageManager = new VillageManager(this);
        mayorManager = new MayorManager(this, villageManager);
        electionManager = new ElectionManager(this, villageManager, mayorManager);
        borderVisualizer = new BorderVisualizer(this);
        villagePresence = new me.matsubara.realisticvillagers.task.VillagePresence(this);
        villagePresence.start();
        villageEconomy = new me.matsubara.realisticvillagers.task.VillageEconomy(this, villageManager);
        villageEconomy.start();
        villageHarvest = new me.matsubara.realisticvillagers.task.VillageHarvest(this, villageManager);
        villageHarvest.start();
        villageCrafting = new me.matsubara.realisticvillagers.village.VillageCrafting(this);
        villageCrafting.load(getConfig().getConfigurationSection("village.crafting"));
        blueprints = new me.matsubara.realisticvillagers.village.Blueprints(this);
        blueprints.load();
        villageDiscovery = new me.matsubara.realisticvillagers.village.VillageDiscovery(this, villageManager);

        // Read again once the worlds exist.
        //
        // Plugins are enabled before the levels are prepared, so at this point
        // getWorlds() is
        // empty and everything kept inside a world folder — discovered blueprints, the
        // villages
        // themselves — reads as absent. That is not a harmless miss: the record of what
        // has
        // already been copied out of a village lives there too, so every village looked
        // new on
        // every restart and was written out again, six more near-identical houses at a
        // time.
        // The first tick is the earliest moment the worlds are actually there.
        getServer().getScheduler().runTask(this, () -> {
            villageManager.load();
            blueprints.load();
        });
        hoverNametagTask = new me.matsubara.realisticvillagers.task.HoverNametagTask(this);
        hoverNametagTask.start();

        // Appearance traits: the resource pack carries the shapes, each villager
        // carries her own
        // colours. Written out at start-up so changing the arithmetic changes the
        // model.
        if (me.matsubara.realisticvillagers.task.AppearanceTask.supported())
            try {
                java.io.File packFolder = new java.io.File(getDataFolder(), "pack");
                int shapes = me.matsubara.realisticvillagers.appearance.TraitPack.write(packFolder);
                getLogger().info("Appearance pack written: " + shapes + " shapes.");

                // Handed to players by the plugin rather than by an admin with a web server.
                java.io.File zip = new java.io.File(getDataFolder(), "appearance-pack.zip");
                if (Config.APPEARANCE_SERVE_PACK.asBool(true) && zip.isFile()) {
                    packServer = new me.matsubara.realisticvillagers.appearance.PackServer(this);

                    boolean serving = packServer.start(zip,
                            Config.APPEARANCE_PACK_PORT.asInt(25580),
                            Config.APPEARANCE_PACK_HOST.asString("auto"));

                    if (serving) {
                        getServer().getPluginManager().registerEvents(packServer, this);
                    } else {
                        packServer = null;
                        shapesServed = false;
                    }
                } else if (Config.APPEARANCE_SERVE_PACK.asBool(true)) {
                    // Asked to serve it, and there is nothing to serve.
                    shapesServed = false;
                }
            } catch (Throwable throwable) {
                getLogger().warning("Could not write the appearance pack: " + throwable.getMessage());

                // Only where the plugin was the one meant to hand it out. A server hosting the pack
                // itself is not made wrong by this having failed here.
                if (Config.APPEARANCE_SERVE_PACK.asBool(true)) shapesServed = false;
            }

        if (me.matsubara.realisticvillagers.task.AppearanceTask.supported()) {
            shapeEquipment = new me.matsubara.realisticvillagers.appearance.ShapeEquipment(this);
            playerAppearanceManager = new me.matsubara.realisticvillagers.appearance.PlayerAppearanceManager(this);
            appearanceTask = new me.matsubara.realisticvillagers.task.AppearanceTask(this);
            appearanceTask.start();

            if (shapesServed) {
                getLogger().info("Appearance traits: on. Every villager gets her own height, and women a"
                        + " figure drawn from their own skin — villagers and players alike, worn so the"
                        + " game moves it with them, and hers is there in third person too.");
            } else {
                getLogger().warning("Appearance traits: heights only. The pack the figures are drawn from"
                        + " is not being served, so the figures are off rather than drawn as the"
                        + " placeholder a client shows for a model it cannot find. Put"
                        + " appearance.pack-port and pack-host right and run /rv reload, or set"
                        + " appearance.serve-pack to false if you hand the pack out yourself.");
            }
        } else {
            getLogger().info("Appearance traits: off, this version has no per-item model tints (needs 1.21.4+).");
        }
        graveManager = new me.matsubara.realisticvillagers.task.GraveManager(this);
        graveManager.start();
        constructionManager = new me.matsubara.realisticvillagers.village.ConstructionManager(this);
        buildPreview = new me.matsubara.realisticvillagers.village.BuildPreview(this);
        new me.matsubara.realisticvillagers.listener.VillageListeners(this);
        CustomBlockData.registerListener(this);

        logger.info("Managers created!");
        logger.info("");
        logger.info("Creating recipes...");

        ring = createWeddingRing();
        whistle = createWhistle();
        cross = createCross();

        logger.info("Recipes created!");
        logger.info("");
        logger.info("Loading entity data from all worlds...");

        converter.loadData();

        logger.info("Data loaded!");
        logger.info("");
        logger.info("Loading loots from the configuration files...");

        reloadDefaultTargetEntities();
        reloadWantedItems();
        reloadLoots();

        logger.info("Loots loaded!");
        logger.info("");

        hologramManager = new HologramManager(this);

        registerEvents(
                new BlockListeners(this),
                (inventoryListeners = new InventoryListeners(this)),
                (otherListeners = new OtherListeners(this)),
                (playerListeners = new PlayerListeners(this)),
                (villagerListeners = new VillagerListeners(this)),
                new HologramListener(this));

        // Used in previous versions, not needed any more.
        FileUtils.deleteQuietly(new File(getDataFolder(), "villagers.yml"));

        PluginCommand command = getCommand("realisticvillagers");
        if (command == null)
            return;

        MainCommand main = new MainCommand(this);
        command.setExecutor(main);
        command.setTabCompleter(main);

        PluginCommand genderCmd = getCommand("gender");
        if (genderCmd != null) {
            GenderCommand gender = new GenderCommand(this);
            genderCmd.setExecutor(gender);
            genderCmd.setTabCompleter(gender);
        }

        logLoadingTime(false, now);

        logger.info("****************************************");
    }

    /**
     * Writes the appearance pack again and gives everyone online the new one.
     * <p>
     * Where a shape sits is decided inside the pack, because an item worn in a slot
     * carries no
     * transform anyone can change from here — whatever the pack says is what every
     * villager wearing
     * it gets. So a nudge to the fit is not a packet, it is a new pack, and this is
     * what turns one
     * into the other: rewrite the models, swap the bytes under the little web
     * server, hand out the
     * new hash. Clients fetch it because the hash changed, which takes the few
     * seconds a pack takes
     * and needs nobody to reconnect.
     *
     * @return what to tell whoever asked for it.
     */
    public @NotNull String regenerateAppearancePack() {
        if (!me.matsubara.realisticvillagers.task.AppearanceTask.supported()) {
            return "This version has no per-item model tints, so there is no pack to write.";
        }

        int shapes;
        try {
            shapes = me.matsubara.realisticvillagers.appearance.TraitPack.write(
                    new java.io.File(getDataFolder(), "pack"));
        } catch (Throwable throwable) {
            return "Could not write the pack: " + throwable.getMessage();
        }

        if (packServer == null) {
            return "Wrote " + shapes + " shapes, but the pack is not being served — host it yourself.";
        }

        java.io.File zip = new java.io.File(getDataFolder(), "appearance-pack.zip");
        if (!packServer.reload(zip)) {
            shapesServed = false;
            return "Wrote " + shapes + " shapes, but could not re-serve them, so figures are off.";
        }

        // Back on, for a server that started without a pack and has just been given one.
        shapesServed = true;

        return "Wrote " + shapes + " shapes and sent the pack to everyone online.";
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();

        if (hologramManager != null)
            hologramManager.closeAll();

        // Flush village data before the early return below: it must survive a shutdown
        // that
        // happens while the converter/tracker never finished starting up.
        if (borderVisualizer != null)
            borderVisualizer.shutdown();
        if (hoverNametagTask != null)
            hoverNametagTask.shutdown();
        if (appearanceTask != null)
            appearanceTask.shutdown();
        if (packServer != null)
            packServer.stop();
        if (graveManager != null)
            graveManager.shutdown();
        if (villagePresence != null)
            villagePresence.shutdown();
        if (villageEconomy != null)
            villageEconomy.shutdown();
        if (villageHarvest != null)
            villageHarvest.shutdown();
        if (buildPreview != null)
            buildPreview.shutdown();
        if (constructionManager != null)
            constructionManager.shutdown();
        if (electionManager != null)
            electionManager.shutdown();
        if (villageManager != null)
            villageManager.shutdown();

        if (converter == null || tracker == null)
            return;

        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (tracker.isInvalid(villager, true))
                    continue;
                converter.getNPC(villager).ifPresent(IVillagerNPC::stopExchangeables);
            }
        }
    }

    /**
     * Adds a setting the config has never seen, and saves it, so it can be found
     * and changed.
     */
    private void ensureSetting(@NotNull String path, @NotNull Object value) {
        if (getConfig().contains(path))
            return;

        getConfig().set(path, value);
        saveConfig();

        getLogger().info("Added missing setting " + path + " to config.yml (default: " + value + ").");
    }

    private void logLoadingTime(boolean loading, long now) {
        String time = String.format(Locale.ROOT, "%.3fs", (double) (System.nanoTime() - now) / 1.0E9);
        getLogger().info((loading ? "Loading" : "Enabling") + " took " + time + "!");
    }

    private void fillGuiIgnoredSections(FileConfiguration guiCfg) {
        for (String guiType : GUI_TYPES) {
            ConfigurationSection section = guiCfg.getConfigurationSection("gui." + guiType + ".items");
            if (section == null)
                continue;

            for (String key : section.getKeys(false)) {
                GUI_SPECIAL_SECTIONS.add("gui." + guiType + ".items." + key);
            }
        }
    }

    private void registerEvents(@NotNull Listener... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    public void updateConfigs() {
        String pluginFolder = getDataFolder().getPath();
        String skinFolder = getSkinFolder();

        Predicate<FileConfiguration> noVersion = temp -> !temp.contains("config-version");

        // config.yml
        updateConfig(
                pluginFolder,
                "config.yml",
                file -> {
                    reloadConfig();

                    // Re-cache EnvironmentAttributeSystem for all loaded worlds after schedule
                    // refresh, otherwise villagers will use stale or vanilla schedules until
                    // the next server restart (causing sleep/job loop).
                    for (World world : getServer().getWorlds()) {
                        converter.addGameRuleListener(world);
                    }

                    // Refresh brains sync to prevent issues.
                    getServer().getScheduler().runTask(this, () -> {
                        for (World world : getServer().getWorlds()) {
                            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                                if (tracker.isInvalid(villager, true))
                                    continue;
                                converter.getNPC(villager).ifPresent(IVillagerNPC::refreshBrain);
                            }
                        }
                    });

                    if (tracker == null)
                        tracker = new VillagerTracker(this);
                    if (worlds == null)
                        worlds = Config.WORLDS_FILTER_WORLDS.asStringList();
                },
                file -> saveDefaultConfig(),
                config -> SPECIAL_SECTIONS.stream().filter(config::isConfigurationSection).toList(),
                ConfigChanges.builder()
                        .addChange(
                                noVersion,
                                temp -> {
                                    String pathToInfoLore = "gui.main.items.information.lore";

                                    List<String> lore = temp.getStringList(pathToInfoLore);
                                    if (lore.isEmpty())
                                        return;

                                    lore.replaceAll(line -> line.replace("%partner%", "%current-partner%"));
                                    temp.set(pathToInfoLore, lore);
                                },
                                1)
                        .addChange(
                                aimVersion(1),
                                temp -> temp.set("gui.new-skin", null),
                                2)
                        .addChange(
                                aimVersion(2),
                                temp -> {
                                    String pathToSetHome = "gui.main.items.set-home.";
                                    temp.set(pathToSetHome + "only-for-family", null);
                                    temp.set(pathToSetHome + "only-if-allowed", false);

                                    String pathToCombat = "gui.main.items.combat.";
                                    temp.set(pathToCombat + "only-for-family", null);
                                    temp.set(pathToCombat + "only-if-allowed", false);
                                },
                                3)
                        .addChange(
                                aimVersion(3),
                                new Consumer<>() {
                                    @Override
                                    public void accept(FileConfiguration temp) {
                                        handleEntityName(temp, "zombie_villager");
                                        handleEntityName(temp, "cave_spider");
                                        handleEntityName(temp, "elder_guardian");
                                        handleEntityName(temp, "wither_skeleton");
                                        handleEntityName(temp, "piglin_brute");
                                        handleEntityName(temp, "zombified_piglin");
                                        handleEntityName(temp, "ender_dragon");
                                    }

                                    private void handleEntityName(@NotNull FileConfiguration temp, String path) {
                                        String name = temp.getString(path);
                                        if (name != null)
                                            temp.set(path.replace("_", "-"), name);
                                    }
                                },
                                4)
                        .addChange(
                                aimVersion(4),
                                temp -> {
                                    List<String> lines = temp.getStringList("custom-nametags.lines");
                                    if (lines.isEmpty())
                                        return;

                                    temp.set("custom-nametags.lines", null);
                                    temp.set("custom-nametags.lines.villager", lines);
                                },
                                5)
                        .addChange(
                                aimVersion(5),
                                temp -> {
                                    temp.set("villager-title-article", null);
                                    temp.set("variable-text.profession", null);
                                },
                                6)
                        .build());

        Function<FileConfiguration, List<String>> emptyIgnore = config -> Collections.emptyList();

        // gui.yml
        updateConfig(
                pluginFolder,
                "configs/gui.yml",
                file -> {
                    guiConfig = YamlConfiguration.loadConfiguration(file);
                    guiConfig.setDefaults(new MemoryConfiguration());
                },
                file -> saveResource("configs/gui.yml"),
                config -> {
                    fillGuiIgnoredSections(config);
                    return GUI_SPECIAL_SECTIONS.stream().filter(config::isConfigurationSection).toList();
                },
                Collections.emptyList());

        // loot.yml
        updateConfig(
                pluginFolder,
                "configs/loot.yml",
                file -> {
                    lootConfig = YamlConfiguration.loadConfiguration(file);
                    lootConfig.setDefaults(new MemoryConfiguration());
                },
                file -> saveResource("configs/loot.yml"),
                config -> config.contains("spawn-loot") ? List.of("spawn-loot") : Collections.emptyList(),
                Collections.emptyList());

        // gifts.yml
        updateConfig(
                pluginFolder,
                "configs/gifts.yml",
                file -> {
                    giftsConfig = YamlConfiguration.loadConfiguration(file);
                    giftsConfig.setDefaults(new MemoryConfiguration());
                    if (giftManager != null)
                        giftManager.loadGiftCategories();
                },
                file -> saveResource("configs/gifts.yml"),
                config -> config.contains("items") ? List.of("items") : Collections.emptyList(),
                Collections.emptyList());

        // variable-text.yml
        updateConfig(
                pluginFolder,
                "configs/variable-text.yml",
                file -> {
                    variableTextConfig = YamlConfiguration.loadConfiguration(file);
                    variableTextConfig.setDefaults(new MemoryConfiguration());
                    // Refresh schedules now that variableTextConfig holds the current values.
                    converter.refreshSchedules();
                },
                file -> saveResource("configs/variable-text.yml"),
                config -> config.contains("schedules") ? List.of("schedules") : Collections.emptyList(),
                Collections.emptyList());

        // holograms.yml — ConfigUpdater is NOT used here because it duplicates YAML
        // block-sequence items (list entries get written twice into the merged file).
        // We handle it manually: create from JAR when missing, then add only the keys
        // that are absent from the disk file using YamlConfiguration directly.
        {
            File hologramsFile = new File(pluginFolder, "configs/holograms.yml");

            // 1. Create from JAR if the file is missing.
            if (!hologramsFile.exists())
                saveResource("configs/holograms.yml");

            // 2. Silently replace old format (pre hologram.menus) with the JAR version.
            YamlConfiguration diskCfg = YamlConfiguration.loadConfiguration(hologramsFile);
            if (diskCfg.contains("hologram") && !diskCfg.contains("hologram.menus")) {
                FileUtils.deleteQuietly(hologramsFile);
                saveResource("configs/holograms.yml");
                diskCfg = YamlConfiguration.loadConfiguration(hologramsFile);
            }

            // 3. Add any keys present in the JAR but missing from the disk file.
            // isConfigurationSection() guards skip parent nodes — only leaf values
            // (scalars and lists) are added, so no section is inadvertently overwritten.
            try (InputStream jarStream = getResource("configs/holograms.yml")) {
                if (jarStream != null) {
                    YamlConfiguration jarCfg = YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(jarStream, java.nio.charset.StandardCharsets.UTF_8));
                    boolean changed = false;
                    for (String key : jarCfg.getKeys(true)) {
                        if (!jarCfg.isConfigurationSection(key) && !diskCfg.contains(key)) {
                            diskCfg.set(key, jarCfg.get(key));
                            changed = true;
                        }
                    }
                    if (changed)
                        diskCfg.save(hologramsFile);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            hologramConfig = diskCfg;
            hologramConfig.setDefaults(new MemoryConfiguration());
        }

        // The text blueprint format is gone; its files would only sit there looking
        // meaningful.
        FileUtils.deleteQuietly(new File(pluginFolder, "configs/blueprints.yml"));
        FileUtils.deleteQuietly(new File(pluginFolder, "configs/blueprints.yml.old"));

        // Graves and the recorded-village list belong to the world they describe, and
        // moved there.
        // The copies an earlier build left here are read by nothing and only look
        // meaningful.
        FileUtils.deleteQuietly(new File(pluginFolder, "graves.yml"));
        FileUtils.deleteQuietly(new File(pluginFolder, "recorded.yml"));

        // Settings added after a server's config was written.
        //
        // The updater fills in missing keys, and when it doesn't the feature is simply
        // off with
        // nothing to say why — which has now cost two rounds of "it isn't working" for
        // settings
        // that were never in the file. Writing them here makes that impossible.
        ensureSetting("nametags.only-when-looking", false);
        ensureSetting("nametags.look-range", 12.0d);

        // configs/messages/system.yml — system/admin messages only (no villager
        // dialogue)
        updateConfig(
                pluginFolder,
                "configs/messages/system.yml",
                file -> {
                    messages.setConfiguration(YamlConfiguration.loadConfiguration(file));
                    messages.loadRegionalConfigs();
                },
                file -> saveResource("configs/messages/system.yml"),
                emptyIgnore,
                ConfigChanges.builder()
                        // Previously @interact-fail.not-allowed was a single line message, now is a
                        // map; only for V = X.
                        .addChange(
                                noVersion,
                                temp -> temp.set("interact-fail.not-allowed", null),
                                1)
                        .build());

        // male.yml & female.yml (these shouldn't be modified directly by admins, only
        // using the skin GUI).
        loadConsumer.accept(new File(skinFolder, "male.yml"));
        loadConsumer.accept(new File(skinFolder, "female.yml"));

        // Regional skin filter files: skins/regions/<type>/male.yml and female.yml
        // Format: each profession key holds a list of skin IDs from the global file.
        // Saved from JAR on first run (saveResource skips if file already exists on
        // disk).
        // Loaded with key "<sex>_<type>.yml" (e.g. "male_desert.yml").
        for (String regionType : new String[] { "desert", "plains", "snow", "savanna", "jungle", "swamp", "taiga" }) {
            for (String sexName : new String[] { "male", "female" }) {
                saveResource("skins/regions/" + regionType + "/" + sexName + ".yml");
                File regionalFile = new File(skinFolder + File.separator + "regions" + File.separator + regionType,
                        sexName + ".yml");
                if (regionalFile.exists()) {
                    tracker.getFiles().put(
                            sexName + "_" + regionType + ".yml",
                            Pair.of(regionalFile, YamlConfiguration.loadConfiguration(regionalFile)));
                }
            }
        }

        // configs/names/default.yml — global fallback name pool (was names.yml)
        {
            String resourcePath = "configs/names/default.yml";
            saveResource(resourcePath);
            File defaultNamesFile = new File(pluginFolder, resourcePath);
            if (defaultNamesFile.exists()) {
                tracker.getFiles().put("names_default.yml",
                        Pair.of(defaultNamesFile, YamlConfiguration.loadConfiguration(defaultNamesFile)));
            }
        }

        // Regional name files — configs/names/<type>.yml, keyed as "names_<type>.yml".
        for (String type : new String[] { "desert", "plains", "snow", "savanna", "jungle", "swamp", "taiga" }) {
            String resourcePath = "configs/names/" + type + ".yml";
            String mapKey = "names_" + type + ".yml";
            saveResource(resourcePath);
            File regionalFile = new File(pluginFolder, resourcePath);
            if (regionalFile.exists()) {
                tracker.getFiles().put(mapKey,
                        Pair.of(regionalFile, YamlConfiguration.loadConfiguration(regionalFile)));
            }
        }
    }

    @Contract(pure = true)
    private @NotNull Predicate<FileConfiguration> aimVersion(int version) {
        return config -> config.getInt("config-version") == version;
    }

    public void updateConfig(String folderName,
            String fileName,
            Consumer<File> reloadAfterUpdating,
            Consumer<File> resetConfiguration,
            Function<FileConfiguration, List<String>> ignoreSection,
            List<ConfigChanges> changes) {
        File file = new File(folderName, fileName);

        FileConfiguration config = PluginUtils.reloadConfig(this, file, resetConfiguration);
        if (config == null) {
            getLogger().severe("Can't find {" + file.getName() + "}!");
            return;
        }

        for (ConfigChanges change : changes) {
            handleConfigChanges(file, config, change.predicate(), change.consumer(), change.newVersion());
        }

        try {
            ConfigUpdater.update(
                    this,
                    fileName,
                    file,
                    ignoreSection.apply(config));
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        reloadAfterUpdating.accept(file);
    }

    @Override
    public @Nullable InputStream getResource(@NotNull String name) {
        InputStream resource = super.getResource(name);
        if (resource != null)
            return resource;

        if (!name.equals("male.yml") && !name.equals("female.yml"))
            return null;

        try {
            File file = new File(getSkinFolder(), name);
            if (!file.exists())
                return null;

            URL url = file.toURI().toURL();

            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);

            return connection.getInputStream();
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private void handleConfigChanges(@NotNull File file, FileConfiguration config,
            @NotNull Predicate<FileConfiguration> predicate, Consumer<FileConfiguration> consumer, int newVersion) {
        if (!predicate.test(config))
            return;

        int previousVersion = config.getInt("config-version", 0);
        getLogger().info(
                "Updated {%s} config to v{%s} (from v{%s})".formatted(file.getName(), newVersion, previousVersion));

        consumer.accept(config);
        config.set("config-version", newVersion);

        try {
            config.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public record ConfigChanges(Predicate<FileConfiguration> predicate,
            Consumer<FileConfiguration> consumer,
            int newVersion) {

        public static @NotNull Builder builder() {
            return new Builder();
        }

        public static class Builder {

            private final List<ConfigChanges> changes = new ArrayList<>();

            public Builder addChange(Predicate<FileConfiguration> predicate,
                    Consumer<FileConfiguration> consumer,
                    int newVersion) {
                changes.add(new ConfigChanges(predicate, consumer, newVersion));
                return this;
            }

            public List<ConfigChanges> build() {
                return ImmutableList.copyOf(changes);
            }
        }
    }

    public ItemStack createBaby(boolean isBoy, String babyName, long procreation, @NotNull UUID motherUUID) {
        return getItem("baby." + (isBoy ? "boy" : "girl"))
                .replace("%villager-name%", babyName)
                .setData(getChildNameKey(), PersistentDataType.STRING, babyName)
                .setData(getChildSexKey(), PersistentDataType.STRING, isBoy ? "male" : "female")
                .setData(getProcreationKey(), PersistentDataType.LONG, procreation)
                .setData(getMotherUUIDKey(), PersistentDataType.STRING, motherUUID.toString())
                .build();
    }

    public @NotNull Shape createWeddingRing() {
        return createCraftableItem("wedding-ring", "wedding_ring", isRingKey);
    }

    public @NotNull Shape createWhistle() {
        return createCraftableItem("whistle", isWhistleKey);
    }

    public @NotNull Shape createCross() {
        return createCraftableItem("cross", isCrossKey);
    }

    private @NotNull Shape createCraftableItem(String item, NamespacedKey identifier) {
        return createCraftableItem(item, item, identifier);
    }

    private @NotNull Shape createCraftableItem(String item, String recipeName, NamespacedKey identifier) {
        ItemBuilder builder = getItem(item).setData(identifier, PersistentDataType.INTEGER, 1);

        boolean shaped = getConfig().getBoolean(item + ".crafting.shaped");
        boolean enabled = getConfig().getBoolean(item + ".crafting.enabled", true);
        List<String> ingredients = enabled ? getConfig().getStringList(item + ".crafting.ingredients") : List.of();
        List<String> shapeList = getConfig().getStringList(item + ".crafting.shape");

        return new Shape(this, recipeName, shaped, ingredients, shapeList, builder.build());
    }

    public ItemStack getDivorcePapers() {
        return getItem("divorce-papers").setData(divorcePapersKey, PersistentDataType.INTEGER, 1).build();
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();

        // We don't want to use default values.
        getConfig().setDefaults(new MemoryConfiguration());
    }

    public ItemBuilder getItem(String path) {
        return getItem(path, null);
    }

    public ItemBuilder getItem(String path, @Nullable IVillagerNPC npc) {
        FileConfiguration config = path.startsWith("gui.") ? guiConfig
                : path.startsWith("spawn-loot.") ? lootConfig
                        : getConfig();

        String name = config.getString(path + ".display-name");
        List<String> lore = config.getStringList(path + ".lore");

        String url = config.getString(path + ".url");

        String materialPath = path + ".material";

        String materialName = config.getString(materialPath, "STONE");
        Material material = PluginUtils.getOrNull(Material.class, materialName);

        ItemBuilder builder = new ItemBuilder(material)
                .setData(itemIdKey, PersistentDataType.STRING,
                        path.contains(".") ? path.substring(path.lastIndexOf(".") + 1) : path)
                .setLore(lore);

        if (name != null)
            builder.setDisplayName(name);

        String amountString = config.getString(path + ".amount");
        if (amountString != null) {
            int amount = PluginUtils.getRangedAmount(amountString);
            builder.setAmount(amount);
        }

        if (material == Material.PLAYER_HEAD && url != null) {
            // Use UUID from path to allow stacking heads.
            UUID itemUUID = UUID.nameUUIDFromBytes(path.getBytes());
            builder.setHead(itemUUID, url.equals("SELF") ? getNPCTextureURL(npc) : url, true);
        }

        int modelData = config.getInt(path + ".model-data", Integer.MIN_VALUE);
        if (modelData != Integer.MIN_VALUE)
            builder.setCustomModelData(modelData);

        for (String enchantmentString : config.getStringList(path + ".enchantments")) {
            if (Strings.isNullOrEmpty(enchantmentString))
                continue;
            String[] data = PluginUtils.splitData(enchantmentString);

            Enchantment enchantment = Registry.ENCHANTMENT
                    .get(NamespacedKey.minecraft(data[0].toLowerCase(Locale.ROOT)));

            int level;
            try {
                level = PluginUtils.getRangedAmount(data[1]);
            } catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
                level = 1;
            }

            if (enchantment != null)
                builder.addEnchantment(enchantment, level);
        }

        for (String flag : config.getStringList(path + ".flags")) {
            ItemFlag flagValue = PluginUtils.getOrNull(ItemFlag.class, flag.toUpperCase(Locale.ROOT));
            if (flagValue != null)
                builder.addItemFlags(flagValue);
        }

        String tippedArrow = config.getString(path + ".tipped");
        if (tippedArrow != null) {
            PotionType potionType = PluginUtils.getValidPotionType(tippedArrow);
            if (potionType != null)
                builder.setBasePotionData(potionType);
        }

        Object leather = config.get(path + ".leather-color");
        if (leather instanceof String leatherColor) {
            Color color = PluginUtils.getColor(leatherColor);
            if (color != null)
                builder.setLeatherArmorMetaColor(color);
        } else if (leather instanceof List<?> list) {
            List<Color> colors = new ArrayList<>();

            for (Object object : list) {
                if (!(object instanceof String string))
                    continue;
                if (string.equalsIgnoreCase("$RANDOM"))
                    continue;

                Color color = PluginUtils.getColor(string);
                if (color != null)
                    colors.add(color);
            }

            if (!colors.isEmpty()) {
                Color color = colors.get(RandomUtils.nextInt(0, colors.size()));
                builder.setLeatherArmorMetaColor(color);
            }
        }

        if (config.contains(path + ".firework")) {
            ConfigurationSection section = config.getConfigurationSection(path + ".firework.firework-effects");
            if (section == null)
                return builder;

            Set<FireworkEffect> effects = new HashSet<>();
            for (String effect : section.getKeys(false)) {
                FireworkEffect.Builder effectBuilder = FireworkEffect.builder();

                String type = config.getString(path + ".firework.firework-effects." + effect + ".type");
                if (type == null)
                    continue;

                FireworkEffect.Type effectType = PluginUtils.getOrEitherRandomOrNull(FireworkEffect.Type.class, type);

                boolean flicker = config.getBoolean(path + ".firework.firework-effects." + effect + ".flicker");
                boolean trail = config.getBoolean(path + ".firework.firework-effects." + effect + ".trail");

                effects.add((effectType != null ? effectBuilder.with(effectType) : effectBuilder)
                        .flicker(flicker)
                        .trail(trail)
                        .withColor(getColors(config, path, effect, "colors"))
                        .withFade(getColors(config, path, effect, "fade-colors"))
                        .build());
            }

            String powerString = config.getString(path + ".firework.power");
            int power = PluginUtils.getRangedAmount(powerString != null ? powerString : "");

            if (!effects.isEmpty())
                builder.initializeFirework(power, effects.toArray(new FireworkEffect[0]));
        }

        String damageString = config.getString(path + ".damage");
        if (damageString != null) {
            int maxDurability = builder.build().getType().getMaxDurability();

            int damage;
            if (damageString.equalsIgnoreCase("$RANDOM")) {
                damage = RandomUtils.nextInt(1, maxDurability);
            } else if (damageString.contains("%")) {
                damage = Math.round(
                        maxDurability * ((float) PluginUtils.getRangedAmount(damageString.replace("%", "")) / 100));
            } else {
                damage = PluginUtils.getRangedAmount(damageString);
            }

            if (damage > 0)
                builder.setDamage(Math.min(damage, maxDurability));
        }

        return builder;
    }

    public String getNPCTextureURL(@Nullable IVillagerNPC npc) {
        if (Config.DISABLE_SKINS.asBool())
            return VILLAGER_HEAD_TEXTURE;

        if (npc == null)
            return UNKNOWN_HEAD_TEXTURE;

        TextureProperty textures = tracker.getTextures(npc.getSex(), "none", npc.getSkinTextureId());
        return textures.getName().equals("error") ? UNKNOWN_HEAD_TEXTURE
                : PluginUtils.getURLFromTexture(textures.getValue());
    }

    private @NotNull Set<Color> getColors(@NotNull FileConfiguration config, String path, String effect,
            String needed) {
        Set<Color> colors = new HashSet<>();
        for (String colorString : config.getStringList(path + ".firework.firework-effects." + effect + "." + needed)) {
            Color color = PluginUtils.getColor(colorString);
            if (color != null)
                colors.add(color);
        }
        return colors;
    }

    @SuppressWarnings("SameParameterValue")
    public void saveResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists())
            saveResource(name, false);
    }

    public boolean isMarried(@NotNull Player player) {
        String partner = player.getPersistentDataContainer().get(marriedWith, PersistentDataType.STRING);
        if (partner == null)
            return false;

        IVillagerNPC partnerInfo = tracker.getOffline(UUID.fromString(partner));
        if (partnerInfo == null) {
            player.getPersistentDataContainer().remove(marriedWith);
            return false;
        }

        return true;
    }

    public void reloadDefaultTargetEntities() {
        defaultTargets.clear();

        for (String entity : getConfig().getStringList("default-target-entities")) {
            EntityType type = PluginUtils.getOrNull(EntityType.class, entity.toUpperCase(Locale.ROOT));
            if (type == null)
                continue;

            Class<? extends Entity> clazz = type.getEntityClass();
            if (clazz == null || !Monster.class.isAssignableFrom(clazz))
                continue;

            defaultTargets.add(entity);
        }
    }

    public void reloadWantedItems() {
        wantedItems.clear();
        // Social gifts (reputation effects) from gifts.items.
        wantedItems.addAll(giftManager.getAllGifts());
        // Utility items (saddle, food, carpets, etc.) from default-wanted-items.
        // Parse simple material entries; skip tags (#...) and profession conditions
        // (?...).
        Set<Material> existing = java.util.EnumSet.noneOf(Material.class);
        for (Gift g : wantedItems)
            existing.add(g.getType());
        for (String entry : getConfig().getStringList("default-wanted-items")) {
            String s = entry.trim();
            if (s.isEmpty() || s.startsWith("#"))
                continue;
            // Strip profession condition prefix: ?PROFESSION:ITEM -> ITEM
            int colon = s.indexOf(':');
            if (colon >= 0)
                s = s.substring(colon + 1);
            // Strip amount/modifier suffixes: ITEM(n)* -> ITEM
            int paren = s.indexOf('(');
            if (paren >= 0)
                s = s.substring(0, paren);
            s = s.replace("*", "").trim();
            Material material = Material.matchMaterial(s);
            if (material == null || material == Material.AIR)
                continue;
            if (existing.contains(material))
                continue;
            wantedItems.add(new Gift(material, GiftCategory.NEUTRAL, 0, false));
            existing.add(material);
        }
    }

    public void reloadLoots() {
        loots.clear();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            loots.put(slotName(slot), createLoot("equipment", slot));
        }

        loots.put("inventory-items", createLoot("inventory-items"));
    }

    public boolean isDisabledIn(@NotNull World world) {
        return !isEnabledIn(world.getName());
    }

    public boolean isEnabledIn(String world) {
        String type = Config.WORLDS_FILTER_TYPE.asString();
        if (type == null || !FILTER_TYPES.contains(type.toUpperCase(Locale.ROOT)))
            return true;

        boolean contains = worlds.contains(world);
        return type.equalsIgnoreCase("WHITELIST") == contains;
    }

    public Gift getWantedItem(IVillagerNPC npc, ItemStack item, boolean isItemPickup) {
        for (Gift wanted : wantedItems) {
            if (!wanted.is(item.getType()))
                continue;
            if (isItemPickup && wanted.isInventoryLootOnly())
                continue;
            // Ground pickup only chases items the villager actually likes — otherwise a DISLIKED
            // drop (e.g. rotten flesh from a zombie) gets treated as "wanted" and walked towards.
            if (isItemPickup && wanted.getCategory() == GiftCategory.DISLIKED)
                continue;
            return wanted;
        }
        return null;
    }

    public @Nullable LivingEntity getUnloadedOffline(@NotNull IVillagerNPC offline) {
        LivingEntity bukkit = offline.bukkit();
        if (bukkit != null)
            return bukkit;

        Location location = offline.getLastKnownPosition().asLocation();
        if (location.getWorld() == null)
            return null;

        Chunk chunk = location.getWorld().getChunkAt(location);
        chunk.load();
        chunk.getEntities();

        Entity inChunk = Bukkit.getEntity(offline.getUniqueId());
        return inChunk instanceof Villager villager ? villager : null;
    }

    public void openWhistleGUI(Player player, @Nullable Integer page, @Nullable String keyword) {
        List<IVillagerNPC> family = tracker.getOfflineVillagers()
                .stream()
                .filter(offline -> {
                    Villager bukkit = offline.bukkit() instanceof Villager villager ? villager : null;
                    if (bukkit != null) {
                        Optional<IVillagerNPC> online = converter.getNPC(bukkit);
                        return online.isPresent() && online.get().isFamily(player, true);
                    } else {
                        return offline.isFamily(player, true);
                    }
                }).toList();

        if (family.isEmpty()) {
            messages.send(player, Messages.Message.WHISTLE_NO_FAMILY);
            return;
        }

        new WhistleGUI(this, player, family.stream(), page, keyword);
    }

    public void equipVillager(LivingEntity living, boolean force) {
        if (invalidLoots())
            return;

        Optional<IVillagerNPC> npc = converter.getNPC(living);
        if (npc.isEmpty()
                || npc.get().isEquipped()
                || !force
                || tracker.isInvalid(living, true))
            return;

        EntityEquipment equipment = living.getEquipment();
        if (equipment == null)
            return;

        Map<EquipmentSlot, ItemLoot> equipped = new HashMap<>();
        npc.get().setEquipped(true);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String name = slotName(slot);
            List<ItemLoot> loots = this.loots.get(name);
            if (loots == null)
                continue;

            double chance = Math.random();
            for (ItemLoot loot : loots) {
                if (chance > loot.chance())
                    continue;

                ItemStack item = loot.getItem();
                if (item == null)
                    continue;

                equipment.setItem(slot, item);
                equipped.put(slot, loot);
                break;
            }
        }

        List<ItemLoot> loots = this.loots.get("inventory-items");
        if (loots == null)
            return;

        double chance = Math.random();
        for (ItemLoot loot : loots) {
            if (chance > loot.chance())
                continue;

            ItemStack item = loot.getItem();
            if (item == null)
                continue;

            if ((loot.forRange() && testBothHand(equipped, ItemStackUtils::isRangeWeapon))
                    || (loot.bow() && testBothHand(equipped, inHand -> inHand.getType() == Material.BOW))
                    || (loot.crossbow() && testBothHand(equipped, inHand -> inHand.getType() == Material.CROSSBOW))) {

                if (loot.offHandIfPossible() && equipped.get(EquipmentSlot.OFF_HAND) == null) {
                    equipment.setItemInOffHand(item);
                    continue;
                }

                if (living instanceof InventoryHolder holder) {
                    holder.getInventory().addItem(item);
                }
            }
        }
    }

    private boolean invalidLoots() {
        if (loots.isEmpty())
            return true;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (loots.get(slotName(slot)) != null)
                return false;
        }

        return loots.get("inventory-items") == null;
    }

    private boolean testBothHand(Map<EquipmentSlot, ItemLoot> equipped, Predicate<ItemStack> predicate) {
        return testHand(equipped, predicate, EquipmentSlot.HAND)
                || testHand(equipped, predicate, EquipmentSlot.OFF_HAND);
    }

    private boolean testHand(@NotNull Map<EquipmentSlot, ItemLoot> equipped, Predicate<ItemStack> predicate,
            EquipmentSlot slot) {
        ItemLoot hand = equipped.get(slot);
        if (hand == null)
            return false;

        return predicate.test(hand.getItem());
    }

    public @NotNull List<ItemLoot> createLoot(String sector) {
        return createLoot(sector, null);
    }

    public @NotNull List<ItemLoot> createLoot(String sector, @Nullable EquipmentSlot part) {
        FileConfiguration config = lootConfig;

        String name = sector + (part != null ? "." + slotName(part) : "");

        ConfigurationSection section = config.getConfigurationSection("spawn-loot." + name);
        if (section == null)
            return Collections.emptyList();

        List<ItemLoot> loots = new ArrayList<>();
        for (String path : section.getKeys(false)) {
            double chance = config.getDouble("spawn-loot." + name + "." + path + ".chance", 1.0d);

            boolean onlyForRangeWeapon = config
                    .getBoolean("spawn-loot." + name + "." + path + ".only-for-range-weapon");
            boolean onlyForBow, onlyForCrossbow;
            if (onlyForRangeWeapon) {
                onlyForBow = (onlyForCrossbow = true);
            } else {
                onlyForBow = config.getBoolean("spawn-loot." + name + "." + path + ".only-for-bow");
                onlyForCrossbow = config.getBoolean("spawn-loot." + name + "." + path + ".only-for-crossbow");
            }

            boolean offHandIfPossible = config.getBoolean("spawn-loot." + name + "." + path + ".off-hand-if-possible");

            loots.add(new ItemLoot(
                    () -> getItem("spawn-loot." + name + "." + path).build(),
                    chance,
                    onlyForBow,
                    onlyForCrossbow,
                    offHandIfPossible));
        }

        loots.sort(Comparator.comparingDouble(ItemLoot::chance));
        return loots;
    }

    private @NotNull String slotName(@NotNull EquipmentSlot slot) {
        return slot.name().toLowerCase(Locale.ROOT).replace("_", "-");
    }

    @Contract("_ -> new")
    public @NotNull NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    @Contract(pure = true)
    public @NotNull String getSkinFolder() {
        return getDataFolder() + File.separator + "skins";
    }

    public String getProfessionFormatted(@NotNull Villager.Profession profession, boolean isMale) {
        return getProfessionFormatted(profession.name().toLowerCase(Locale.ROOT), isMale);
    }

    public String getProfessionFormatted(String profession, boolean isMale) {
        String sex = isMale ? "male" : "female";
        return variableTextConfig.getString(
                String.format("variable-text.profession.%s.%s", sex, profession),
                PluginUtils.capitalizeFully(profession));
    }

    public static @Nullable OfflineDataWrapper villagerDataFromPDC(RealisticVillagers plugin,
            PersistentDataContainer container) {
        // Attempt 1: new format — OfflineDataWrapper stored under "RValues" (1.21.11+)
        try {
            OfflineDataWrapper wrapper = container.get(plugin.getNpcValuesKey(), RealisticVillagers.VILLAGER_DATA);
            if (wrapper != null)
                return wrapper;
        } catch (Exception ignored) {
            // Stored in a different format — fall through to legacy attempts.
        }
        // Attempt 2: legacy format — OfflineVillagerNPC stored under "RValues" (saved
        // by 1.21.8).
        // The 1.21.8 NMSConverter used
        // ConfigurationSerializableDataType<OfflineVillagerNPC> which
        // is incompatible with the current
        // ConfigurationSerializableDataType<OfflineDataWrapper>.
        // getNPCFromPDC extracts the raw bytes and re-interprets them, returning an
        // OfflineDataWrapper.
        try {
            OfflineDataWrapper legacy = plugin.getConverter().getNPCFromPDC(container, plugin.getNpcValuesKey());
            if (legacy != null)
                return legacy;
        } catch (Exception ignored) {
        }
        // Attempt 3: very old format stored under legacy key "VillagerNPCValues"
        // (pre-1.21.8).
        try {
            OfflineDataWrapper wrapper = container.get(plugin.getLegacyNpcValuesKey(),
                    RealisticVillagers.VILLAGER_DATA);
            if (wrapper != null)
                return wrapper;
        } catch (Exception ignored) {
        }
        return null;
    }

    public static @Nullable OfflineDataWrapper villagerDataFromPrimitive(byte[] primitive,
            PersistentDataAdapterContext context) {
        try {
            return VILLAGER_DATA.fromPrimitive(primitive, context);
        } catch (Exception exception) {
            return null;
        }
    }
}
