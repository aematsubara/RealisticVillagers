package me.matsubara.realisticvillagers.manager.gift;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.ExtraTags;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
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

    public Set<Gift> getGiftsFromCategory(String path) {
        Set<Gift> tags = new HashSet<>();
        for (String materialOrTag : plugin.getConfig().getStringList(path)) {

            Predicate<IVillagerNPC> predicate;
            if (materialOrTag.startsWith("?")) {
                String[] data = materialOrTag.substring(1).split(":");
                if (data.length != 2) continue;

                try {
                    Villager.Profession profession = Villager.Profession.valueOf(data[0].toUpperCase());
                    predicate = npc -> npc.bukkit().getProfession() == profession;
                } catch (IllegalArgumentException exception) {
                    exception.printStackTrace();
                    continue;
                }
            } else {
                predicate = null;
            }

            int indexOf = predicate != null ? materialOrTag.indexOf(":") : -1;
            if (materialOrTag.startsWith("$") || (indexOf != -1 && materialOrTag.substring(indexOf + 1).startsWith("$"))) {
                String tagName = indexOf != -1 ? materialOrTag.substring(indexOf + 2) : materialOrTag.substring(1);

                addMaterialsFromRegistry(tags, predicate, tagName.toLowerCase(), Tag.REGISTRY_ITEMS, Tag.REGISTRY_BLOCKS);

                Set<Material> extra = ExtraTags.TAGS.get(tagName.toUpperCase());
                if (extra != null && !extra.isEmpty()) {
                    for (Material material : extra) {
                        addAndOverride(tags, createGift(material, predicate));
                    }
                }
                continue;
            }

            try {
                String materialName = materialOrTag.substring(indexOf != -1 ? indexOf + 1 : 0);
                Material material = Material.valueOf(materialName.toUpperCase());
                addAndOverride(tags, createGift(material, predicate));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return tags;
    }

    private Gift createGift(Material material, @Nullable Predicate<IVillagerNPC> predicate) {
        return predicate != null ? new Gift.GiftWithCondition(material, predicate) : new Gift(material);
    }

    private void addMaterialsFromRegistry(Set<Gift> gifts, @Nullable Predicate<IVillagerNPC> predicate, String tagName, String... registries) {
        for (String registry : registries) {
            addMaterials(gifts, predicate, registry, tagName);
        }
    }

    private void addMaterials(Set<Gift> gifts, @Nullable Predicate<IVillagerNPC> predicate, String registry, String tagName) {
        Tag<Material> tag = Bukkit.getTag(registry, NamespacedKey.minecraft(tagName.toLowerCase()), Material.class);
        if (tag == null) return;

        for (Material material : tag.getValues()) {
            addAndOverride(gifts, createGift(material, predicate));
        }
    }

    public GiftCategory getCategory(IVillagerNPC npc, ItemStack item) {
        GiftCategory selected = null;

        // The highest the category the highest the priority.
        for (GiftCategory category : data) {
            if (category.applies(npc, item)) selected = category;
        }

        return selected;
    }

    private void addAndOverride(Set<Gift> gifts, Gift newGift) {
        Material type = newGift.getType();
        if (getGift(gifts, type, true) != null) return;

        Gift withoutCondition = getGift(gifts, type, false);
        if (withoutCondition != null) {
            if (!(newGift instanceof Gift.GiftWithCondition)) return;
            gifts.remove(withoutCondition);
        }

        gifts.add(newGift);
    }

    private Gift getGift(Set<Gift> gifts, Material type, boolean condition) {
        for (Gift gift : gifts) {
            if (gift.is(type) && (!condition || gift instanceof Gift.GiftWithCondition)) return gift;
        }
        return null;
    }
}