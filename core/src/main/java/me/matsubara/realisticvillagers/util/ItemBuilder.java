package me.matsubara.realisticvillagers.util;

import com.cryptomorin.xseries.reflection.XReflection;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandle;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.UnaryOperator;

@SuppressWarnings({"unused", "UnusedReturnValue"})
public final class ItemBuilder {

    private final ItemStack item;

    private static final MethodHandle SET_BASE_POTION_TYPE;

    static {
        SET_BASE_POTION_TYPE = XReflection.supports(20, 6) ?
                Reflection.getMethod(PotionMeta.class, "setBasePotionType", PotionType.class) :
                null;
    }

    public ItemBuilder(@NotNull ItemStack item) {
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
        return setHead(UUID.randomUUID(), texture, isUrl);
    }

    public ItemBuilder setHead(UUID uuid, String texture, boolean isUrl) {
        if (item.getType() != Material.PLAYER_HEAD) {
            setType(Material.PLAYER_HEAD);
        }

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        PluginUtils.applySkin(meta, uuid, texture, isUrl);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        item.setAmount(amount);
        return this;
    }

    public ItemBuilder setCustomModelData(int data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.setCustomModelData(data);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setDamage(int damage) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return this;
        damageable.setDamage(damage);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setOwningPlayer(UUID uuid) {
        return setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
    }

    public ItemBuilder setOwningPlayer(OfflinePlayer player) {
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return this;

        meta.setOwningPlayer(player);
        item.setItemMeta(meta);
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

    public ItemBuilder addLore(String... lore) {
        return addLore(Arrays.asList(lore));
    }

    public ItemBuilder addLore(List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        List<String> actual = meta.getLore();
        if (actual == null) return setLore(lore);

        actual.addAll(lore);
        return setLore(lore);
    }

    public List<String> getLore() {
        ItemMeta meta = item.getItemMeta();
        List<String> lore;
        return meta != null && (lore = meta.getLore()) != null ? lore : Collections.emptyList();
    }

    public ItemBuilder setLeatherArmorMetaColor(Color color) {
        if (!(item.getItemMeta() instanceof LeatherArmorMeta meta)) return this;

        meta.setColor(color);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addEnchantment(Enchantment enchantment, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.addEnchant(enchantment, level, true);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder removeEnchantment(Enchantment enchantment) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        meta.removeEnchant(enchantment);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        // This is necessary as of 1.20.6.
        Multimap<Attribute, AttributeModifier> modifiers = Objects.requireNonNullElseGet(meta.getAttributeModifiers(), HashMultimap::create);
        meta.setAttributeModifiers(modifiers);

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

    @SuppressWarnings("deprecation")
    public ItemBuilder setBasePotionData(PotionType type) {
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return this;

        if (SET_BASE_POTION_TYPE != null) {
            try {
                SET_BASE_POTION_TYPE.invoke(meta, type);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        } else {
            meta.setBasePotionData(new org.bukkit.potion.PotionData(type));
        }

        item.setItemMeta(meta);
        return this;
    }

    @SuppressWarnings("deprecation")
    public ItemBuilder addPattern(int colorId, String patternCode) {
        return addPattern(DyeColor.values()[colorId], PatternType.getByIdentifier(patternCode));
    }

    public ItemBuilder addPattern(DyeColor color, PatternType patternType) {
        return addPattern(new Pattern(color, patternType));
    }

    public ItemBuilder addPattern(Pattern pattern) {
        if (!(item.getItemMeta() instanceof BannerMeta meta)) return this;

        meta.addPattern(pattern);
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setBannerColor(DyeColor color) {
        if (!(item.getItemMeta() instanceof BannerMeta meta)) return this;

        meta.addPattern(new Pattern(color, PatternType.BASE));
        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder initializeFirework(int power, FireworkEffect... effects) {
        if (!(item.getItemMeta() instanceof FireworkMeta meta)) return this;

        meta.setPower(power);
        meta.addEffects(effects);
        item.setItemMeta(meta);
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

    public ItemBuilder replace(String target, @NotNull Object replace) {
        String text = PluginUtils.translate((replace instanceof Double number ? fixedDouble(number) : replace).toString());
        return replaceName(target, text).replaceLore(target, text);
    }

    private double fixedDouble(double value) {
        return new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public ItemBuilder replace(UnaryOperator<String> operator) {
        return replaceName(operator).replaceLore(operator);
    }

    public ItemBuilder replaceName(String target, String replace) {
        return replaceName(string -> string.replace(target, replace));
    }

    public ItemBuilder replaceName(UnaryOperator<String> operator) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        if (meta.hasDisplayName()) {
            meta.setDisplayName(operator.apply(meta.getDisplayName()));
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder replaceLore(String target, String replace) {
        return replaceLore(string -> string.replace(target, replace));
    }

    public ItemBuilder replaceLore(UnaryOperator<String> operator) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return this;

        List<String> lore;
        if (meta.hasLore() && (lore = meta.getLore()) != null) {
            lore.replaceAll(operator);
            meta.setLore(lore);
        }

        item.setItemMeta(meta);
        return this;
    }

    public ItemBuilder applyMultiLineLore(
            List<String> strings,
            String advancedPlaceholder,
            String simplePlaceholder,
            String noResultLine,
            String basicLine) {
        List<String> lore = getLore();
        if (lore.isEmpty()) return this;

        int indexOf = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).contains(advancedPlaceholder)) {
                indexOf = i;
                break;
            }
        }

        if (indexOf != -1) {
            String toReplace = lore.get(indexOf);

            List<String> newLore = new ArrayList<>();

            if (indexOf > 0) {
                newLore.addAll(lore.subList(0, indexOf));
            }

            if (strings.isEmpty()) {
                newLore.add(toReplace.replace(advancedPlaceholder, noResultLine));
            } else {
                for (String string : strings) {
                    newLore.add(toReplace.replace(advancedPlaceholder, string));
                }
            }

            if (lore.size() > 1 && indexOf < lore.size() - 1) {
                newLore.addAll(lore.subList(indexOf + 1, lore.size()));
            }

            return setLore(newLore);
        }

        return simplePlaceholder != null ? replace(simplePlaceholder, basicLine) : this;
    }

    public ItemStack build() {
        return item;
    }
}