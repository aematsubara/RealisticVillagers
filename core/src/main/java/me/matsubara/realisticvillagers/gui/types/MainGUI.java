package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.TriFunction;
import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Getter
@Setter
public final class MainGUI extends InteractGUI {

    private final Player player;
    private int currentTrade = 0;
    private ItemStack chat, greet, story,
            joke, flirt, proud,
            insult, follow, stay,
            inspect, gift, procreate,
            home, divorce, combat,
            papers, info, trade, noTrades;
    private final Function<String, ItemStack> tradeItem = itemName -> {
        List<MerchantRecipe> recipes = npc.bukkit().getRecipes().stream()
                .filter(recipe -> recipe.getUses() < recipe.getMaxUses())
                .toList();

        if (currentTrade >= recipes.size()) currentTrade = 0;

        ItemStack item = new ItemBuilder(getGUIItem(itemName))
                .setType(recipes.get(currentTrade).getResult().getType())
                .addItemFlags(ItemFlag.values())
                .build();

        currentTrade++;
        return item;
    };

    private static final Map<String, Settings> ITEM_SETTINGS = Map.of(
            "procreate", Settings.ONLY_IF_MARRIED,
            "set-home", Settings.ONLY_IF_ALLOWED,
            "combat", Settings.ONLY_IF_ALLOWED,
            "divorce", Settings.ONLY_IF_MARRIED);
    private static final int[] NEXT_LEVEL_XP_THRESHOLDS = {0, 10, 70, 150, 250};

    public MainGUI(RealisticVillagers plugin, IVillagerNPC npc, @NotNull Player player) {
        super(plugin, npc, "main", getValidSize(plugin, "main", 27), title -> title.replace("%reputation%", String.valueOf(npc.getReputation(player.getUniqueId()))), true);

        this.player = player;

        chat = setChatItemInSlot("chat");
        greet = setChatItemInSlot("greet");
        story = setChatItemInSlot("story");
        joke = setChatItemInSlot("joke");
        insult = setChatItemInSlot("insult");
        flirt = setChatItemInSlot("flirt");
        proud = setChatItemInSlot("proud-of");

        follow = setGUIItemInSlot("follow-me");
        stay = setGUIItemInSlot("stay-here");

        // Info, trade & no trade.
        updateRequiredItems();

        inspect = setGUIItemInSlot("inspect-inventory");
        gift = setGUIItemInSlot("gift");
        procreate = setGUIItemInSlot("procreate");

        // Only show divorce papers if villager isn't a cleric partner.
        boolean isPartner = npc.isPartner(player.getUniqueId());
        if (!isPartner && npc.bukkit().getProfession() == Villager.Profession.CLERIC) {
            int slot = getItemSlot("divorce-papers");
            inventory.setItem(slot, papers = getGUIItem("divorce-papers"));
        } else {
            // May or not be shown depending on {@divorce -> @only-if-married}.
            divorce = setGUIItemInSlot("divorce");
        }

        combat = setGUIItemInSlot("combat");
        home = setGUIItemInSlot("set-home");

        player.openInventory(inventory);
    }

    public void updateRequiredItems() {
        // In this case, info item needs to be updated.
        info = setItemInSlot("information", name -> createInfoItem(getGUIItem(name)));

        // Iterate through all trades (if possible).
        if (outOfFullStock(npc.bukkit().getRecipes())) {
            noTrades = setGUIItemInSlot("no-trades");
            return;
        }

        trade = setItemInSlot("trade", tradeItem);
    }

    private ItemStack setChatItemInSlot(String itemName) {
        return setItemInSlot(itemName, this::createChatItem);
    }

    private ItemStack setGUIItemInSlot(String itemName) {
        return setItemInSlot(itemName, this::getGUIItem);
    }

    private @Nullable ItemStack setItemInSlot(String itemName, Function<String, ItemStack> callable) {
        if (!checkSettings(itemName)) return null;
        int slot = getItemSlot(itemName);
        if (slot == -1) return null;

        ItemStack item = callable.apply(itemName);
        inventory.setItem(slot, item);

        return item;
    }

    private boolean checkSettings(String itemName) {
        return !ITEM_SETTINGS.containsKey(itemName)
                || !getBool(itemName, ITEM_SETTINGS.get(itemName).getName())
                || ITEM_SETTINGS.get(itemName).getCondition().apply(npc, player, itemName);
    }

    private boolean getBool(String itemName, String setting) {
        return plugin.getConfig().getBoolean("gui." + name + ".items." + itemName + "." + setting);
    }

    private int getItemSlot(String itemName) {
        String string = plugin.getConfig().getString("gui." + name + ".items." + itemName + ".slot");
        if (string == null || string.isEmpty()) return -1;

        String[] data = string.split(",");
        if (data.length == 1) {
            return NumberUtils.isCreatable(string) ? Integer.parseInt(string) : -1;
        }

        if (data.length != 2) return -1;

        int x = parseInt(data[0]);
        int y = parseInt(data[1]);
        if (x == -1 || y == -1) return -1;

        return Math.max(0, Math.max(0, y - 1) * 9 + x - 1);
    }

    private int parseInt(String string) {
        try {
            return Integer.parseInt(StringUtils.deleteWhitespace(string));
        } catch (Exception exception) {
            return -1;
        }
    }

    private ItemStack createChatItem(String name) {
        return new ItemBuilder(getGUIItem(name))
                .setData(plugin.getChatInteractionTypeKey(), PersistentDataType.STRING, name.toUpperCase())
                .build();
    }

    private ItemStack createInfoItem(ItemStack item) {
        String sex = npc.isMale() ? Config.MALE.asString() : Config.FEMALE.asString();

        String none = Config.NONE.asString();
        String unknown = Config.UNKNOWN.asString();
        String villagerType = Config.VILLAGER.asString();

        VillagerTracker tracker = plugin.getTracker();

        String deadIcon = Config.DEAD.asString();
        if (!deadIcon.isEmpty()) deadIcon = " " + deadIcon + " ";
        else deadIcon = " ";

        // Get partners names.
        List<String> partners = new ArrayList<>();

        String previous = null;
        int count = 0;

        for (IVillagerNPC partner : npc.getPartners()) {
            // First, we add previous partners; shouldn't be null in these cases.

            String formatted = getPartnerFormatted(partner, !partner.getSex().isEmpty(), deadIcon, none, villagerType);

            if (previous == null) {
                previous = formatted;
                count = 1;
                continue;
            }

            if (previous.equals(formatted)) {
                count++;
                continue;
            }

            partners.add(previous + (count > 1 ? " x" + count : ""));
            previous = formatted;
            count = 1;
        }

        if (previous != null && count != 0) {
            partners.add(previous + (count > 1 ? " x" + count : ""));
        }

        // Then, we add the current partner (if any).
        String noResult = partners.isEmpty() ? Config.NO_PARTNERS.asString() : Config.NO_PARTNER_CURRENTLY.asString();
        String partnerName = getPartnerFormatted(npc.getPartner(), npc.isPartnerVillager(), deadIcon, noResult, villagerType);

        boolean currentlyMarried = partnerName.equals(noResult) || partners.isEmpty();
        partners.add((currentlyMarried ? "" : "&l") + partnerName);

        String partnersNames;
        List<String> temp;
        if (currentlyMarried) {
            temp = new ArrayList<>(partners);
            temp.add(partnerName);
        } else temp = partners;
        partnersNames = String.join(", ", temp);

        // Get father name.
        OfflinePlayer fatherPlayer = npc.getFather() != null
                && !npc.isFatherVillager() ? Bukkit.getOfflinePlayer(npc.getFather().getUniqueId()) : null;
        IVillagerNPC fatherInfo = npc.getFather() != null ? tracker.getOffline(npc.getFather().getUniqueId()) : null;
        String fatherName = null;
        if (fatherPlayer != null) {
            fatherName = fatherPlayer.getName() + " ";
        } else if (fatherInfo != null) {
            fatherName = fatherInfo.getVillagerName() + " ";
        } else if (npc.getFather() != null) {
            fatherName = npc.getFather().getVillagerName() + deadIcon;
        }

        // Add suffix to father name.
        if (fatherName == null) {
            fatherName = unknown;
        } else {
            fatherName += "(" + (npc.isFatherVillager() ? villagerType : Config.PLAYER.asString()) + ")";
        }

        // Get mother name.
        IVillagerNPC motherInfo = npc.getMother() != null ? tracker.getOffline(npc.getMother().getUniqueId()) : null;
        String motherName = motherInfo != null ? motherInfo.getVillagerName() : unknown;
        if (motherName.equalsIgnoreCase(unknown) && npc.getMother() != null) {
            motherName = npc.getMother().getVillagerName() + deadIcon + "(" + villagerType + ")";
        } else if (!motherName.equalsIgnoreCase(unknown)) {
            motherName += " (" + villagerType + ")";
        }

        // Get childrens names.
        List<String> childrens = new ArrayList<>();
        for (IVillagerNPC childrenUUID : npc.getChildrens()) {
            IVillagerNPC childrenInfo = tracker.getOffline(childrenUUID.getUniqueId());
            if (childrenInfo != null) {
                childrens.add(childrenInfo.getVillagerName());
            } else {
                childrens.add(childrenUUID.getVillagerName() + deadIcon);
            }
        }
        String childrensNames = childrens.isEmpty() ? Config.NO_CHILDRENS.asString() : String.join(", ", childrens);

        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : npc.bukkit().getActivePotionEffects()) {
            String effectName = plugin.getConfig().getString(
                    "variable-text.potion-effect-type." + effect.getType().getKey().getKey().replace("_", "-"),
                    PluginUtils.capitalizeFully(effect.getType().getKey().getKey().replace("_", " ")));

            // Type.
            String formatFully = "%type% %lvl% (%time%)".replace("%type%", effectName);

            // Level.
            formatFully = formatFully.replace("%lvl%", PluginUtils.toRoman(effect.getAmplifier() + 1));

            // Duration.
            int duration = effect.getDuration();
            int durationInSeconds = duration / 20;
            int maxSecondsInADayTicks = 86399 * 20;

            String time = duration == -1 || duration > maxSecondsInADayTicks ? Config.INFINITE.asString() : LocalTime.ofSecondOfDay(durationInSeconds).toString();
            if (time.startsWith("00:")) time = time.substring(3);
            if (duration % 60 == 0
                    || time.length() == 2
                    || (time.equals("01:00") && durationInSeconds >= 3600)) time = time + ":00";
            formatFully = formatFully.replace("%time%", time);

            effects.add(formatFully);
        }
        String effectsNames = effects.isEmpty() ? Config.NO_EFFECTS.asString() : String.join(", ", effects);

        String age = npc.bukkit().isAdult() ? Config.ADULT.asString() : Config.KID.asString();

        String type = npc.bukkit().getVillagerType().name().toLowerCase();
        type = plugin.getConfig().getString("variable-text.type." + type, type);

        String activity = npc.getActivityName(none);
        if (!activity.equalsIgnoreCase(none)) {
            activity = plugin.getConfig().getString("variable-text.activity." + activity, activity);
        }

        AttributeInstance maxHealthAttribute = npc.bukkit().getAttribute(Attribute.GENERIC_MAX_HEALTH);
        int level = npc.bukkit().getVillagerLevel();

        @SuppressWarnings("deprecation") ItemBuilder builder = new ItemBuilder(item)
                .replace("%villager-name%", npc.getVillagerName())
                .replace("%sex%", sex)
                .replace("%age-stage%", age)
                .replace("%health%", npc.bukkit().getHealth() + npc.bukkit().getAbsorptionAmount())
                .replace("%max-health%", maxHealthAttribute != null ? maxHealthAttribute.getValue() : npc.bukkit().getMaxHealth())
                .replace("%food-level%", npc.getFoodLevel())
                .replace("%max-food-level%", 20)
                .replace("%type%", type)
                .replace("%profession%", plugin.getProfessionFormatted(npc.bukkit().getProfession()))
                .replace("%level%", level)
                .replace("%experience%", npc.bukkit().getVillagerExperience())
                .replace("%max-experience%", getMaxXpPerLevel(level))
                .replace("%activity%", activity)
                .replace("%father%", fatherName)
                .replace("%mother%", motherName)
                .replace("%skin-id%", npc.getSkinTextureId())
                .replace("%id%", npc.bukkit().getEntityId())
                .replace("%current-partner%", currentlyMarried ? partnerName : Config.NO_PARTNERS.asString())
                .replace("%reputation%", npc.getReputation(player.getUniqueId()));

        return builder
                .applyMultiLineLore(partners, "%partner%", "%partners%", null /* Won't be empty. */, partnersNames)
                .applyMultiLineLore(childrens, "%children%", "%childrens%", Config.NO_CHILDRENS.asString(), childrensNames)
                .applyMultiLineLore(effects, "%effect%", "%effects%", Config.NO_EFFECTS.asString(), effectsNames)
                .build();
    }

    private String getPartnerFormatted(IVillagerNPC npc, boolean isVillager, String deadIcon, String none, String villagerType) {
        // Get partner name.
        OfflinePlayer partnerPlayer = npc != null
                && !isVillager ? Bukkit.getOfflinePlayer(npc.getUniqueId()) : null;
        IVillagerNPC partnerInfo = npc != null ? plugin.getTracker().getOffline(npc.getUniqueId()) : null;
        String partnerName = null;
        if (partnerPlayer != null) {
            partnerName = partnerPlayer.getName() + " ";
        } else if (partnerInfo != null) {
            partnerName = partnerInfo.getVillagerName() + " ";
        } else if (npc != null) {
            partnerName = npc.getVillagerName() + deadIcon;
        }

        // Add suffix to partner name.
        if (partnerName == null) {
            partnerName = none;
        } else {
            partnerName += "(" + (isVillager ? villagerType : Config.PLAYER.asString()) + ")";
        }

        return partnerName;
    }

    private int getMaxXpPerLevel(int level) {
        return NEXT_LEVEL_XP_THRESHOLDS[level > NEXT_LEVEL_XP_THRESHOLDS.length - 1 ? level - 1 : level];
    }

    private boolean outOfFullStock(@NotNull List<MerchantRecipe> offers) {
        for (MerchantRecipe offer : offers) {
            if (offer.getUses() < offer.getMaxUses()) return false;
        }
        return true;
    }

    @Override
    public boolean shouldStopInteracting() {
        return npc.isConversating() && super.shouldStopInteracting();
    }

    @Getter
    private enum Settings {
        ONLY_IF_ALLOWED("only-if-allowed", (npc, player, name) -> {
            UUID playerUUID = player.getUniqueId();
            String finalName = (name.equals("set-home") ? "home" : name).toUpperCase();
            return switch (Config.valueOf("WHO_CAN_MODIFY_VILLAGER_" + finalName).asString("FAMILY").toUpperCase()) {
                case "FAMILY" -> npc.isFamily(playerUUID, true);
                case "PARTNER" -> npc.isPartner(playerUUID);
                case "EVERYONE" -> true;
                default -> false;
            };
        }),
        ONLY_IF_MARRIED("only-if-married", (npc, player, name) -> npc.isPartner(player.getUniqueId()));

        private final String name;
        private final TriFunction<IVillagerNPC, Player, String, Boolean> condition;

        Settings(String name, TriFunction<IVillagerNPC, Player, String, Boolean> condition) {
            this.name = name;
            this.condition = condition;
        }
    }
}