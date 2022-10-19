package me.matsubara.realisticvillagers;

import com.google.common.collect.Sets;
import com.tchristofferson.configupdater.ConfigUpdater;
import lombok.Getter;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.listener.InventoryListeners;
import me.matsubara.realisticvillagers.listener.PlayerListeners;
import me.matsubara.realisticvillagers.listener.VillagerListeners;
import me.matsubara.realisticvillagers.manager.ExpectingManager;
import me.matsubara.realisticvillagers.manager.InteractCooldownManager;
import me.matsubara.realisticvillagers.manager.gift.Gift;
import me.matsubara.realisticvillagers.manager.gift.GiftCategory;
import me.matsubara.realisticvillagers.manager.gift.GiftManager;
import me.matsubara.realisticvillagers.nms.INMSConverter;
import me.matsubara.realisticvillagers.tracker.VillagerInfo;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import me.matsubara.realisticvillagers.util.Shape;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

@Getter
public final class RealisticVillagers extends JavaPlugin {

    private final static String SKINS_REPO = "https://raw.githubusercontent.com/aematsubara/villager-skins/main/";

    private final NamespacedKey giftKey = key("GiftUUID");
    private final NamespacedKey marriedWith = key("MarriedWith");
    private final NamespacedKey procreationKey = key("Procreation");
    private final NamespacedKey motherUUIDKey = key("MotherUUID");
    private final NamespacedKey isRingKey = key("IsRing");
    private final NamespacedKey entityTypeKey = key("EntityType");
    private final NamespacedKey chatInteractionTypeKey = key("ChatInteractionType");
    private final NamespacedKey childNameKey = key("ChildName");
    private final NamespacedKey childSexKey = key("ChildSex");
    private final NamespacedKey zombieTransformKey = key("ZombieTransform");
    private final NamespacedKey fishedKey = key("Fished");
    private final NamespacedKey npcValuesKey = key("VillagerNPCValues");
    private final NamespacedKey tamedByPlayerKey = key("TamedByPlayer");

    private VillagerTracker tracker;
    private Shape ring;

    private ExpectingManager expectingManager;
    private GiftManager giftManager;
    private InteractCooldownManager cooldownManager;

    private Messages messages;
    private INMSConverter converter;

    private List<String> defaultTargets;
    private List<String> worlds;
    private Set<Gift> wantedItems;

    private final static Set<String> FILTER_TYPES = Sets.newHashSet("WHITELIST", "BLACKLIST");

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
        }
    }

    @Override
    public void onEnable() {
        if (!getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            getLogger().severe("This plugin requires ProtocolLib, disabling...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveSkins("male");
        saveSkins("female");

        saveResource("names.yml");

        saveDefaultConfig();
        updateConfig("config.yml", this::reloadConfig);

        messages = new Messages(this);
        updateConfig("messages.yml", () -> messages.reloadConfig());

        worlds = Config.WORLDS_FILTER_WORLDS.asStringList();

        expectingManager = new ExpectingManager(this);
        giftManager = new GiftManager(this);
        cooldownManager = new InteractCooldownManager(this);

        tracker = new VillagerTracker(this);
        ring = createWeddingRing();

        defaultTargets = new ArrayList<>();
        initDefaultTargetEntities(defaultTargets);
        reloadWantedItems();

        getServer().getPluginManager().registerEvents(new InventoryListeners(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListeners(this), this);
        getServer().getPluginManager().registerEvents(new VillagerListeners(this), this);
    }

    private void updateConfig(String fileName, Runnable reload) {
        File config = new File(getDataFolder(), fileName);

        try {
            ConfigUpdater.update(this, fileName, config);
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        reload.run();
    }

    public ItemStack createBaby(boolean isBoy, String babyName, long procreation, UUID motherUUID) {
        return getItem("baby." + (isBoy ? "boy" : "girl"))
                .replace("%villager-name%", babyName)
                .setData(getChildNameKey(), PersistentDataType.STRING, babyName)
                .setData(getChildSexKey(), PersistentDataType.STRING, isBoy ? "male" : "female")
                .setData(getProcreationKey(), PersistentDataType.LONG, procreation)
                .setData(getMotherUUIDKey(), PersistentDataType.STRING, motherUUID.toString())
                .build();
    }

    private Shape createWeddingRing() {
        ItemBuilder builder = getItem("wedding-ring").setData(getIsRingKey(), PersistentDataType.INTEGER, 1);

        boolean shaped = getConfig().getBoolean("wedding-ring.crafting.shaped");
        List<String> ingredients = getConfig().getStringList("wedding-ring.crafting.ingredients");
        List<String> shapeList = getConfig().getStringList("wedding-ring.crafting.shape");

        return new Shape(this, "wedding_ring", shaped, ingredients, shapeList, builder.build());
    }

    public ItemBuilder getItem(String path) {
        String name = PluginUtils.translate(getConfig().getString(path + ".display-name"));
        List<String> lore = PluginUtils.translate(getConfig().getStringList(path + ".lore"));

        String url = getConfig().getString(path + ".url");

        String materialPath = path + ".material";

        String materialName = getConfig().getString(materialPath);
        if (materialName == null) getLogger().info("Invalid material at" + materialPath);

        Material material = materialName != null ? getMaterial(materialName) : Material.STONE;

        ItemBuilder builder = new ItemBuilder(material)
                .setDisplayName(name)
                .setLore(lore);

        if (material == Material.PLAYER_HEAD && url != null) {
            builder.setHead(url, true);
        }

        for (String flag : getConfig().getStringList(path + ".flags")) {
            builder.addItemFlags(ItemFlag.valueOf(flag.toUpperCase()));
        }

        int modelData = getConfig().getInt(path + ".model-data", Integer.MIN_VALUE);
        if (modelData != Integer.MIN_VALUE) builder.setCustomModelData(modelData);

        return builder;
    }

    private Material getMaterial(String name) {
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
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
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void saveResource(String name) {
        File file = new File(getDataFolder(), name);
        if (!file.exists()) saveResource(name, false);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("realisticvillagers")) return true;

        if (!sender.hasPermission("realisticvillagers.reload")) {
            sender.sendMessage(messages.getRandomMessage(Messages.Message.NO_PERMISSION));
            return true;
        }

        if (args.length != 1 || !args[0].equalsIgnoreCase("reload")) {
            sender.sendMessage(messages.getRandomMessage(Messages.Message.WRONG_COMMAND));
            return true;
        }

        boolean skinsDisabled = Config.DISABLE_SKINS.asBool();
        boolean nametagsDisabled = Config.DISABLE_NAMETAGS.asBool();

        reloadConfig();
        messages.reloadConfig();
        giftManager.loadGiftCategories();
        initDefaultTargetEntities(defaultTargets);
        sender.sendMessage(messages.getRandomMessage(Messages.Message.RELOAD));

        reloadWantedItems();

        handleChangedOption(
                skinsDisabled,
                Config.DISABLE_SKINS.asBool(),
                (npc, state) -> {
                    if (tracker.isInvalid(npc.bukkit(), true)) return;

                    if (state) {
                        tracker.removeNPC(npc.bukkit().getEntityId());
                        npc.sendSpawnPacket();
                    } else {
                        npc.sendDestroyPacket();
                        tracker.spawnNPC(npc.bukkit());
                    }
                });

        handleChangedOption(
                nametagsDisabled,
                Config.DISABLE_NAMETAGS.asBool(),
                (npc, state) -> tracker.refreshNPC(npc.bukkit()));

        return true;
    }

    private void handleChangedOption(boolean previous, boolean current, BiConsumer<IVillagerNPC, Boolean> consumer) {
        if (previous == current) return;

        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Villager villager)) continue;

                Optional<IVillagerNPC> npc = converter.getNPC(villager);
                if (npc.isEmpty()) continue;

                consumer.accept(npc.get(), current);
            }
        }
    }

    public boolean isMarried(Player player) {
        String partner = player.getPersistentDataContainer().get(marriedWith, PersistentDataType.STRING);
        if (partner == null) return false;

        VillagerInfo partnerInfo = tracker.get(UUID.fromString(partner));
        if (partnerInfo != null && partnerInfo.isDead()) {
            player.getPersistentDataContainer().remove(marriedWith);
            return false;
        }

        return true;
    }

    public String getRandomNameBySex(String sex) {
        File file = new File(getDataFolder(), "names.yml");
        if (!file.exists()) return "";

        FileConfiguration config = new YamlConfiguration();
        try {
            config.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            exception.printStackTrace();
        }

        List<String> names = config.getStringList(sex);
        return names.get(ThreadLocalRandom.current().nextInt(names.size()));
    }

    private void initDefaultTargetEntities(List<String> list) {
        list.clear();

        for (String entity : getConfig().getStringList("default-target-entities")) {
            try {
                EntityType type = EntityType.valueOf(entity.toUpperCase());

                Class<? extends Entity> clazz = type.getEntityClass();
                if (clazz == null || !Monster.class.isAssignableFrom(clazz)) continue;

                list.add(entity);
            } catch (IllegalArgumentException ignored) {

            }
        }
    }

    public boolean isEnabledIn(World world) {
        String type = Config.WORLDS_FILTER_TYPE.asString();
        if (type == null || !FILTER_TYPES.contains(type.toUpperCase())) return true;

        boolean contains = worlds.contains(world.getName());
        return type.equalsIgnoreCase("WHITELIST") == contains;
    }

    public boolean isWantedItem(IVillagerNPC npc, ItemStack item) {
        // Not really a gift, but we use the same system.
        return GiftCategory.appliesToVillager(wantedItems, npc, item);
    }

    private void reloadWantedItems() {
        Set<Gift> wanted = giftManager.getGiftsFromCategory("default-wanted-items");
        if (wantedItems != null) {
            wantedItems.clear();
            wantedItems.addAll(wanted);
        } else {
            wantedItems = wanted;
        }
    }

    public NamespacedKey key(String name) {
        return new NamespacedKey(this, name);
    }

    public String getSkinFolder() {
        return getDataFolder() + File.separator + "skins";
    }
}