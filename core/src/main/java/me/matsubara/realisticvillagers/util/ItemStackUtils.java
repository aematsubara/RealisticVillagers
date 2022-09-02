package me.matsubara.realisticvillagers.util;

import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

public final class ItemStackUtils {

    private final static Map<Enchantment, Double> ARMOR_REDUCTION = new HashMap<>();

    private final static String[] ARMOR = {
            "LEATHER_HELMET",
            "LEATHER_CHESTPLATE",
            "LEATHER_LEGGINGS",
            "LEATHER_BOOTS",
            "GOLDEN_HELMET",
            "GOLDEN_CHESTPLATE",
            "GOLDEN_LEGGINGS",
            "GOLDEN_BOOTS",
            "CHAINMAIL_HELMET",
            "CHAINMAIL_CHESTPLATE",
            "CHAINMAIL_LEGGINGS",
            "CHAINMAIL_BOOTS",
            "IRON_HELMET",
            "IRON_CHESTPLATE",
            "IRON_LEGGINGS",
            "IRON_BOOTS",
            "DIAMOND_HELMET",
            "DIAMOND_CHESTPLATE",
            "DIAMOND_LEGGINGS",
            "DIAMOND_BOOTS",
            "NETHERITE_HELMET",
            "NETHERITE_CHESTPLATE",
            "NETHERITE_LEGGINGS",
            "NETHERITE_BOOTS"};

    private final static String[] AXE = {
            "WOODEN_AXE",
            "STONE_AXE",
            "GOLDEN_AXE",
            "IRON_AXE",
            "DIAMOND_AXE",
            "NETHERITE_AXE"};

    private final static String[] SWORD = {
            "WOODEN_SWORD",
            "GOLDEN_SWORD",
            "STONE_SWORD",
            "IRON_SWORD",
            "DIAMOND_SWORD",
            "NETHERITE_SWORD"};

    static {
        ARMOR_REDUCTION.put(Enchantment.PROTECTION_ENVIRONMENTAL, 0.75d);
        ARMOR_REDUCTION.put(Enchantment.PROTECTION_FIRE, 1.25d);
        ARMOR_REDUCTION.put(Enchantment.PROTECTION_EXPLOSIONS, 1.5d);
        ARMOR_REDUCTION.put(Enchantment.PROTECTION_PROJECTILE, 1.5d);
        ARMOR_REDUCTION.put(Enchantment.PROTECTION_FALL, 2.5d);
    }

    public static boolean isSword(ItemStack item) {
        return item.getType().name().contains("SWORD");
    }

    public static boolean isBow(ItemStack item) {
        return item.getType().name().contains("BOW");
    }

    public static boolean isAxe(ItemStack item) {
        return item.getType().name().contains("AXE");
    }

    public static boolean isWeapon(ItemStack item) {
        return isSword(item) || isBow(item) || isAxe(item);
    }

    public static boolean isBetterArmorMaterial(ItemStack toCheck, ItemStack actual) {
        return ArrayUtils.indexOf(ARMOR, toCheck.getType().name()) > ArrayUtils.indexOf(ARMOR, actual.getType().name());
    }

    public static boolean isBetterAxeMaterial(ItemStack toCheck, ItemStack actual) {
        return ArrayUtils.indexOf(AXE, toCheck.getType().name()) > ArrayUtils.indexOf(AXE, actual.getType().name());
    }

    public static boolean isBetterSwordMaterial(ItemStack toCheck, ItemStack actual) {
        return ArrayUtils.indexOf(SWORD, toCheck.getType().name()) > ArrayUtils.indexOf(SWORD, actual.getType().name());
    }

    /**
     * Check if two different items are the same type (sword/axe/bow/helmet/etc.).
     * This method should only be used for armor, weapons and tools.
     */
    public static boolean isSameType(ItemStack first, ItemStack second) {
        String[] firstData = first.getType().name().split("_");
        String[] secondData = second.getType().name().split("_");

        if (firstData.length == 1 && secondData.length == 1) {
            return first.getType() == second.getType();
        }

        return firstData[firstData.length - 1].equalsIgnoreCase(secondData[secondData.length - 1]);
    }

    @SuppressWarnings("ConstantConditions")
    public static boolean isBetterArmor(ItemStack toCheck, ItemStack actual) {
        // If both items doesn't have enchantments, we check which one is better (material).
        if (!toCheck.getItemMeta().hasEnchants() && !actual.getItemMeta().hasEnchants()) {
            return isBetterArmorMaterial(toCheck, actual);
        }
        return getArmorBasePoints(toCheck) > getArmorBasePoints(actual);
    }

    private static double getArmorBasePoints(ItemStack item) {
        // https://www.reddit.com/r/Minecraft/comments/157mxr/fyi_specifics_on_armor_enchantment_damage/

        int checkIndex = ArrayUtils.indexOf(ARMOR, item.getType().name());

        // Initialize points based on the type of the armor, leather = 0, gold = gold = 1, chainmail = 2, etc...
        double points = (checkIndex < 4) ? 0 : (double) (checkIndex / 4);

        for (Enchantment enchantment : item.getEnchantments().keySet()) {
            if (!ARMOR_REDUCTION.containsKey(enchantment)) continue;
            points += getDamageReduction(item.getEnchantmentLevel(enchantment), ARMOR_REDUCTION.get(enchantment));
        }

        return points;
    }

    private static double getDamageReduction(int level, double typeModifier) {
        return Math.floor((6 + level * level) * typeModifier / 3);
    }

    public static void setBetterWeaponInMaindHand(Villager villager, ItemStack item) {
        boolean isShield = item.getType() == Material.SHIELD;

        if (!isWeapon(item) && !isShield) return;
        if (villager.getEquipment() == null) return;

        // Get item in main hand.
        ItemStack content = villager.getEquipment().getItemInMainHand();

        // If main hand item is empty, we check if the picked item is a weapon, if so, set item in main hand and return.
        if (content.getType().isAir()) {
            if (isWeapon(item)) {
                villager.getEquipment().setItemInMainHand(item);
                return;
            }
        }

        // If main hand item isn't empty or is empty but picked item isn't a weapon,
        // we check if the picked item is a shield, if so, we try to put it the offhand, if already occupied, add to inventory and return.
        if (isShield) {
            // The shield always goes in offhand.
            if (villager.getEquipment().getItemInOffHand().getType().isAir()) {
                villager.getEquipment().setItemInOffHand(item);
            } else {
                villager.getInventory().addItem(item);
            }
            return;
        }

        if (!isWeapon(content)) return;
        if (!isSameType(item, content)) {
            // Not of the same time, add to inventory.
            villager.getInventory().addItem(item);
            return;
        }

        // If both items have no enchantments, we check which one is better (based on its material).
        if (item.getEnchantments().isEmpty() && content.getEnchantments().isEmpty()) {
            handle(villager, item, content, (first, second) -> isAxe(item) ?
                    isBetterAxeMaterial(first, second) :
                    isSword(second) && isBetterSwordMaterial(first, second));
            return;
        }

        if (isSword(item)) {
            handle(villager, item, content, (first, second) -> getSwordBasePoints(first) > getSwordBasePoints(second));
        } else if (isAxe(item)) {
            handle(villager, item, content, (first, second) -> getAxeBasePoints(first) > getAxeBasePoints(second));
        } else if (isBow(item)) {
            handle(villager, item, content, (first, second) -> getBowBasePoints(first) > getBowBasePoints(second));
        }
    }

    private static void handle(Villager villager, ItemStack item, ItemStack content, BiPredicate<ItemStack, ItemStack> predicate) {
        if (predicate.test(item, content)) {
            villager.getInventory().addItem(content);
            if (villager.getEquipment() != null) villager.getEquipment().setItemInMainHand(item);
        } else {
            villager.getInventory().addItem(item);
        }
    }

    private static double getAxeBasePoints(ItemStack item) {
        int checkIndex = ArrayUtils.indexOf(AXE, item.getType().name());

        double points = switch (checkIndex) {
            case 0, 1 -> 7;
            case 2, 3, 4 -> 9;
            default -> 10;
        };

        return points + getSharpnessPoints(item);
    }

    private static double getSwordBasePoints(ItemStack item) {
        int checkIndex = ArrayUtils.indexOf(SWORD, item.getType().name());

        double points = checkIndex + 4.0d;
        if (checkIndex > 0) points -= 1.0d;

        points += getSharpnessPoints(item);

        // https://minecraft.fandom.com/wiki/Fire_Aspect - (level × 4) – 1.
        int fireAspectLevel = item.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        if (fireAspectLevel > 0) {
            points += (((double) fireAspectLevel * 4) - 1) * 10 / 100;
        }

        return points;
    }

    private static double getSharpnessPoints(ItemStack item) {
        // https://minecraft.fandom.com/wiki/Sharpness - 0.5 * level + 0.5.
        int sharpnessLevel = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        return (sharpnessLevel > 0) ? 0.5d * sharpnessLevel + 0.5d : 0.0d;
    }

    private static double getBowBasePoints(ItemStack item) {
        double points = 0.0d;

        // https://minecraft.fandom.com/wiki/Power - 25% * (level + 1).
        int powerLevel = item.getEnchantmentLevel(Enchantment.ARROW_DAMAGE);
        if (powerLevel > 0) {
            points += 0.25d * (powerLevel + 1);
        }

        return points;
    }

    public static void setArmorItem(Villager villager, ItemStack item) {
        if (item == null || item.getType().isAir() || villager.getEquipment() == null) return;

        // If isn't armor, return.
        EquipmentSlot slot = getSlotByItem(item);
        if (slot == null) return;

        ItemStack current = villager.getEquipment().getItem(slot);
        if (!current.getType().isAir() && !ItemStackUtils.isBetterArmor(item, current)) {
            // Isn't better than current, add to inventory.
            villager.getInventory().addItem(item);
            return;
        }

        // Set armor item.
        villager.getEquipment().setItem(slot, item);

        // Add previous to inventory.
        villager.getInventory().addItem(current);
    }

    public static EquipmentSlot getSlotByItem(ItemStack item) {
        String name = item.getType().name();

        if (name.contains("HELMET")) return EquipmentSlot.HEAD;
        if (name.contains("CHESTPLATE")) return EquipmentSlot.CHEST;
        if (name.contains("LEGGINGS")) return EquipmentSlot.LEGS;
        if (name.contains("BOOTS")) return EquipmentSlot.FEET;

        return null;
    }
}