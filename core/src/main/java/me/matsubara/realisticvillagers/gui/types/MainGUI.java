package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.tracker.VillagerTracker;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

@Getter
public final class MainGUI extends InteractGUI {

    private final ItemStack chat, greet, story,
            joke, flirt, proud,
            insult, follow, stay,
            inspect, gift, procreate,
            home, divorce, combat,
            papers, info, trade, noTrades;

    public MainGUI(RealisticVillagers plugin, IVillagerNPC npc, Player player) {
        super(plugin, npc, "main", plugin.getConfig().getInt("gui.main.size"), title -> title
                .replace("%reputation%", String.valueOf(npc.getReputation(player.getUniqueId()))));

        chat = setChatItemInSlot("chat");
        greet = setChatItemInSlot("greet");
        story = setChatItemInSlot("story");
        joke = setChatItemInSlot("joke");
        insult = setChatItemInSlot("insult");
        flirt = setChatItemInSlot("flirt");
        proud = setChatItemInSlot("proud-of");

        follow = setGUIItemInSlot("follow-me");
        stay = setGUIItemInSlot("stay-here");

        // This is the only item that occup the same slot.
        trade = getGUIItem("trade");
        noTrades = getGUIItem("no-trades");

        if (outOfFullStock(npc.bukkit().getRecipes())) {
            int slot = getItemSlot("no-trades");
            inventory.setItem(slot, noTrades);
        } else {
            int slot = getItemSlot("trade");
            inventory.setItem(slot, trade);
        }

        info = setItemInSlot("information", name -> createInfoItem(getGUIItem(name)));
        inspect = setGUIItemInSlot("inspect-inventory");
        gift = setGUIItemInSlot("gift");
        procreate = setGUIItemInSlot("procreate");
        divorce = setGUIItemInSlot("divorce");
        combat = setGUIItemInSlot("combat");
        home = setGUIItemInSlot("set-home");
        papers = setGUIItemInSlot("divorce-papers");

        if (size >= 27 && Config.GUI_MAIN_FRAME_ENABLED.asBool()) {
            ItemBuilder builder = plugin.getItem("gui.main.frame");
            if (builder != null) createFrame(builder.build());
        }

        player.openInventory(inventory);
    }

    private void createFrame(ItemStack item) {
        createFrame(item, 0, 9, i -> i + 1);
        createFrame(item, size - 9, size, i -> i + 1);
        createFrame(item, 0, size - 1, i -> i + 9);
        createFrame(item, 8, size, i -> i + 9);
    }

    private void createFrame(ItemStack item, int start, int limit, IntUnaryOperator operator) {
        for (int i = start; i < limit; i = operator.applyAsInt(i)) {
            inventory.setItem(i, item);
        }
    }

    private ItemStack setChatItemInSlot(String itemName) {
        return setItemInSlot(itemName, this::createChatItem);
    }

    private ItemStack setGUIItemInSlot(String itemName) {
        return setItemInSlot(itemName, this::getGUIItem);
    }

    private ItemStack setItemInSlot(String itemName, Function<String, ItemStack> callable) {
        int slot = getItemSlot(itemName);
        if (slot == -1) return null;

        ItemStack item = callable.apply(itemName);
        inventory.setItem(slot, item);

        return item;
    }

    private int getItemSlot(String itemName) {
        String string = plugin.getConfig().getString("gui." + name + ".items." + itemName + ".slot");
        if (string == null || string.isEmpty()) return -1;

        String[] data = string.split(",");
        if (data.length == 1) {
            return NumberUtils.isNumber(string) ? Integer.parseInt(string) : -1;
        }

        if (data.length != 2) return -1;

        int x = parseInt(data[0]);
        int y = parseInt(data[1]);

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

    @SuppressWarnings("deprecation")
    private ItemStack createInfoItem(ItemStack item) {
        String sex = npc.isMale() ? Config.MALE.asString() : Config.FEMALE.asString();

        String none = Config.NONE.asString();
        String unknown = Config.UNKNOWN.asString();
        String villagerType = Config.VILLAGER.asString();

        VillagerTracker tracker = plugin.getTracker();

        String deadIcon = Config.DEAD.asString();
        if (!deadIcon.isEmpty()) deadIcon = " " + deadIcon + " ";
        else deadIcon = " ";

        // Get partner name.
        OfflinePlayer partnerPlayer = npc.getPartner() != null
                && !npc.isPartnerVillager() ? Bukkit.getOfflinePlayer(npc.getPartner().getUniqueId()) : null;
        IVillagerNPC partnerInfo = npc.getPartner() != null ? tracker.getOffline(npc.getPartner().getUniqueId()) : null;
        String partnerName = null;
        if (partnerPlayer != null) {
            partnerName = partnerPlayer.getName() + " ";
        } else if (partnerInfo != null) {
            partnerName = partnerInfo.getVillagerName() + " ";
        } else if (npc.getPartner() != null) {
            partnerName = npc.getPartner().getVillagerName() + deadIcon;
        }

        // Add suffix to partner name.
        if (partnerName == null) {
            partnerName = none;
        } else {
            partnerName += "(" + (npc.isPartnerVillager() ? villagerType : Config.PLAYER.asString()) + ")";
        }


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

        // Get childrens name.
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

        String age = npc.bukkit().isAdult() ? Config.ADULT.asString() : Config.KID.asString();

        String type = npc.bukkit().getVillagerType().name().toLowerCase();
        type = plugin.getConfig().getString("variable-text.type." + type, type);

        String defaultProfession = npc.bukkit().getProfession().name().toLowerCase();
        String profession = plugin.getConfig().getString("variable-text.profession." + defaultProfession, WordUtils.capitalize(defaultProfession));

        String activity = npc.getActivityName(none);
        if (!activity.equalsIgnoreCase(none)) {
            activity = plugin.getConfig().getString("variable-text.activity." + activity, activity);
        }

        AttributeInstance maxHealthAttribute = npc.bukkit().getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double health = maxHealthAttribute != null ? fixedDecimal(maxHealthAttribute.getValue()) : npc.bukkit().getMaxHealth();

        ItemBuilder builder = new ItemBuilder(item)
                .replace("%villager-name%", npc.getVillagerName())
                .replace("%sex%", sex)
                .replace("%age-stage%", age)
                .replace("%health%", fixedDecimal(npc.bukkit().getHealth() + npc.bukkit().getAbsorptionAmount()))
                .replace("%max-health%", health)
                .replace("%food-level%", fixedDecimal(npc.getFoodLevel()))
                .replace("%max-food-level%", fixedDecimal(20))
                .replace("%type%", type)
                .replace("%profession%", profession)
                .replace("%level%", npc.bukkit().getVillagerLevel())
                .replace("%activity%", activity)
                .replace("%partner%", partnerName)
                .replace("%father%", fatherName)
                .replace("%mother%", motherName);

        List<String> lore = builder.getLore();
        if (lore.isEmpty()) return builder.build();


        int indexOfChildrens = lore.indexOf("%childrens%");
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains("%childrens%")) {
                indexOfChildrens = i;
                break;
            }
        }

        if (indexOfChildrens != -1) {
            String toReplace = lore.get(indexOfChildrens);

            // FIRST PART
            List<String> newLore = new ArrayList<>();

            if (indexOfChildrens > 0) {
                newLore.addAll(lore.subList(0, indexOfChildrens));
            }

            if (childrens.isEmpty()) {
                newLore.add(toReplace.replace("%childrens%", Config.NO_CHILDRENS.asString()));
            } else {
                for (String children : childrens) {
                    newLore.add(toReplace.replace("%childrens%", children));
                }
            }

            if (lore.size() > 1 && indexOfChildrens < lore.size() - 1) {
                newLore.addAll(lore.subList(indexOfChildrens + 1, lore.size()));
            }

            return builder.setLore(newLore).build();
        }

        return builder.replace("%childrens%", childrensNames).build();
    }

    private boolean outOfFullStock(List<MerchantRecipe> offers) {
        for (MerchantRecipe offer : offers) {
            if (offer.getUses() < offer.getMaxUses()) return false;
        }
        return true;
    }

    @Override
    public boolean shouldStopInteracting() {
        return npc.isConversating() && super.shouldStopInteracting();
    }

    private double fixedDecimal(double value) {
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}