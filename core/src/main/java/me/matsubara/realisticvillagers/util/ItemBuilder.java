package me.matsubara.realisticvillagers.util;

import org.bukkit.*;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public final class ItemBuilder {

    private final ItemStack item;

    public ItemBuilder(ItemStack item) {
        this.item = item.clone();
    }

    public ItemBuilder(Material material) {
        this(new ItemStack(material));
    }

    public ItemBuilder setType(Material type) {
        item.setType(type);
        return this;
    }

    public ItemBuilder setHead(String texture, boolean isUrl) {
        if (item.getType() != Material.PLAYER_HEAD) {
            setType(Material.PLAYER_HEAD);
        }

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PluginUtils.applySkin(meta, texture, isUrl);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    public ItemBuilder setCustomModelData(int data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setCustomModelData(data);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setDamage(int damage) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable)) return this;
        ((Damageable) meta).setDamage(damage);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setOwningPlayer(UUID uuid) {
        return setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
    }

    public ItemBuilder setOwningPlayer(OfflinePlayer player) {
        if (!(item.getItemMeta() instanceof SkullMeta)) return this;

        try {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwningPlayer(player);
            item.setItemMeta(meta);
        } catch (ClassCastException ignore) {
        }
        return this;
    }

    public ItemBuilder setDisplayName(String displayName) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setDisplayName(PluginUtils.translate(displayName));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder clearLore() {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setLore(null);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setLore(String... lore) {
        return setLore(Arrays.asList(lore));
    }

    public ItemBuilder setLore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setLore(PluginUtils.translate(lore));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setLeatherArmorMetaColor(Color color) {
        if (!(item.getItemMeta() instanceof LeatherArmorMeta)) return this;

        try {
            LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
            meta.setColor(color);
            item.setItemMeta(meta);
        } catch (ClassCastException ignore) {
        }
        return this;
    }

    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        item.addUnsafeEnchantment(enchantment, level);
        return this;
    }

    public ItemBuilder removeEnchantment(Enchantment enchantment) {
        item.removeEnchantment(enchantment);
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.addItemFlags(flags);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder removeItemFlags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.removeItemFlags(flags);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setBasePotionData(PotionType type) {
        if (!(item.getItemMeta() instanceof PotionMeta)) return this;

        try {
            PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null) return this;

            meta.setBasePotionData(new PotionData(type));
            item.setItemMeta(meta);
        } catch (ClassCastException ignore) {

        }
        return this;
    }

    public ItemBuilder addPattern(int colorId, String patternCode) {
        return addPattern(DyeColor.values()[colorId], PatternType.getByIdentifier(patternCode));
    }

    public ItemBuilder addPattern(DyeColor color, PatternType patternType) {
        return addPattern(new Pattern(color, patternType));
    }

    public ItemBuilder addPattern(Pattern pattern) {
        if (!(item.getItemMeta() instanceof BannerMeta)) return this;

        try {
            BannerMeta meta = (BannerMeta) item.getItemMeta();
            if (meta == null) return this;

            meta.addPattern(pattern);
            item.setItemMeta(meta);
        } catch (ClassCastException ignore) {

        }
        return this;
    }

    public ItemBuilder setBannerColor(DyeColor color) {
        if (!(item.getItemMeta() instanceof BannerMeta)) return this;

        try {
            BannerMeta meta = (BannerMeta) item.getItemMeta();
            if (meta == null) return this;

            meta.addPattern(new Pattern(color, PatternType.BASE));
            item.setItemMeta(meta);
        } catch (ClassCastException ignore) {

        }
        return this;
    }

    public <T, Z> ItemBuilder setData(NamespacedKey key, PersistentDataType<T, Z> type, @NotNull Z value) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(key, type, value);

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder replace(String target, Object replace) {
        String text = PluginUtils.translate(replace.toString());
        return replaceName(target, text).replaceLore(target, text);
    }

    public ItemBuilder replaceName(String target, String replace) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        if (meta.hasDisplayName()) {
            meta.setDisplayName(meta.getDisplayName().replace(target, replace));
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder replaceLore(String target, String replace) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        if (meta.hasLore() && meta.getLore() != null) {
            meta.setLore(meta.getLore().stream().map(line -> line.replace(target, replace)).collect(Collectors.toList()));
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder glow() {
        item.addUnsafeEnchantment(Enchantment.ARROW_DAMAGE, 1);
        return addItemFlags(ItemFlag.HIDE_ENCHANTS);
    }

    public ItemStack build() {
        return item;
    }
}