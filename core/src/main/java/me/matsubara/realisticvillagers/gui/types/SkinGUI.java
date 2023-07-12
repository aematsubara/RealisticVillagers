package me.matsubara.realisticvillagers.gui.types;

import com.comphenix.protocol.wrappers.Pair;
import com.mojang.authlib.properties.PropertyMap;
import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.files.Messages;
import me.matsubara.realisticvillagers.gui.PaginatedGUI;
import me.matsubara.realisticvillagers.util.InventoryUpdate;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Getter
public class SkinGUI extends PaginatedGUI {

    private final boolean isMale;
    private final boolean isAdult;
    private final String keyword;
    private final ItemStack close;
    private final ItemStack previous;
    private final ItemStack next;
    private final ItemStack search;
    private final ItemStack clearSearch;
    private final ItemStack male;
    private final ItemStack female;
    private final ItemStack adult;
    private final ItemStack kid;
    private final ItemStack clearSkin;
    private final ItemStack newSkin;
    private final Map<Villager.Profession, ItemStack> professionItems = new LinkedHashMap<>();
    private @Setter Villager.Profession currentProfession;

    private static final int AGE_STAGE_SLOT = 0;
    private static final int PROFESSION_SLOT = 27;
    private static final int CLOSE_SLOT = 35;
    private static final int PREVIOUS_SLOT = 19;
    private static final int NEXT_SLOT = 25;
    private static final int TOGGLE_SEX = 21;
    private static final int SEARCH_SLOT = 22;
    private static final int CLEAR_SKIN = 23;
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Map<Villager.Profession, Material> PROFESSION_ICON = new LinkedHashMap<>();
    public static final Map<Integer, ItemStack> CACHE_MALE_HEADS = new ConcurrentHashMap<>();
    public static final Map<Integer, ItemStack> CACHE_FEMALE_HEADS = new ConcurrentHashMap<>();

    static {
        PROFESSION_ICON.put(Villager.Profession.NONE, Material.BARRIER);
        PROFESSION_ICON.put(Villager.Profession.ARMORER, Material.BLAST_FURNACE);
        PROFESSION_ICON.put(Villager.Profession.BUTCHER, Material.SMOKER);
        PROFESSION_ICON.put(Villager.Profession.CARTOGRAPHER, Material.CARTOGRAPHY_TABLE);
        PROFESSION_ICON.put(Villager.Profession.CLERIC, Material.BREWING_STAND);
        PROFESSION_ICON.put(Villager.Profession.FARMER, Material.COMPOSTER);
        PROFESSION_ICON.put(Villager.Profession.FISHERMAN, Material.BARREL);
        PROFESSION_ICON.put(Villager.Profession.FLETCHER, Material.FLETCHING_TABLE);
        PROFESSION_ICON.put(Villager.Profession.LEATHERWORKER, Material.CAULDRON);
        PROFESSION_ICON.put(Villager.Profession.LIBRARIAN, Material.LECTERN);
        PROFESSION_ICON.put(Villager.Profession.MASON, Material.STONECUTTER);
        PROFESSION_ICON.put(Villager.Profession.NITWIT, Material.BARRIER);
        PROFESSION_ICON.put(Villager.Profession.SHEPHERD, Material.LOOM);
        PROFESSION_ICON.put(Villager.Profession.TOOLSMITH, Material.SMITHING_TABLE);
        PROFESSION_ICON.put(Villager.Profession.WEAPONSMITH, Material.GRINDSTONE);
    }

    private SkinGUI(RealisticVillagers plugin, Player player, List<ItemStack> heads, boolean isMale, boolean isAdult, @Nullable Integer page, @Nullable String keyword) {
        super(plugin, null, "skin", getValidSize(plugin, "skin", 36), player, heads);

        this.isMale = isMale;
        this.isAdult = isAdult;
        this.current = page != null ? page : 0;
        this.keyword = keyword;

        close = getGUIItem("close");
        previous = getGUIItem("previous");
        next = getGUIItem("next");
        search = getGUIItem("search");
        clearSearch = keyword != null ? getGUIItem("clear-search", string -> string.replace("%keyword%", keyword)) : null;
        male = getGUIItem("male");
        female = getGUIItem("female");
        adult = getGUIItem("adult");
        kid = getGUIItem("kid");
        newSkin = getGUIItem("add-new-skin");

        ItemBuilder builder = new ItemBuilder(getGUIItem("clear-skin"));
        Pair<Integer, PropertyMap> pair = plugin.getTracker().getOldProperties().get(player.getUniqueId());
        if (pair != null) {
            String textures = pair.getSecond().get("textures").iterator().next().getValue();
            builder.setHead(textures, false);
        }
        clearSkin = builder.build();

        for (Villager.Profession profession : Villager.Profession.values()) {
            professionItems.put(profession, new ItemBuilder(getGUIItem("profession"))
                    .setType(PROFESSION_ICON.get(profession))
                    .replace("%profession%", plugin.getProfessionFormatted(profession))
                    .build());
        }

        InventoryUpdate.updateInventory(player, getTitle());
    }

    public static void openMenu(@NotNull RealisticVillagers plugin, Player player, @NotNull String sex, boolean isAdult, @Nullable Integer page, @Nullable String keyword) {
        boolean isMale = sex.equals("male");
        if ((isMale ? SkinGUI.CACHE_MALE_HEADS : SkinGUI.CACHE_FEMALE_HEADS).isEmpty()) {
            plugin.getMessages().send(player, Messages.Message.NO_SKIN_CACHED);
        }
        CompletableFuture.supplyAsync((Supplier<List<ItemStack>>) () -> {
            Pair<File, FileConfiguration> pair = plugin.getTracker().getFile(sex + ".yml");
            FileConfiguration config = pair.getSecond();

            ConfigurationSection section = config.getConfigurationSection("none");
            if (section == null) return Collections.emptyList();

            List<ItemStack> heads = new ArrayList<>();
            for (String skinId : section.getKeys(false).stream().sorted(Comparator.comparingInt(Integer::parseInt)).toList()) {
                ItemStack skinItem = createSkinItem(plugin, config, sex, isAdult, Integer.parseInt(skinId), keyword);
                if (skinItem != null) heads.add(skinItem);
            }
            return heads;
        }).thenAccept(heads -> plugin.getServer().getScheduler().runTask(plugin, () -> new SkinGUI(plugin, player, heads, isMale, isAdult, page, keyword)));
    }

    @Override
    protected String getTitle() {
        return super.getTitle()
                .replace("%sex%", isMale ? Config.MALE.asString() : Config.FEMALE.asString())
                .replace("%age-stage%", isAdult ? Config.ADULT.asString() : Config.KID.asString());
    }

    private static @Nullable ItemStack createSkinItem(@NotNull RealisticVillagers plugin, @NotNull FileConfiguration config, String sex, boolean isAdult, int id, @Nullable String keyword) {
        String texture = config.getString("none." + id + ".texture");
        if (texture == null || texture.isBlank() || texture.isEmpty()) return null;

        boolean forBabies = config.getBoolean("none." + id + ".for-babies");
        if ((isAdult && forBabies) || (!isAdult && !forBabies)) {
            return null;
        }

        String adddedByUUID = config.getString("none." + id + ".added-by", null);
        boolean fromConsole = false;
        OfflinePlayer addedBy = (adddedByUUID != null && !(fromConsole = adddedByUUID.equalsIgnoreCase("Console"))) ? Bukkit.getOfflinePlayer(UUID.fromString(adddedByUUID)) : null;
        boolean validAdder = addedBy != null && addedBy.hasPlayedBefore() && addedBy.getName() != null;

        if (keyword != null) {
            String lowerKeyword = keyword.toLowerCase();
            if (lowerKeyword.startsWith("by:")) {
                if (lowerKeyword.contains("by:unknown")) {
                    // If looking for unknown, the adder should be invalid.
                    if (validAdder || fromConsole) return null;
                } else if (lowerKeyword.contains("by:console")) {
                    if (validAdder || !fromConsole) return null;
                } else if (!validAdder || !lowerKeyword.contains("by:" + addedBy.getName().toLowerCase())) {
                    // If looking for someone, the adder should be valid and the keyword should match the current skin adder.
                    return null;
                }
            } else if (!("#" + id).contains(lowerKeyword)) return null;
        }

        Map<Integer, ItemStack> cache = sex.equals("male") ? CACHE_MALE_HEADS : CACHE_FEMALE_HEADS;
        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        long when = config.getLong("none." + id + ".when", -1L);

        int generated = 0;
        List<String> professions = new ArrayList<>();
        for (Villager.Profession profession : Villager.Profession.values()) {
            if (config.contains(profession.name().toLowerCase() + "." + id)) {
                professions.add("&a" + plugin.getProfessionFormatted(profession));
                generated++;
            } else {
                professions.add("&c" + plugin.getProfessionFormatted(profession));
            }
        }

        String unknown = Config.UNKNOWN.asString();
        String addedByString = validAdder ? addedBy.getName() : fromConsole ? Config.CONSOLE.asString() : unknown;
        String whenString = when != -1L ? TIME_FORMAT.format(new Date(when)) : unknown;

        ItemStack item = new ItemBuilder(plugin.getItem("gui.skin.items.skin").build())
                .setHead(texture, false)
                .setData(plugin.getSkinDataKey(), PersistentDataType.STRING, String.format("%s:%s", sex, id))
                .applyMultiLineLore(professions, "%profession%", "%professions%", "???", String.join(", ", professions))
                .replace("%skin-id%", id)
                .replace("%added-by%", addedByString)
                .replace("%when%", whenString)
                .replace("%generated%", generated)
                .replace("%max-professions%", Villager.Profession.values().length)
                .build();

        cache.put(id, item);
        return item;
    }

    @Override
    public void addButtons() {
        int extra = 9 * (size == 36 ? 0 : size == 45 ? 1 : 2);

        inventory.setItem(AGE_STAGE_SLOT, keyword != null ?
                (!animation.isGuiAnim() ? animation.getDefaultItem() : null) :
                (isAdult ? adult : kid));

        ItemStack professionItem;
        if (professionItems != null) {
            Villager.Profession oldSelected = plugin.getTracker().getSelectedProfession().get(player.getUniqueId());
            professionItem = professionItems.get((currentProfession = oldSelected != null ? oldSelected : Villager.Profession.NONE));
        } else professionItem = null;
        inventory.setItem(PROFESSION_SLOT + extra, professionItem);

        inventory.setItem(CLOSE_SLOT + extra, close);
        if (current > 0) inventory.setItem(PREVIOUS_SLOT + extra, previous);
        if (current < pages - 1) inventory.setItem(NEXT_SLOT + extra, next);
        inventory.setItem(TOGGLE_SEX + extra, keyword != null ? null : isMale ? male : female);
        inventory.setItem(SEARCH_SLOT + extra, keyword != null ? clearSearch : pages > 1 ? search : null);
        inventory.setItem(CLEAR_SKIN + extra, keyword != null ? null : plugin.getTracker().getOldProperties().containsKey(player.getUniqueId()) ? clearSkin : newSkin);
    }
}