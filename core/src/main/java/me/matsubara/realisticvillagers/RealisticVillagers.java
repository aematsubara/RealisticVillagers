package me.matsubara.realisticvillagers;

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
import me.matsubara.realisticvillagers.command.MainCommand;
import me.matsubara.realisticvillagers.compatibility.*;
import me.matsubara.realisticvillagers.data.ItemLoot;
import me.matsubara.realisticvillagers.data.serialization.GossipEntryWrapper;
import me.matsubara.realisticvillagers.data.serialization.OfflineDataWrapper;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
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
import net.wesjd.anvilgui.AnvilGUI;
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

    @ApiStatus.Internal
    private final @Getter(AccessLevel.NONE) NamespacedKey valuesKey = key("RValues"); // New main key.
    private final NamespacedKey inventoryKey = key("RInventory");
    @ApiStatus.Internal
    private final @Getter(AccessLevel.NONE) NamespacedKey npcValuesKey = key("VillagerNPCValues"); // Previous main key.

    public NamespacedKey getNpcValuesKey() {
        VersionMatcher matcher = VersionMatcher.getByMinecraftVersion();
        return matcher != null && matcher.higherOrEqualThan(VersionMatcher.v1_21_8) ? valuesKey : getLegacyNpcValuesKey();
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

    private Messages messages;
    private INMSConverter converter;

    private final List<String> defaultTargets = new ArrayList<>();
    private final Set<Gift> wantedItems = new HashSet<>();
    private final Map<String, List<ItemLoot>> loots = new HashMap<>();
    private final Consumer<File> loadConsumer = file -> tracker.getFiles().put(file.getName(), Pair.of(file, YamlConfiguration.loadConfiguration(file)));

    private List<String> worlds;

    private static final String VILLAGER_HEAD_TEXTURE = "4ca8ef2458a2b10260b8756558f7679bcb7ef691d41f534efea2ba75107315cc";
    private static final String UNKNOWN_HEAD_TEXTURE = "badc048a7ce78f7dad72a07da27d85c0916881e5522eeed1e3daf217a38c1a";

    // We want ListenMode to ignore our entities.
    public static final BiConsumer<JavaPlugin, Metadatable> LISTEN_MODE_IGNORE = (plugin, living) -> living.setMetadata("RemoveGlow", new FixedMetadataValue(plugin, true));

    public static final List<AnvilGUI.ResponseAction> CLOSE_RESPONSE = Collections.singletonList(AnvilGUI.ResponseAction.close());
    private static final List<String> FILTER_TYPES = List.of("WHITELIST", "BLACKLIST");
    private static final Set<String> SPECIAL_SECTIONS = Sets.newHashSet(
            "baby",
            "spawn-loot",
            "wedding-ring",
            "whistle",
            "divorce-papers",
            "cross",
            "change-skin",
            "gui.main.frame",
            "schedules",
            "revive.head-item");
    private static final List<String> GUI_TYPES = List.of("main", "equipment", "combat", "whistle", "skin", "new-skin");
    private static final int BSTATS_ID = 27463;
    private static final @SuppressWarnings("UnstableApiUsage") NamespacedKey MM_KEY = new NamespacedKey("mythicmobs", "type");

    static {
        // Register our data serializators.
        ConfigurationSerialization.registerClass(GossipEntryWrapper.class);
        ConfigurationSerialization.registerClass(OfflineDataWrapper.class);
    }

    public static final PersistentDataType<byte[], OfflineDataWrapper> VILLAGER_DATA = new ConfigurationSerializableDataType<>(OfflineDataWrapper.class);

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();

        long now = System.nanoTime();

        Logger logger = getLogger();
        logger.info("****************************************");
        logger.info("Loading compatibilities...");

        compatibilityManager = new CompatibilityManager();

        // Shopkeeper, Citizens & (probably) RainbowsPro; for VillagerMarket, the villager shouldn't have AI to work properly.
        compatibilityManager.addCompatibility(getName(), villager -> villager.hasAI() && !villager.hasMetadata("shopkeeper") && !villager.hasMetadata("NPC"));

        // General compatibilities.
        addCompatibility("EliteMobs", EMCompatibility::new);
        addCompatibility("ViaVersion", ViaCompatibility::new);
        addCompatibility("VillagerTradeLimiter", VTLCompatibility::new);
        addCompatibility("MythicMobs", () ->
                villager -> !villager.getPersistentDataContainer().has(MM_KEY, PersistentDataType.STRING));

        logger.info("Compatibilities loaded!");
        logger.info("");
        logger.info("Registering custom entities...");

        VersionMatcher matcher = VersionMatcher.getByMinecraftVersion();
        if (matcher == null) {
            logger.severe("NMSConverter couldn't find a valid implementation for this server version.");
        } else try {
            Class<?> converterClass = Class.forName(INMSConverter.class.getPackageName() + "." + matcher.getPackageName() + ".NMSConverter");
            Constructor<?> converterConstructor = converterClass.getConstructor(getClass());
            converter = (INMSConverter) converterConstructor.newInstance(this);
            converter.registerEntities();
        } catch (ReflectiveOperationException exception) {
            logger.severe("NMSConverter couldn't find a valid implementation for this server version.");
            exception.printStackTrace();
        }

        logger.info("Custom entities registered!");
        logger.info("");

        logLoadingTime(true, now);

        logger.info("****************************************");
    }

    private void addCompatibility(String name, Supplier<Compatibility> supplier) {
        PluginManager manager = getServer().getPluginManager();
        if (manager.getPlugin(name) == null) return;

        compatibilityManager.addCompatibility(name, supplier.get());
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

        // Enable bStats so we can track which versions we should keep supporting.
        new Metrics(this, BSTATS_ID);

        logger.info("Loading skin files...");

        saveResource("skins/female.yml");
        saveResource("skins/male.yml");

        logger.info("Skins loaded!");
        logger.info("");

        saveResource("names.yml");

        saveDefaultConfig();
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

        registerEvents(
                new BlockListeners(this),
                (inventoryListeners = new InventoryListeners(this)),
                (otherListeners = new OtherListeners(this)),
                (playerListeners = new PlayerListeners(this)),
                (villagerListeners = new VillagerListeners(this)));

        // Used in previous versions, not needed any more.
        FileUtils.deleteQuietly(new File(getDataFolder(), "villagers.yml"));

        PluginCommand command = getCommand("realisticvillagers");
        if (command == null) return;

        MainCommand main = new MainCommand(this);
        command.setExecutor(main);
        command.setTabCompleter(main);

        logLoadingTime(false, now);

        logger.info("****************************************");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();

        if (converter == null || tracker == null) return;

        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (tracker.isInvalid(villager, true)) continue;
                converter.getNPC(villager).ifPresent(IVillagerNPC::stopExchangeables);
            }
        }
    }

    private void logLoadingTime(boolean loading, long now) {
        String time = String.format(Locale.ROOT, "%.3fs", (double) (System.nanoTime() - now) / 1.0E9);
        getLogger().info((loading ? "Loading" : "Enabling") + " took " + time + "!");
    }

    private void fillIgnoredSections(FileConfiguration config) {
        for (String guiType : GUI_TYPES) {
            ConfigurationSection section = config.getConfigurationSection("gui." + guiType + ".items");
            if (section == null) continue;

            for (String key : section.getKeys(false)) {
                SPECIAL_SECTIONS.add("gui." + guiType + ".items." + key);
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

                    // Refresh schedules.
                    converter.refreshSchedules();

                    // Refresh brains sync to prevent issues.
                    getServer().getScheduler().runTask(this, () -> {
                        for (World world : getServer().getWorlds()) {
                            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                                if (tracker.isInvalid(villager, true)) continue;
                                converter.getNPC(villager).ifPresent(IVillagerNPC::refreshBrain);
                            }
                        }
                    });

                    if (tracker == null) tracker = new VillagerTracker(this);
                    if (worlds == null) worlds = Config.WORLDS_FILTER_WORLDS.asStringList();
                },
                file -> saveDefaultConfig(),
                config -> {
                    fillIgnoredSections(config);
                    return SPECIAL_SECTIONS.stream().filter(config::contains).toList();
                },
                ConfigChanges.builder()
                        .addChange(
                                noVersion,
                                temp -> {
                                    String pathToInfoLore = "gui.main.items.information.lore";

                                    List<String> lore = temp.getStringList(pathToInfoLore);
                                    if (lore.isEmpty()) return;

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
                                        if (name != null) temp.set(path.replace("_", "-"), name);
                                    }
                                },
                                4)
                        .addChange(
                                aimVersion(4),
                                temp -> {
                                    List<String> lines = temp.getStringList("custom-nametags.lines");
                                    if (lines.isEmpty()) return;

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

        // messages.yml
        updateConfig(
                pluginFolder,
                "messages.yml",
                file -> messages.setConfiguration(YamlConfiguration.loadConfiguration(file)),
                file -> saveResource("messages.yml"),
                emptyIgnore,
                ConfigChanges.builder()
                        // Previously @interact-fail.not-allowed was a single line message, now is a map; only for V = X.
                        .addChange(
                                noVersion,
                                temp -> temp.set("interact-fail.not-allowed", null),
                                1)
                        .build());

        // male.yml & female.yml (these shouldn't be modified directly by admins, only using the skin GUI).
        loadConsumer.accept(new File(skinFolder, "male.yml"));
        loadConsumer.accept(new File(skinFolder, "female.yml"));

        // names.yml
        updateConfig(
                pluginFolder,
                "names.yml",
                loadConsumer,
                file -> saveResource("names.yml"),
                emptyIgnore,
                Collections.emptyList());
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
        if (resource != null) return resource;

        if (!name.equals("male.yml") && !name.equals("female.yml")) return null;

        try {
            File file = new File(getSkinFolder(), name);
            if (!file.exists()) return null;

            URL url = file.toURI().toURL();

            URLConnection connection = url.openConnection();
            connection.setUseCaches(false);

            return connection.getInputStream();
        } catch (IOException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    private void handleConfigChanges(@NotNull File file, FileConfiguration config, @NotNull Predicate<FileConfiguration> predicate, Consumer<FileConfiguration> consumer, int newVersion) {
        if (!predicate.test(config)) return;

        int previousVersion = config.getInt("config-version", 0);
        getLogger().info("Updated {%s} config to v{%s} (from v{%s})".formatted(file.getName(), newVersion, previousVersion));

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
        List<String> ingredients = getConfig().getStringList(item + ".crafting.ingredients");
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
        FileConfiguration config = getConfig();

        String name = config.getString(path + ".display-name");
        List<String> lore = config.getStringList(path + ".lore");

        String url = config.getString(path + ".url");

        String materialPath = path + ".material";

        String materialName = config.getString(materialPath, "STONE");
        Material material = PluginUtils.getOrNull(Material.class, materialName);

        ItemBuilder builder = new ItemBuilder(material)
                .setData(itemIdKey, PersistentDataType.STRING, path.contains(".") ? path.substring(path.lastIndexOf(".") + 1) : path)
                .setLore(lore);

        if (name != null) builder.setDisplayName(name);

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
        if (modelData != Integer.MIN_VALUE) builder.setCustomModelData(modelData);

        for (String enchantmentString : config.getStringList(path + ".enchantments")) {
            if (Strings.isNullOrEmpty(enchantmentString)) continue;
            String[] data = PluginUtils.splitData(enchantmentString);

            Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(data[0].toLowerCase(Locale.ROOT)));

            int level;
            try {
                level = PluginUtils.getRangedAmount(data[1]);
            } catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
                level = 1;
            }

            if (enchantment != null) builder.addEnchantment(enchantment, level);
        }

        for (String flag : config.getStringList(path + ".flags")) {
            ItemFlag flagValue = PluginUtils.getOrNull(ItemFlag.class, flag.toUpperCase(Locale.ROOT));
            if (flagValue != null) builder.addItemFlags(flagValue);
        }

        String tippedArrow = config.getString(path + ".tipped");
        if (tippedArrow != null) {
            PotionType potionType = PluginUtils.getValidPotionType(tippedArrow);
            if (potionType != null) builder.setBasePotionData(potionType);
        }

        Object leather = config.get(path + ".leather-color");
        if (leather instanceof String leatherColor) {
            Color color = PluginUtils.getColor(leatherColor);
            if (color != null) builder.setLeatherArmorMetaColor(color);
        } else if (leather instanceof List<?> list) {
            List<Color> colors = new ArrayList<>();

            for (Object object : list) {
                if (!(object instanceof String string)) continue;
                if (string.equalsIgnoreCase("$RANDOM")) continue;

                Color color = PluginUtils.getColor(string);
                if (color != null) colors.add(color);
            }

            if (!colors.isEmpty()) {
                Color color = colors.get(RandomUtils.nextInt(0, colors.size()));
                builder.setLeatherArmorMetaColor(color);
            }
        }

        if (config.contains(path + ".firework")) {
            ConfigurationSection section = config.getConfigurationSection(path + ".firework.firework-effects");
            if (section == null) return builder;

            Set<FireworkEffect> effects = new HashSet<>();
            for (String effect : section.getKeys(false)) {
                FireworkEffect.Builder effectBuilder = FireworkEffect.builder();

                String type = config.getString(path + ".firework.firework-effects." + effect + ".type");
                if (type == null) continue;

                FireworkEffect.Type effectType = PluginUtils.getOrEitherRandomOrNull(FireworkEffect.Type.class, type);

                boolean flicker = config.getBoolean(path + ".firework.firework-effects." + effect + ".flicker");
                boolean trail = config.getBoolean(path + ".firework.firework-effects." + effect + ".trail");

                effects.add((effectType != null ?
                        effectBuilder.with(effectType) :
                        effectBuilder)
                        .flicker(flicker)
                        .trail(trail)
                        .withColor(getColors(config, path, effect, "colors"))
                        .withFade(getColors(config, path, effect, "fade-colors"))
                        .build());
            }

            String powerString = config.getString(path + ".firework.power");
            int power = PluginUtils.getRangedAmount(powerString != null ? powerString : "");

            if (!effects.isEmpty()) builder.initializeFirework(power, effects.toArray(new FireworkEffect[0]));
        }

        String damageString = config.getString(path + ".damage");
        if (damageString != null) {
            int maxDurability = builder.build().getType().getMaxDurability();

            int damage;
            if (damageString.equalsIgnoreCase("$RANDOM")) {
                damage = RandomUtils.nextInt(1, maxDurability);
            } else if (damageString.contains("%")) {
                damage = Math.round(maxDurability * ((float) PluginUtils.getRangedAmount(damageString.replace("%", "")) / 100));
            } else {
                damage = PluginUtils.getRangedAmount(damageString);
            }

            if (damage > 0) builder.setDamage(Math.min(damage, maxDurability));
        }

        return builder;
    }

    public String getNPCTextureURL(@Nullable IVillagerNPC npc) {
        if (Config.DISABLE_SKINS.asBool()) return VILLAGER_HEAD_TEXTURE;

        if (npc == null) return UNKNOWN_HEAD_TEXTURE;

        TextureProperty textures = tracker.getTextures(npc.getSex(), "none", npc.getSkinTextureId());
        return textures.getName().equals("error") ? UNKNOWN_HEAD_TEXTURE : PluginUtils.getURLFromTexture(textures.getValue());
    }

    private @NotNull Set<Color> getColors(@NotNull FileConfiguration config, String path, String effect, String needed) {
        Set<Color> colors = new HashSet<>();
        for (String colorString : config.getStringList(path + ".firework.firework-effects." + effect + "." + needed)) {
            Color color = PluginUtils.getColor(colorString);
            if (color != null) colors.add(color);
        }
        return colors;
    }

    @SuppressWarnings("SameParameterValue")
    public void saveResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    public boolean isMarried(@NotNull Player player) {
        String partner = player.getPersistentDataContainer().get(marriedWith, PersistentDataType.STRING);
        if (partner == null) return false;

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
            if (type == null) continue;

            Class<? extends Entity> clazz = type.getEntityClass();
            if (clazz == null || !Monster.class.isAssignableFrom(clazz)) continue;

            defaultTargets.add(entity);
        }
    }

    public void reloadWantedItems() {
        wantedItems.clear();

        Set<Gift> wanted = giftManager.getGiftsFromCategory("default-wanted-items");
        wantedItems.addAll(wanted);
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
        if (type == null || !FILTER_TYPES.contains(type.toUpperCase(Locale.ROOT))) return true;

        boolean contains = worlds.contains(world);
        return type.equalsIgnoreCase("WHITELIST") == contains;
    }

    public Gift getWantedItem(IVillagerNPC npc, ItemStack item, boolean isItemPickup) {
        // Not really a gift, but we use the same system.
        return GiftCategory.appliesToVillager(wantedItems, npc, item, isItemPickup);
    }

    public @Nullable LivingEntity getUnloadedOffline(@NotNull IVillagerNPC offline) {
        LivingEntity bukkit = offline.bukkit();
        if (bukkit != null) return bukkit;

        Location location = offline.getLastKnownPosition().asLocation();
        if (location.getWorld() == null) return null;

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
        if (invalidLoots()) return;

        Optional<IVillagerNPC> npc = converter.getNPC(living);
        if (npc.isEmpty()
                || npc.get().isEquipped()
                || !force
                || tracker.isInvalid(living, true)) return;

        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) return;

        Map<EquipmentSlot, ItemLoot> equipped = new HashMap<>();
        npc.get().setEquipped(true);

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String name = slotName(slot);
            List<ItemLoot> loots = this.loots.get(name);
            if (loots == null) continue;

            double chance = Math.random();
            for (ItemLoot loot : loots) {
                if (chance > loot.chance()) continue;

                ItemStack item = loot.getItem();
                if (item == null) continue;

                equipment.setItem(slot, item);
                equipped.put(slot, loot);
                break;
            }
        }

        List<ItemLoot> loots = this.loots.get("inventory-items");
        if (loots == null) return;

        double chance = Math.random();
        for (ItemLoot loot : loots) {
            if (chance > loot.chance()) continue;

            ItemStack item = loot.getItem();
            if (item == null) continue;

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
        if (loots.isEmpty()) return true;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (loots.get(slotName(slot)) != null) return false;
        }

        return loots.get("inventory-items") == null;
    }

    private boolean testBothHand(Map<EquipmentSlot, ItemLoot> equipped, Predicate<ItemStack> predicate) {
        return testHand(equipped, predicate, EquipmentSlot.HAND) || testHand(equipped, predicate, EquipmentSlot.OFF_HAND);
    }

    private boolean testHand(@NotNull Map<EquipmentSlot, ItemLoot> equipped, Predicate<ItemStack> predicate, EquipmentSlot slot) {
        ItemLoot hand = equipped.get(slot);
        if (hand == null) return false;

        return predicate.test(hand.getItem());
    }

    public @NotNull List<ItemLoot> createLoot(String sector) {
        return createLoot(sector, null);
    }

    public @NotNull List<ItemLoot> createLoot(String sector, @Nullable EquipmentSlot part) {
        FileConfiguration config = getConfig();

        String name = sector + (part != null ? "." + slotName(part) : "");

        ConfigurationSection section = config.getConfigurationSection("spawn-loot." + name);
        if (section == null) return Collections.emptyList();

        List<ItemLoot> loots = new ArrayList<>();
        for (String path : section.getKeys(false)) {
            double chance = config.getDouble("spawn-loot." + name + "." + path + ".chance", 1.0d);

            boolean onlyForRangeWeapon = config.getBoolean("spawn-loot." + name + "." + path + ".only-for-range-weapon");
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
        return getConfig().getString(
                String.format("variable-text.profession.%s.%s", sex, profession),
                PluginUtils.capitalizeFully(profession));
    }

    public static @Nullable OfflineDataWrapper villagerDataFromPDC(RealisticVillagers plugin, PersistentDataContainer container) {
        try {
            return container.get(plugin.getNpcValuesKey(), RealisticVillagers.VILLAGER_DATA);
        } catch (Exception exception) {
            return null;
        }
    }

    public static @Nullable OfflineDataWrapper villagerDataFromPrimitive(byte[] primitive, PersistentDataAdapterContext context) {
        try {
            return VILLAGER_DATA.fromPrimitive(primitive, context);
        } catch (Exception exception) {
            return null;
        }
    }
}