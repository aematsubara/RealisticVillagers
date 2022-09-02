package me.matsubara.realisticvillagers.util;

import com.google.common.base.Preconditions;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.invoke.MethodHandle;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils {

    private final static Pattern PATTERN = Pattern.compile("&(#[\\da-fA-F]{6})");

    private final static MethodHandle SET_PROFILE;
    private final static MethodHandle PROFILE;

    static {
        Class<?> craftMetaSkull = ReflectionUtils.getCraftClass("inventory.CraftMetaSkull");
        Preconditions.checkNotNull(craftMetaSkull);

        SET_PROFILE = Reflection.getMethod(craftMetaSkull, "setProfile", GameProfile.class);
        PROFILE = Reflection.getFieldSetter(craftMetaSkull, "profile");
    }

    public static String translate(String message) {
        Preconditions.checkArgument(message != null, "Message can't be null.");

        Matcher matcher = PATTERN.matcher(ChatColor.translateAlternateColorCodes('&', message));
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of(matcher.group(1)).toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    public static List<String> translate(List<String> messages) {
        Preconditions.checkArgument(messages != null, "Messages can't be null.");

        messages.replaceAll(PluginUtils::translate);
        return messages;
    }

    public static ItemStack createHead(String texture) {
        return createHead(texture, false);
    }

    public static ItemStack createHead(String texture, boolean isUrl) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        applySkin(headMeta, texture, isUrl);
        head.setItemMeta(headMeta);
        return head;
    }

    public static void applySkin(SkullMeta meta, String texture, boolean isUrl) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), null);

        String textureValue = texture;
        if (isUrl) {
            textureValue = "http://textures.minecraft.net/texture/" + textureValue;
            byte[] encodedData = Base64.getEncoder().encode(String.format("{textures:{SKIN:{url:\"%s\"}}}", textureValue).getBytes());
            textureValue = new String(encodedData);
        }

        profile.getProperties().put("textures", new Property("textures", textureValue));

        try {
            // If the serialized profile field isn't set, ItemStack#isSimilar() and ItemStack#equals() throw an error.
            (SET_PROFILE == null ? PROFILE : SET_PROFILE).invoke(meta, profile);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }
}