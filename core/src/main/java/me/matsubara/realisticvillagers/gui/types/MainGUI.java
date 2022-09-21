package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.tracker.VillagerInfo;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
public final class MainGUI extends InteractGUI {

    private final ItemStack chat, greet, story,
            joke, flirt, proud,
            insult, follow, stay,
            inspect, gift, procreate,
            home, divorce, combat,
            papers, info, trade, noTrades;

    public MainGUI(RealisticVillagers plugin, Player player, IVillagerNPC npc) {
        super("main", plugin, npc, 18, title -> title
                .replace("%reputation%", String.valueOf(npc.getReputation(player.getUniqueId()))));

        trade = getGUIItem("trade");
        noTrades = getGUIItem("no-trades");

        boolean outOfTrades = outOfFullStock(npc.bukkit().getRecipes());
        inventory.addItem(
                chat = createChatItem("chat"),
                greet = createChatItem("greet"),
                story = createChatItem("story"),
                follow = getGUIItem("follow-me"),
                stay = getGUIItem("stay-here"),
                outOfTrades ? noTrades : trade,
                info = createInfoItem(getGUIItem("information")),
                inspect = getGUIItem("inspect-inventory"),
                gift = getGUIItem("gift"),
                joke = createChatItem("joke"),
                insult = createChatItem("insult")
        );

        flirt = createChatItem("flirt");
        proud = createChatItem("proud-of");
        procreate = getGUIItem("procreate");
        divorce = getGUIItem("divorce");
        combat = getGUIItem("combat");
        home = getGUIItem("set-home");
        papers = getGUIItem("divorce-papers");


        if (npc.bukkit().isAdult() && !npc.isFamily(player.getUniqueId())) {
            inventory.addItem(flirt);
        } else {
            inventory.addItem(proud);
        }

        if (npc.isPartner(player.getUniqueId())) {
            inventory.addItem(procreate, divorce);
        }

        boolean isFamily = npc.isFamily(player.getUniqueId(), true);

        String modifyCombat = Config.WHO_CAN_MODIFY_VILLAGER_COMBAT.asString();
        if (modifyCombat.equalsIgnoreCase("everyone") || (modifyCombat.equalsIgnoreCase("family") && isFamily)) {
            inventory.addItem(combat);
        }

        if (isFamily) {
            inventory.addItem(home);
        }

        if (npc.is(Villager.Profession.CLERIC) && plugin.isMarried(player)) {
            inventory.addItem(papers);
        }

        player.openInventory(inventory);
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

        // Get partner name.
        OfflinePlayer partnerPlayer = npc.getPartner() != null
                && !npc.isPartnerVillager() ? Bukkit.getOfflinePlayer(npc.getPartner()) : null;
        VillagerInfo partnerInfo = plugin.getVillagerTracker().get(npc.getPartner());
        String partnerName = null;
        if (partnerPlayer != null) {
            partnerName = partnerPlayer.getName();
        } else if (partnerInfo != null) {
            partnerName = partnerInfo.getLastKnownName();
            if (partnerInfo.isDead()) partnerName += " †";
        }
        // Add suffix to partner name.
        if (partnerName == null) {
            partnerName = none;
        } else {
            partnerName += " (" + (npc.isPartnerVillager() ? villagerType : Config.PLAYER.asString()) + ")";
        }


        // Get father name.
        OfflinePlayer fatherPlayer = npc.getFather() != null
                && !npc.isFatherVillager() ? Bukkit.getOfflinePlayer(npc.getFather()) : null;
        VillagerInfo fatherInfo = plugin.getVillagerTracker().get(npc.getFather());
        String fatherName = null;
        if (fatherPlayer != null) {
            fatherName = fatherPlayer.getName();
        } else if (fatherInfo != null) {
            fatherName = fatherInfo.getLastKnownName();
            if (fatherInfo.isDead()) {
                fatherName += " †";
            }
        }

        // Add suffix to father name.
        if (fatherName == null) {
            fatherName = unknown;
        } else {
            fatherName += " (" + (npc.isFatherVillager() ? villagerType : Config.PLAYER.asString()) + ")";
        }

        // Get mother name.
        VillagerInfo motherInfo = plugin.getVillagerTracker().get(npc.getMother());
        String motherName = motherInfo != null ? motherInfo.getLastKnownName() : unknown;
        if (!motherName.equalsIgnoreCase(unknown)) {
            if (motherInfo != null && motherInfo.isDead()) motherName += " †";
            motherName += " (" + villagerType + ")";
        }

        // Get childrens name.
        List<String> childrens = new ArrayList<>();
        for (UUID childrenUUID : npc.getChildrens()) {
            VillagerInfo childrenInfo = plugin.getVillagerTracker().get(childrenUUID);
            if (childrenInfo != null)
                childrens.add(childrenInfo.getLastKnownName() + (childrenInfo.isDead() ? " †" : ""));
        }
        String childrensNames = childrens.isEmpty() ? Config.NO_CHILDRENS.asString() : String.join(", ", childrens);

        String age = npc.bukkit().isAdult() ? Config.ADULT.asString() : Config.KID.asString();

        String type = npc.bukkit().getVillagerType().name().toLowerCase();
        type = plugin.getConfig().getString("variable-text.type." + type, type);

        String profession = plugin.getConfig().getString("variable-text.profession." + npc.bukkit().getProfession().name().toLowerCase());

        String activity = npc.getActivityName(none);
        if (!activity.equalsIgnoreCase(none)) {
            activity = plugin.getConfig().getString("variable-text.activity." + activity, activity);
        }

        AttributeInstance maxHealthAttribute = npc.bukkit().getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double health = maxHealthAttribute != null ? fixedDecimal(maxHealthAttribute.getValue()) : npc.bukkit().getMaxHealth();

        return new ItemBuilder(item)
                .replace("%villager-name%", npc.bukkit().getName())
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
                .replace("%mother%", motherName)
                .replace("%childrens%", childrensNames)
                .build();
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