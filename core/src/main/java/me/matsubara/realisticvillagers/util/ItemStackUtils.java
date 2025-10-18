package me.matsubara.realisticvillagers.util;

import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

public final class ItemStackUtils {

    private static final Map<Enchantment, Double> ARMOR_REDUCTION = new HashMap<>();

    private static final String[] ARMOR = {
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

    private static final String[] AXE = {
            "WOODEN_AXE",
            "STONE_AXE",
            "GOLDEN_AXE",
            "IRON_AXE",
            "DIAMOND_AXE",
            "NETHERITE_AXE"};

    private static final String[] SWORD = {
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

    public static boolean isSword(@NotNull ItemStack item) {
        return item.getType().name().contains("SWORD");
    }

    public static boolean isRangeWeapon(@NotNull ItemStack item) {
        return item.getType().name().endsWith("BOW");
    }

    public static boolean isAxe(@NotNull ItemStack item) {
        return item.getType().name().endsWith("_AXE");
    }

    public static boolean isWeapon(ItemStack item) {
        return isMeleeWeapon(item) || isRangeWeapon(item);
    }

    public static boolean isMeleeWeapon(@NotNull ItemStack item) {
        return item.getType() == Material.TRIDENT || isSword(item) || isAxe(item);
    }

    public static boolean isBetterArmorMaterial(ItemStack toCheck, ItemStack actual) {
        int toCheckIndex = getArmorIndex(toCheck);
        int actualIndex = getArmorIndex(actual);

        if (toCheckIndex == actualIndex) {
            // If the same type, we check the damage to each armor piece.
            return getItemDamage(toCheck) < getItemDamage(actual);
        } else {
            return toCheckIndex > actualIndex;
        }
    }

    private static int getItemDamage(@NotNull ItemStack item) {
        return item.getItemMeta() instanceof Damageable damageable ? damageable.getDamage() : 0;
    }

    private static int getArmorIndex(@NotNull ItemStack item) {
        return item.getType() == Material.TURTLE_HELMET ? 1 : ArrayUtils.indexOf(ARMOR, item.getType().name());
    }

    public static boolean isBetterAxeMaterial(@NotNull ItemStack toCheck, @NotNull ItemStack actual) {
        return ArrayUtils.indexOf(AXE, toCheck.getType().name()) > ArrayUtils.indexOf(AXE, actual.getType().name());
    }

    public static boolean isBetterSwordMaterial(@NotNull ItemStack toCheck, @NotNull ItemStack actual) {
        return ArrayUtils.indexOf(SWORD, toCheck.getType().name()) > ArrayUtils.indexOf(SWORD, actual.getType().name());
    }

    /**
     * Check if two different items are the same type (sword/axe/bow/helmet/etc.).
     * This method should only be used for armor, weapons and tools.
     */
    public static boolean isDifferentType(@NotNull ItemStack first, @NotNull ItemStack second) {
        String[] firstData = first.getType().name().split("_");
        String[] secondData = second.getType().name().split("_");

        if (firstData.length == 1 && secondData.length == 1) {
            return first.getType() != second.getType();
        }

        return !firstData[firstData.length - 1].equalsIgnoreCase(secondData[secondData.length - 1]);
    }

    @SuppressWarnings("ConstantConditions")
    public static boolean isBetterArmor(@NotNull ItemStack toCheck, ItemStack actual) {
        // If both items don't have enchantments, we check which one is better (material).
        if (!toCheck.getItemMeta().hasEnchants() && !actual.getItemMeta().hasEnchants()) {
            return isBetterArmorMaterial(toCheck, actual);
        }
        return getArmorBasePoints(toCheck) > getArmorBasePoints(actual);
    }

    private static double getArmorBasePoints(@NotNull ItemStack item) {
        // https://www.reddit.com/r/Minecraft/comments/157mxr/fyi_specifics_on_armor_enchantment_damage/

        int checkIndex;
        if (item.getType() == Material.TURTLE_HELMET) {
            checkIndex = 4;
        } else {
            checkIndex = ArrayUtils.indexOf(ARMOR, item.getType().name());
        }

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

    public static void setBetterWeaponInMaindHand(LivingEntity living, ItemStack item) {
        setBetterWeaponInMaindHand(living, item, true, false);
    }

    public static boolean setBetterWeaponInMaindHand(LivingEntity living, @NotNull ItemStack item, boolean addIfNotBetter, boolean canChangeType) {
        boolean isShield = item.getType() == Material.SHIELD;

        if (!isWeapon(item) && !isShield) return false;

        EntityEquipment equipment = living.getEquipment();
        if (equipment == null) return false;

        // Get item in the main hand.
        ItemStack content = equipment.getItemInMainHand();

        // If the main hand item is empty, we check if the picked item is a weapon, if so, set item in the main hand and return.
        if (content.getType().isAir()) {
            if (isWeapon(item)) {
                equipment.setItemInMainHand(item);
                return true;
            }
        }

        Inventory inventory = living instanceof InventoryHolder holder ? holder.getInventory() : null;

        // If the main hand item isn't empty or is empty but the picked item isn't a weapon,
        // we check if the picked item is a shield; if so, we try to put it the offhand, if already occupied, add to inventory and return.
        if (isShield) {
            // The shield always goes in offhand.
            if (equipment.getItemInOffHand().getType().isAir() && living.getHealth() >= 8.0d) {
                equipment.setItemInOffHand(item);
            } else {
                if (!addIfNotBetter) return false;
                if (inventory != null) inventory.addItem(item);
            }
            return true;
        }

        if (!isWeapon(content)) return false;
        if (isDifferentType(item, content) && !canChangeType) {
            if (!addIfNotBetter) return false;

            // Not of the same time, add to inventory.
            if (inventory != null) inventory.addItem(item);
            return true;
        } else if (isDifferentType(item, content) && canChangeType) {
            if (inventory != null) inventory.addItem(content);
            equipment.setItemInMainHand(item);
            return true;
        }

        // If both items have no enchantments, we check which one is better (based on its material).
        if (item.getEnchantments().isEmpty() && content.getEnchantments().isEmpty()) {
            return handle(living, item, content, (first, second) -> isAxe(item) ?
                            isBetterAxeMaterial(first, second) :
                            isSword(second) && isBetterSwordMaterial(first, second),
                    addIfNotBetter);
        }

        if (isSword(item)) {
            return handle(living, item, content, (first, second) -> getSwordBasePoints(first) > getSwordBasePoints(second), addIfNotBetter);
        } else if (isAxe(item)) {
            return handle(living, item, content, (first, second) -> getAxeBasePoints(first) > getAxeBasePoints(second), addIfNotBetter);
        } else if (isRangeWeapon(item)) {
            return handle(living, item, content, (first, second) -> getBowBasePoints(first) > getBowBasePoints(second), addIfNotBetter);
        } else if (item.getType() == Material.TRIDENT) {
            return handle(living, item, content, (first, second) -> getTridentBasePoints(first) > getTridentBasePoints(second), addIfNotBetter);
        }
        return false;
    }

    private static boolean handle(LivingEntity living,
                                  ItemStack item,
                                  ItemStack content,
                                  @NotNull BiPredicate<ItemStack, ItemStack> predicate,
                                  boolean addIfNotBetter) {
        if (predicate.test(item, content)) {
            if (living instanceof InventoryHolder holder) {
                holder.getInventory().addItem(content);
            }
            if (living.getEquipment() != null) living.getEquipment().setItemInMainHand(item);
        } else {
            if (!addIfNotBetter) return false;
            if (living instanceof InventoryHolder holder) {
                holder.getInventory().addItem(item);
            }
        }
        return true;
    }

    private static double getAxeBasePoints(@NotNull ItemStack item) {
        int checkIndex = ArrayUtils.indexOf(AXE, item.getType().name());

        double points = switch (checkIndex) {
            case 0, 1 -> 7;
            case 2, 3, 4 -> 9;
            default -> 10;
        };

        return points + getSharpnessPoints(item);
    }

    private static double getSwordBasePoints(@NotNull ItemStack item) {
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

    private static double getSharpnessPoints(@NotNull ItemStack item) {
        // https://minecraft.fandom.com/wiki/Sharpness - 0.5 * level + 0.5.
        int sharpnessLevel = item.getEnchantmentLevel(Enchantment.DAMAGE_ALL);
        return (sharpnessLevel > 0) ? 0.5d * sharpnessLevel + 0.5d : 0.0d;
    }

    private static double getBowBasePoints(@NotNull ItemStack item) {
        double points = 0.0d;

        // https://minecraft.fandom.com/wiki/Power - 25% * (level + 1).
        int powerLevel = item.getEnchantmentLevel(Enchantment.ARROW_DAMAGE);
        if (powerLevel > 0) {
            points += 0.25d * (powerLevel + 1);
        }

        return points;
    }

    private static double getTridentBasePoints(@NotNull ItemStack item) {
        double points = 0.0d;

        // https://minecraft.fandom.com/wiki/Impaling - level × 2.5.
        int impalingLevel = item.getEnchantmentLevel(Enchantment.IMPALING);
        if (impalingLevel > 0) {
            points += impalingLevel * 2.5d;
        }

        return points;
    }

    public static void setArmorItem(LivingEntity living, ItemStack item) {
        setArmorItem(living, item, true);
    }

    public static boolean setArmorItem(LivingEntity living, ItemStack item, boolean addIfNotBetter) {
        if (item == null || item.getType().isAir() || living.getEquipment() == null) return false;

        // If isn't armor, return.
        EquipmentSlot slot = getSlotByItem(item);
        if (slot == null) return false;

        Inventory inventory = living instanceof InventoryHolder holder ? holder.getInventory() : null;

        ItemStack current = living.getEquipment().getItem(slot);
        if (!current.getType().isAir() && !ItemStackUtils.isBetterArmor(item, current)) {
            if (!addIfNotBetter) return false;

            // Isn't better than current, add to inventory.
            if (inventory != null) inventory.addItem(item);
            return true;
        }

        // Set armor item.
        living.getEquipment().setItem(slot, item);

        // Add previous to inventory.
        if (inventory != null && !current.getType().isAir()) inventory.addItem(current);

        return true;
    }

    public static @Nullable EquipmentSlot getSlotByItem(@NotNull ItemStack item) {
        String name = item.getType().name();
        if (name.contains("HELMET") || name.equals("TURTLE_HELMET")) return EquipmentSlot.HEAD;
        if (name.contains("CHESTPLATE")) return EquipmentSlot.CHEST;
        if (name.contains("LEGGINGS")) return EquipmentSlot.LEGS;
        if (name.contains("BOOTS")) return EquipmentSlot.FEET;

        return null;
    }
}