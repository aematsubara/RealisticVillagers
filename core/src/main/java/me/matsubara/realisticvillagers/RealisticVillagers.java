package me.matsubara.realisticvillagers;

import com.comphenix.protocol.wrappers.Pair;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.tchristofferson.configupdater.ConfigUpdater;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.command.MainCommand;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.types.WhistleGUI;
import me.matsubara.realisticvillagers.listener.InventoryListeners;
import me.matsubara.realisticvillagers.listener.OtherListeners;
import me.matsubara.realisticvillagers.listener.PlayerListeners;
import me.matsubara.realisticvillagers.listener.VillagerListeners;
import me.matsubara.realisticvillagers.manager.ChestManager;
import me.matsubara.realisticvillagers.manager.ExpectingManager;
import me.matsubara.realisticvillagers.manager.InteractCooldownManager;
import me.matsubara.realisticvillagers.manager.gift.Gift;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.manager.gift.GiftManager;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.ItemStackUtils;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Shape;
import net.wesjd.anvilgui.AnvilGUI;
import org.apache.commons.io.FileUtils;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Getter
public final class RealisticVillagers extends JavaPlugin {

    private static final String SKINS_REPO = "https://raw.githubusercontent.com/aematsubara/villager-skins/main/";

    private final NamespacedKey giftKey = key("GiftUUID");
    private final NamespacedKey marriedWith = key("MarriedWith");
    private final NamespacedKey procreationKey = key("Procreation");
    private final NamespacedKey motherUUIDKey = key("MotherUUID");
    private final NamespacedKey isRingKey = key("IsRing");
    private final NamespacedKey isWhistleKey = key("IsWhistle");
    private final NamespacedKey entityTypeKey = key("EntityType");
    private final NamespacedKey chatInteractionTypeKey = key("ChatInteractionType");
    private final NamespacedKey childNameKey = key("ChildName");
    private final NamespacedKey childSexKey = key("ChildSex");
    private final NamespacedKey zombieTransformKey = key("ZombieTransform");
    private final NamespacedKey fishedKey = key("Fished");
    private final NamespacedKey npcValuesKey = key("VillagerNPCValues");
    private final NamespacedKey tamedByPlayerKey = key("TamedByPlayer");
    private final NamespacedKey isBeingLootedKey = key("IsBeingLooted");
    private final NamespacedKey ignoreVillagerKey = key("IgnoreVillager");
    private final NamespacedKey villagerNameKey = key("VillagerName");
    private final NamespacedKey divorcePapersKey = key("DivorcePapers");
    private final NamespacedKey raidStatsKey = key("RaidStats");
    private final NamespacedKey skinDataKey = key("SkinDataID");

    private InventoryListeners inventoryListeners;
    private OtherListeners otherListeners;
    private PlayerListeners playerListeners;
    private VillagerListeners villagerListeners;

    private VillagerTracker tracker;
    private @Setter Shape ring;
    private @Setter Shape whistle;

    private GiftManager giftManager;
    private ChestManager chestManager;
    private ExpectingManager expectingManager;
    private InteractCooldownManager cooldownManager;

    private Messages messages;
    private INMSConverter converter;

    private final List<String> defaultTargets = new ArrayList<>();
    private final Set<Gift> wantedItems = new HashSet<>();
    private final Map<String, List<ItemLoot>> loots = new HashMap<>();
    private final Consumer<File> loadConsumer = file -> tracker.getFiles().put(file.getName(), new Pair<>(file, YamlConfiguration.loadConfiguration(file)));

    private List<String> worlds;

    public static final List<AnvilGUI.ResponseAction> CLOSE_RESPONSE = Collections.singletonList(AnvilGUI.ResponseAction.close());
    private static final List<String> FILTER_TYPES = List.of("WHITELIST", "BLACKLIST");
    private static final List<String> SPECIAL_SECTIONS = Lists.newArrayList("baby", "spawn-loot", "wedding-ring", "whistle", "divorce-papers", "change-skin", "gui.main.frame");
    private static final List<String> GUI_TYPES = List.of("main", "equipment", "combat", "whistle", "skin", "new-skin");
    private static final List<String> ITEMS_SECTIONS = List.of(
            "slot",
            "material",
            "display-name",
            "lore",
            "url",
            "flags",
            "model-data",
            "crafting",
            "enchantments",
            "leather-color",
            "damage",
            "tipped",
            "amount",
            "firework");

    @Override
    public void onLoad() {
        String internalName = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3].toLowerCase();
        try {
            Class<?> converterClass = Class.forName(INMSConverter.class.getPackageName() + "." + internalName + ".NMSConverter");
            Constructor<?> converterConstructor = converterClass.getConstructor(getClass());
            converter = (INMSConverter) converterConstructor.newInstance(this);
            converter.registerEntity();
        } catch (ReflectiveOperationException exception) {
            getLogger().severe("NMSConverter couldn't find a valid implementation for this server version.");
            exception.printStackTrace();
        }
    }

    @Override
    public void onEnable() {
        PluginManager manager = getServer().getPluginManager();
        if (manager.getPlugin("ProtocolLib") == null) {
            getLogger().severe("This plugin requires ProtocolLib, disabling...");
            manager.disablePlugin(this);
            return;
        }

        saveSkins("male");
        saveSkins("female");
        saveResource("names.yml");

        saveDefaultConfig();
        messages = new Messages(this);

        // Update files async since we modify a lot of files.
        CompletableFuture.runAsync(this::updateConfigs).thenRun(() -> getServer().getScheduler().runTask(this, () -> {
            giftManager = new GiftManager(this);
            chestManager = new ChestManager(this);
            expectingManager = new ExpectingManager(this);
            cooldownManager = new InteractCooldownManager(this);

            ring = createWeddingRing();
            whistle = createWhistle();

            converter.loadData();

            reloadDefaultTargetEntities();
            reloadWantedItems();
            reloadLoots();

            registerEvents(
                    (inventoryListeners = new InventoryListeners(this)),
                    (otherListeners = new OtherListeners(this)),
                    (playerListeners = new PlayerListeners(this)),
                    (villagerListeners = new VillagerListeners(this)));

            // Used in previous versions, not needed anymore.
            FileUtils.deleteQuietly(new File(getDataFolder(), "villagers.yml"));

            PluginCommand command = getCommand("realisticvillagers");
            if (command == null) return;

            MainCommand main = new MainCommand(this);
            command.setExecutor(main);
            command.setTabCompleter(main);
        }));
    }

    private void loadFileOrCreate(String folder, String fileName) {
        File file = new File(folder, fileName);
        if (!file.exists()) createFile(file);
    }

    @Override
    public void onDisable() {
        if (converter == null || tracker == null) return;

        for (World world : Bukkit.getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                if (!tracker.isInvalid(villager)) converter.getNPC(villager).ifPresent(IVillagerNPC::stopExchangeables);
            }
        }
    }

    private void fillIgnoredSections() {
        for (String guiType : GUI_TYPES) {
            ConfigurationSection section = getConfig().getConfigurationSection("gui." + guiType + ".items");
            if (section == null) continue;

            for (String key : section.getKeys(false)) {
                for (String itemSettingType : ITEMS_SECTIONS) {
                    SPECIAL_SECTIONS.add("gui." + guiType + ".items." + key + "." + itemSettingType);
                }
            }
        }
    }

    private void registerEvents(Listener @NotNull ... listeners) {
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void createFile(@NotNull File file) {
        try {
            file.createNewFile();
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public void updateConfigs() {
        String pluginFolder = getDataFolder().getPath();
        String skinFolder = getSkinFolder();

        // config.yml
        updateConfig(
                pluginFolder,
                "config.yml",
                file -> {
                    reloadConfig();
                    fillIgnoredSections();
                    tracker = new VillagerTracker(this);
                    worlds = Config.WORLDS_FILTER_WORLDS.asStringList();
                },
                file -> saveDefaultConfig());

        // messages.yml
        updateConfig(
                pluginFolder,
                "messages.yml",
                file -> messages.setConfiguration(YamlConfiguration.loadConfiguration(file)),
                file -> saveResource("messages.yml"));

        // male.yml & female.yml (these shouldn't be modified directly by admins, only using the skin GUI).
        loadConsumer.accept(new File(skinFolder, "male.yml"));
        loadConsumer.accept(new File(skinFolder, "female.yml"));

        // names.yml
        updateConfig(
                pluginFolder,
                "names.yml",
                loadConsumer,
                file -> saveResource("names.yml"));
    }

    public void updateConfig(String folderName, String fileName, Consumer<File> reloadAfterUpdating, Consumer<File> resetConfiguration) {
        File file = new File(folderName, fileName);
        FileConfiguration config = PluginUtils.reloadConfig(this, file, resetConfiguration);
        if (config == null) {
            getLogger().severe("Can't find {" + file.getName() + "}!");
            return;
        }

        Predicate<FileConfiguration> noVersion = temp -> !temp.contains("config-version");

        // We need to change the previous %partner% to %current-partner% since now %partner% behaves differently (vX.X{0} -> v3.0{1}).
        handleConfigChanges(
                file,
                config,
                "config.yml",
                noVersion,
                temp -> {
                    String pathToInfoLore = "gui.main.items.information.lore";

                    List<String> lore = temp.getStringList(pathToInfoLore);
                    if (lore.isEmpty()) return;

                    lore.replaceAll(line -> line.replace("%partner%", "%current-partner%"));
                    temp.set(pathToInfoLore, lore);
                },
                1);

        // Previously @interact-fail.not-allowed was a single line message, now is a map.
        handleConfigChanges(
                file,
                config,
                "messages.yml",
                noVersion,
                temp -> temp.set("interact-fail.not-allowed", null),
                1);

        try {
            ConfigUpdater.update(
                    this,
                    fileName,
                    file,
                    fileName.equals("config.yml") ? SPECIAL_SECTIONS.stream().filter(config::contains).toList() : null);
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

    @SuppressWarnings("SameParameterValue")
    private void handleConfigChanges(@NotNull File file, FileConfiguration config, String fileTargetName, Predicate<FileConfiguration> predicate, Consumer<FileConfiguration> consumer, int newVersion) {
        if (!file.getName().equals(fileTargetName) || !predicate.test(config)) return;

        consumer.accept(config);
        config.set("config-version", newVersion);

        try {
            config.save(file);
        } catch (IOException exception) {
            exception.printStackTrace();
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
        return createCraftableItem("whistle", "whistle", isWhistleKey);
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

    public ItemBuilder getItem(String path) {
        FileConfiguration config = getConfig();

        String name = config.getString(path + ".display-name");
        List<String> lore = config.getStringList(path + ".lore");

        String url = config.getString(path + ".url");

        String materialPath = path + ".material";

        String materialName = config.getString(materialPath, "STONE");
        Material material = PluginUtils.getOrNull(Material.class, materialName);

        ItemBuilder builder = new ItemBuilder(material).setLore(lore);
        if (name != null) builder.setDisplayName(name);

        String amountString = config.getString(path + ".amount");
        if (amountString != null) {
            int amount = PluginUtils.getRangedAmount(amountString);
            builder.setAmount(amount);
        }

        if (material == Material.PLAYER_HEAD && url != null) {
            builder.setHead(url, true);
        }

        for (String flag : config.getStringList(path + ".flags")) {
            builder.addItemFlags(ItemFlag.valueOf(flag.toUpperCase()));
        }

        int modelData = config.getInt(path + ".model-data", Integer.MIN_VALUE);
        if (modelData != Integer.MIN_VALUE) builder.setCustomModelData(modelData);

        for (String enchantmentString : config.getStringList(path + ".enchantments")) {
            if (Strings.isNullOrEmpty(enchantmentString)) continue;
            String[] data = PluginUtils.splitData(enchantmentString);

            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(data[0].toLowerCase()));

            int level;
            try {
                level = PluginUtils.getRangedAmount(data[1]);
            } catch (IndexOutOfBoundsException | IllegalArgumentException exception) {
                level = 1;
            }

            if (enchantment != null) builder.addEnchantment(enchantment, level);
        }

        String tippedArrow = config.getString(path + ".tipped");
        if (tippedArrow != null) {
            PotionType potionType = PluginUtils.getOrEitherRandomOrNull(PotionType.class, tippedArrow);
            if (potionType != null) builder.setBasePotionData(potionType);
        }

        String leatherColor = config.getString(path + ".leather-color");
        if (leatherColor != null) {
            Color color = PluginUtils.getColor(leatherColor);
            if (color != null) builder.setLeatherArmorMetaColor(color);
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
                damage = ThreadLocalRandom.current().nextInt(1, maxDurability);
            } else if (damageString.contains("%")) {
                damage = Math.round(maxDurability * ((float) PluginUtils.getRangedAmount(damageString.replace("%", "")) / 100));
            } else {
                damage = PluginUtils.getRangedAmount(damageString);
            }

            if (damage > 0) builder.setDamage(Math.min(damage, maxDurability));
        }

        return builder;
    }

    private @NotNull Set<Color> getColors(@NotNull FileConfiguration config, String path, String effect, String needed) {
        Set<Color> colors = new HashSet<>();
        for (String colorString : config.getStringList(path + ".firework.firework-effects." + effect + "." + needed)) {
            Color color = PluginUtils.getColor(colorString);
            if (color != null) colors.add(color);
        }
        return colors;
    }

    private void saveSkins(String sex) {
        String name = sex + ".yml";
        saveFile(SKINS_REPO + name, getSkinFolder(), name);
    }

    private void saveFile(String url, String outputFolder, String outputFile) {
        try {
            File file = new File(outputFolder, outputFile);
            if (file.exists()) return;

            FileUtils.copyURLToFile(new URL(url), file);
        } catch (IOException exception) {
            exception.printStackTrace();
            loadFileOrCreate(getSkinFolder(), outputFile);
        }
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
            try {
                EntityType type = EntityType.valueOf(entity.toUpperCase());

                Class<? extends Entity> clazz = type.getEntityClass();
                if (clazz == null || !Monster.class.isAssignableFrom(clazz)) continue;

                defaultTargets.add(entity);
            } catch (IllegalArgumentException ignored) {

            }
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

    public boolean isEnabledIn(World world) {
        String type = Config.WORLDS_FILTER_TYPE.asString();
        if (type == null || !FILTER_TYPES.contains(type.toUpperCase())) return true;

        boolean contains = worlds.contains(world.getName());
        return type.equalsIgnoreCase("WHITELIST") == contains;
    }

    public Gift getWantedItem(IVillagerNPC npc, ItemStack item, boolean isItemPickup) {
        // Not really a gift, but we use the same system.
        return GiftCategory.appliesToVillager(wantedItems, npc, item, isItemPickup);
    }

    public @Nullable Villager getUnloadedOffline(@NotNull IVillagerNPC offline) {
        Villager bukkit = offline.bukkit();
        if (bukkit != null) return bukkit;

        Location location = offline.getLastKnownPosition().asLocation();
        if (location.getWorld() == null) return null;

        Chunk chunk = location.getWorld().getChunkAt(location);
        chunk.load();
        chunk.getEntities();

        Entity inChunk = Bukkit.getEntity(offline.getUniqueId());
        return inChunk instanceof Villager villager ? villager : null;
    }

    public void openWhistleGUI(Player player, @Nullable String keyword) {
        List<IVillagerNPC> family = tracker.getOfflineVillagers()
                .stream()
                .filter(offline -> {
                    Villager bukkit = offline.bukkit();
                    UUID playerUUID = player.getUniqueId();
                    if (bukkit != null) {
                        Optional<IVillagerNPC> online = converter.getNPC(bukkit);
                        return online.isPresent() && online.get().isFamily(playerUUID, true);
                    } else {
                        return offline.isFamily(playerUUID, true);
                    }
                }).toList();

        if (family.isEmpty()) {
            messages.send(player, Messages.Message.WHISTLE_NO_FAMILY);
            return;
        }

        new WhistleGUI(this, player, family.stream(), keyword);
    }

    public void equipVillager(Villager villager, boolean force) {
        Optional<IVillagerNPC> npc = converter.getNPC(villager);
        if (npc.isEmpty() || npc.get().isEquipped() || !force) return;

        EntityEquipment equipment = villager.getEquipment();
        if (equipment == null) return;

        Map<EquipmentSlot, ItemLoot> equipped = new HashMap<>();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            String name = slotName(slot);
            List<ItemLoot> loots = this.loots.get(name);

            double chance = Math.random();
            for (ItemLoot loot : loots) {
                if (chance > loot.chance()) continue;

                ItemStack item = loot.getItem();
                if (item == null) continue;

                equipment.setItem(slot, loot.randomVanillaEnchantments() ?
                        converter.randomVanillaEnchantments(villager.getLocation(), item) :
                        item);

                equipped.put(slot, loot);
                break;
            }
        }

        double chance = Math.random();
        for (ItemLoot loot : loots.get("inventory-items")) {
            if (chance > loot.chance()) continue;

            ItemStack item = loot.getItem();
            if (item == null) continue;

            if ((loot.forRange() && testBothHand(equipped, ItemStackUtils::isRangeWeapon))
                    || (loot.bow() && testBothHand(equipped, inHand -> inHand.getType() == Material.BOW))
                    || (loot.crossbow() && testBothHand(equipped, inHand -> inHand.getType() == Material.CROSSBOW))) {

                if (loot.randomVanillaEnchantments()) {
                    item = converter.randomVanillaEnchantments(villager.getLocation(), item);
                }

                if (loot.offHandIfPossible() && equipped.get(EquipmentSlot.OFF_HAND) == null) {
                    equipment.setItemInOffHand(item);
                    continue;
                }

                villager.getInventory().addItem(item);
            }
        }

        npc.get().setEquipped(true);
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

            boolean randomVanillaEnchantments = config.getBoolean("spawn-loot." + name + "." + path + ".random-vanilla-enchantments");
            boolean offHandIfPossible = config.getBoolean("spawn-loot." + name + "." + path + ".off-hand-if-possible");

            loots.add(new ItemLoot(
                    () -> getItem("spawn-loot." + name + "." + path).build(),
                    chance,
                    onlyForBow,
                    onlyForCrossbow,
                    randomVanillaEnchantments,
                    offHandIfPossible));
        }

        loots.sort(Comparator.comparingDouble(ItemLoot::chance));
        return loots;
    }

    private @NotNull String slotName(@NotNull EquipmentSlot slot) {
        return slot.name().toLowerCase().replace("_", "-");
    }

    private record ItemLoot(
            Supplier<ItemStack> item,
            double chance,
            boolean bow,
            boolean crossbow,
            boolean randomVanillaEnchantments,
            boolean offHandIfPossible) {

        public boolean forRange() {
            return bow && crossbow;
        }

        public ItemStack getItem() {
            return item.get();
        }
    }

    @Contract("_ -> new")
    public @NotNull NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    @Contract(pure = true)
    public @NotNull String getSkinFolder() {
        return getDataFolder() + File.separator + "skins";
    }

    public String getProfessionFormatted(Villager.@NotNull Profession profession) {
        return getProfessionFormatted(profession.name().toLowerCase());
    }

    public String getProfessionFormatted(String profession) {
        return getConfig().getString("variable-text.profession." + profession, PluginUtils.capitalizeFully(profession));
    }
}