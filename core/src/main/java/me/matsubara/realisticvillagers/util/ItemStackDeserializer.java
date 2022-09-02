package me.matsubara.realisticvillagers.util;

import org.apache.commons.lang3.math.NumberUtils;
import org.bukkit.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ItemStackDeserializer {

    public static ItemStack deserialize(String serialized) {
        String[] data = serialized.split(" ");

        ItemStack item = new ItemStack(Material.AIR);

        // Set material.
        for (String string : data) {
            if (item.getType() != Material.AIR) break;

            Material material;
            try {
                material = Material.valueOf(string.toUpperCase());
            } catch (IllegalArgumentException exception) {
                continue;
            }
            item.setType(material);
        }

        if (item.getType() == Material.AIR) {
            Bukkit.getLogger().info("Couldn't find a valid material for the item in \"" + serialized + "\"");
            return null;
        }

        Map<Enchantment, Integer> enchantments = new HashMap<>();

        for (String string : data) {
            String[] args = string.split(":", 2);

            if (args.length == 1) {
                if (NumberUtils.isNumber(args[0])) item.setAmount(Integer.parseInt(args[0]));
                continue;
            }

            if (args[0].equalsIgnoreCase("name")) {
                setName(item, PluginUtils.translate(args[1]));
                continue;
            }

            if (args[0].equalsIgnoreCase("lore")) {
                setLore(item, PluginUtils.translate(args[1]));
                continue;
            }

            if (args[0].equalsIgnoreCase("rgb")) {
                setArmorColor(item, args[1]);
                continue;
            }

            if (args[0].equalsIgnoreCase("owner")) {
                setOwner(item, args[1]);
                continue;
            }

            if (args[0].equalsIgnoreCase("flag")) {
                setFlags(item, args[1]);
                continue;
            }

            if (args[0].equalsIgnoreCase("url")) {
                item = new ItemBuilder(item).setHead(args[1], true).build();
                continue;
            }

            if (args[0].equalsIgnoreCase("pattern")) {
                setBannerPattern(item, args[1]);
                continue;
            }

            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(args[0].toLowerCase()));
            if (enchantment == null) continue;

            enchantments.put(enchantment, Integer.parseInt(args[1]));
        }

        item.addUnsafeEnchantments(enchantments);

        return item.getType() == Material.AIR ? null : item;
    }

    @SuppressWarnings("deprecation")
    private static void setOwner(ItemStack item, String owner) {
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return;

        OfflinePlayer player = Bukkit.getOfflinePlayer(owner);
        meta.setOwningPlayer(player);

        item.setItemMeta(meta);
    }

    private static void setName(ItemStack item, String name) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name.replace("_", " "));
        }

        item.setItemMeta(meta);
    }

    private static void setLore(ItemStack item, String lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(lore.replace("_", " ").split("\\|")));
        }
        item.setItemMeta(meta);
    }

    private static void setArmorColor(ItemStack item, String string) {
        if (!(item.getItemMeta() instanceof LeatherArmorMeta meta)) return;

        String[] colors = string.split("\\|");
        int red = Integer.parseInt(colors[0]);
        int green = Integer.parseInt(colors[1]);
        int blue = Integer.parseInt(colors[2]);
        meta.setColor(Color.fromRGB(red, green, blue));

        item.setItemMeta(meta);
    }

    private static void setFlags(ItemStack item, String string) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        for (String part : string.split("\\|")) {
            ItemFlag flag;
            try {
                flag = ItemFlag.valueOf(part.toUpperCase());
            } catch (IllegalArgumentException exception) {
                Bukkit.getLogger().info("Invalid flag for item: \"" + part + "\"");
                continue;
            }
            meta.addItemFlags(flag);
            item.setItemMeta(meta);
        }
    }

    public static void setBannerPattern(ItemStack item, String string) {
        if (!(item.getItemMeta() instanceof BannerMeta meta)) return;

        for (String part : string.split("\\|")) {
            String[] data = part.split(":");

            try {
                DyeColor color = DyeColor.values()[Integer.parseInt(data[0])];
                PatternType pattern = PatternType.getByIdentifier(data[1]);
                if (pattern != null) {
                    meta.addPattern(new Pattern(color, pattern));
                }
            } catch (IllegalArgumentException ignored) {

            }
        }

        item.setItemMeta(meta);
    }
}