package me.matsubara.realisticvillagers.manager;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.data.GiftCategory;
import me.matsubara.realisticvillagers.util.ExtraTags;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.*;

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

            Set<Material> tags = new HashSet<>();
            for (String materialOrTag : plugin.getConfig().getStringList("gift." + key + ".items")) {
                if (materialOrTag.startsWith("$")) {
                    String tagName = materialOrTag.substring(1);

                    addMaterialsFromRegistry(tags, tagName.toLowerCase(), Tag.REGISTRY_ITEMS, Tag.REGISTRY_BLOCKS);

                    Set<Material> extra = ExtraTags.TAGS.get(tagName.toUpperCase());
                    if (extra != null && !extra.isEmpty()) tags.addAll(extra);
                    continue;
                }

                try {
                    Material material = Material.valueOf(materialOrTag.toUpperCase());
                    tags.add(material);
                } catch (IllegalArgumentException exception) {
                    exception.printStackTrace();
                }
            }

            data.add(new GiftCategory(key, reputation, tags));
        }

        data.sort(Comparator.comparingInt(GiftCategory::reputation).reversed());
    }

    private void addMaterialsFromRegistry(Set<Material> materials, String tagName, String... registries) {
        for (String registry : registries) {
            addMaterials(materials, registry, tagName);
        }
    }

    private void addMaterials(Set<Material> materials, String registry, String tagName) {
        Tag<Material> tag = getTag(registry, tagName);
        if (tag != null) materials.addAll(tag.getValues());
    }

    private Tag<Material> getTag(String registry, String tagName) {
        return Bukkit.getTag(registry, NamespacedKey.minecraft(tagName.toLowerCase()), Material.class);
    }

    public GiftCategory getCategory(ItemStack item) {
        for (GiftCategory category : data) {
            if (category.applies(item)) return category;
        }
        return null;
    }
}