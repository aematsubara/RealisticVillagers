package me.matsubara.realisticvillagers.manager.gift;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.ExtraTags;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public final class GiftManager {

    private final RealisticVillagers plugin;
    private final List<GiftCategory> data;

    public GiftManager(RealisticVillagers plugin) {
        this.plugin = plugin;
        this.data = new ArrayList<>();
        loadGiftCategories();
    }

    public void loadGiftCategories() {
        data.clear();

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("gift");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            int reputation = Math.max(2, plugin.getConfig().getInt("gift." + key + ".reputation"));
            Set<Gift> tags = getGiftsFromCategory("gift." + key + ".items");
            data.add(new GiftCategory(key, reputation, tags));
        }

        // Sorted by reputation ascendent.
        data.sort(Comparator.comparingInt(GiftCategory::reputation));
    }

    public @NotNull Set<Gift> getGiftsFromCategory(String path) {
        Set<Gift> tags = new HashSet<>();
        for (String materialOrTag : plugin.getConfig().getStringList(path)) {

            Predicate<IVillagerNPC> predicate;
            if (materialOrTag.startsWith("?")) {
                String[] data = materialOrTag.substring(1).split(":");
                if (data.length != 2) {
                    log(path, materialOrTag);
                    continue;
                }

                Villager.Profession profession = PluginUtils.getOrNull(Villager.Profession.class, data[0].toUpperCase());
                if (profession != null) {
                    predicate = npc -> npc.bukkit().getProfession() == profession;
                } else {
                    log(path, materialOrTag);
                    continue;
                }
            } else {
                predicate = null;
            }

            boolean inventoryLootOnly = materialOrTag.endsWith("*");

            int amount = 1;
            String amountString = StringUtils.substringBetween(materialOrTag, "(", ")");
            if (amountString != null) {
                materialOrTag = materialOrTag.replace("(" + amountString + ")", "");
                amount = amountString.equalsIgnoreCase("$RANDOM") ? -1 : PluginUtils.getRangedAmount(amountString);
            }

            int indexOf = predicate != null ? materialOrTag.indexOf(":") : -1;
            if (materialOrTag.startsWith("$") || (indexOf != -1 && materialOrTag.substring(indexOf + 1).startsWith("$"))) {
                String tagName = (indexOf != -1 ? materialOrTag.substring(indexOf + 2) : materialOrTag.substring(1)).replace("*", "");

                Set<Material> extra = ExtraTags.TAGS.get(tagName.toUpperCase());
                if (extra != null && !extra.isEmpty()) {
                    for (Material material : extra) {
                        addAndOverride(tags, createGift(amount, material, inventoryLootOnly, predicate));
                    }
                } else if (!addMaterialsFromRegistry(
                        tags,
                        predicate,
                        tagName.toLowerCase(),
                        inventoryLootOnly,
                        amount,
                        Tag.REGISTRY_ITEMS, Tag.REGISTRY_BLOCKS)) {
                    log(path, materialOrTag);
                }
                continue;
            }

            String materialName = materialOrTag.substring(indexOf != -1 ? indexOf + 1 : 0).replace("*", "");
            Material material = PluginUtils.getOrNull(Material.class, materialName.toUpperCase());
            if (material != null) {
                addAndOverride(tags, createGift(amount, material, inventoryLootOnly, predicate));
            } else {
                log(path, materialOrTag);
            }
        }
        return tags;
    }

    private void log(@NotNull String path, String materialOrTag) {
        boolean isWantedItems = path.equals("default-wanted-items");
        String[] data;
        String categoryName = isWantedItems ? path : (data = path.split("\\.")).length == 3 ? data[1] : path;
        plugin.getLogger().info("Invalid material for " + (isWantedItems ? "" : "gift category ") + "{" + categoryName + "}! " + materialOrTag);
    }

    @Contract("_, _, _, _ -> new")
    private @NotNull Gift createGift(int amount, Material material, boolean inventoryLootOnly, @Nullable Predicate<IVillagerNPC> predicate) {
        return predicate != null ?
                new Gift.GiftWithCondition(amount, material, inventoryLootOnly, predicate) :
                new Gift(amount, material, inventoryLootOnly);
    }

    @SuppressWarnings("SameParameterValue")
    private boolean addMaterialsFromRegistry(Set<Gift> gifts, @Nullable Predicate<IVillagerNPC> predicate, String tagName, boolean inventoryLootOnly, int amount, String @NotNull ... registries) {
        boolean found = false;
        for (String registry : registries) {
            Tag<Material> tag = Bukkit.getTag(registry, NamespacedKey.minecraft(tagName.toLowerCase()), Material.class);
            if (tag == null) continue;

            for (Material material : tag.getValues()) {
                addAndOverride(gifts, createGift(amount, material, inventoryLootOnly, predicate));
            }
            found = true;
        }
        return found;
    }

    public GiftCategory getCategory(IVillagerNPC npc, ItemStack item) {
        GiftCategory selected = null;

        // The highest the category the highest the priority.
        for (GiftCategory category : data) {
            if (category.applies(npc, item)) selected = category;
        }

        return selected;
    }

    private void addAndOverride(Set<Gift> gifts, @NotNull Gift newGift) {
        Material type = newGift.getType();
        if (getGift(gifts, type, true) != null) return;

        Gift withoutCondition = getGift(gifts, type, false);
        if (withoutCondition != null) {
            if (!(newGift instanceof Gift.GiftWithCondition)) return;
            gifts.remove(withoutCondition);
        }

        gifts.add(newGift);
    }

    private @Nullable Gift getGift(@NotNull Set<Gift> gifts, Material type, boolean condition) {
        for (Gift gift : gifts) {
            if (gift.is(type) && (!condition || gift instanceof Gift.GiftWithCondition)) return gift;
        }
        return null;
    }

    public @Nullable GiftCategory getRandomCategory() {
        return data.isEmpty() ? null : data.get(RandomUtils.nextInt(0, data.size()));
    }
}