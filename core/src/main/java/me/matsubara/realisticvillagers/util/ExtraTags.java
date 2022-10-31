package me.matsubara.realisticvillagers.util;

import com.google.common.collect.Sets;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ExtraTags {

    public static final Map<String, Set<Material>> TAGS = new HashMap<>();

    static {
        Set<Material> leatherArmor = Sets.newHashSet(
                Material.LEATHER_HELMET,
                Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS,
                Material.LEATHER_BOOTS);
        TAGS.put("LEATHER_ARMOR_PIECES", leatherArmor);

        HashSet<Material> ironArmor = Sets.newHashSet(
                Material.IRON_HELMET,
                Material.IRON_CHESTPLATE,
                Material.IRON_LEGGINGS,
                Material.IRON_BOOTS);
        TAGS.put("IRON_ARMOR_PIECES", ironArmor);

        HashSet<Material> chainMailArmor = Sets.newHashSet(
                Material.CHAINMAIL_HELMET,
                Material.CHAINMAIL_CHESTPLATE,
                Material.CHAINMAIL_LEGGINGS,
                Material.CHAINMAIL_BOOTS);
        TAGS.put("CHAINMAIL_ARMOR_PIECES", chainMailArmor);

        HashSet<Material> goldenArmor = Sets.newHashSet(
                Material.GOLDEN_HELMET,
                Material.GOLDEN_CHESTPLATE,
                Material.GOLDEN_LEGGINGS,
                Material.GOLDEN_BOOTS);
        TAGS.put("GOLDEN_ARMOR_PIECES", goldenArmor);

        HashSet<Material> diamondArmor = Sets.newHashSet(
                Material.DIAMOND_HELMET,
                Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS,
                Material.DIAMOND_BOOTS);
        TAGS.put("DIAMOND_ARMOR_PIECES", diamondArmor);

        HashSet<Material> netheriteArmor = Sets.newHashSet(
                Material.NETHERITE_HELMET,
                Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS,
                Material.NETHERITE_BOOTS);
        TAGS.put("NETHERITE_ARMOR_PIECES", netheriteArmor);

        Set<Material> armor = new HashSet<>();
        armor.add(Material.TURTLE_HELMET);
        armor.addAll(leatherArmor);
        armor.addAll(ironArmor);
        armor.addAll(chainMailArmor);
        armor.addAll(goldenArmor);
        armor.addAll(diamondArmor);
        armor.addAll(netheriteArmor);
        TAGS.put("ARMOR_PIECES", armor);

        Set<Material> woodenTools = Sets.newHashSet(
                Material.WOODEN_PICKAXE,
                Material.WOODEN_AXE,
                Material.WOODEN_HOE,
                Material.WOODEN_SHOVEL,
                Material.WOODEN_SWORD);
        TAGS.put("WOODEN_TOOLS", woodenTools);

        Set<Material> stoneTools = Sets.newHashSet(
                Material.STONE_PICKAXE,
                Material.STONE_AXE,
                Material.STONE_HOE,
                Material.STONE_SHOVEL,
                Material.STONE_SWORD);
        TAGS.put("STONE_TOOLS", stoneTools);

        Set<Material> ironTools = Sets.newHashSet(
                Material.IRON_PICKAXE,
                Material.IRON_AXE,
                Material.IRON_HOE,
                Material.IRON_SHOVEL,
                Material.IRON_SWORD);
        TAGS.put("IRON_TOOLS", ironTools);

        Set<Material> goldenTools = Sets.newHashSet(
                Material.GOLDEN_PICKAXE,
                Material.GOLDEN_AXE,
                Material.GOLDEN_HOE,
                Material.GOLDEN_SHOVEL,
                Material.GOLDEN_SWORD);
        TAGS.put("GOLDEN_TOOLS", goldenTools);

        Set<Material> diamondTools = Sets.newHashSet(
                Material.DIAMOND_PICKAXE,
                Material.DIAMOND_AXE,
                Material.DIAMOND_HOE,
                Material.DIAMOND_SHOVEL,
                Material.DIAMOND_SWORD);
        TAGS.put("DIAMOND_TOOLS", diamondTools);

        Set<Material> netheriteTools = Sets.newHashSet(
                Material.NETHERITE_PICKAXE,
                Material.NETHERITE_AXE,
                Material.NETHERITE_HOE,
                Material.NETHERITE_SHOVEL,
                Material.NETHERITE_SWORD);
        TAGS.put("NETHERITE_TOOLS", netheriteTools);

        Set<Material> tools = new HashSet<>();
        tools.addAll(stoneTools);
        tools.addAll(woodenTools);
        tools.addAll(ironTools);
        tools.addAll(goldenTools);
        tools.addAll(diamondTools);
        tools.addAll(netheriteTools);
        TAGS.put("TOOLS", tools);

        Set<Material> edibles = new HashSet<>();
        Set<Material> records = new HashSet<>();
        Set<Material> potions = new HashSet<>();

        for (Material material : Material.values()) {
            if (material.isEdible()) edibles.add(material);
            if (material.isRecord()) records.add(material);

            String name = material.name();
            if (name.contains("POTION")) potions.add(material);
        }

        TAGS.put("EDIBLES", edibles);
        TAGS.put("RECORDS", records);
        TAGS.put("POTIONS", potions);
    }
}