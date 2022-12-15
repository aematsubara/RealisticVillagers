package me.matsubara.realisticvillagers.util;

import com.google.common.base.Preconditions;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.matsubara.realisticvillagers.files.Config;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils {

    private static final Pattern PATTERN = Pattern.compile("&(#[\\da-fA-F]{6})");
    private static final Set<String> IGNORE_METHODS = Set.of("spawnLeprechaun", "spawnShop");
    public static final Map<String, Color> COLORS_BY_NAME = new HashMap<>();
    private static final Color[] COLORS;

    private static final MethodHandle SET_PROFILE;
    private static final MethodHandle PROFILE;

    static {
        Field[] fields = Color.class.getDeclaredFields();

        for (Field field : fields) {
            if (!field.getType().equals(Color.class)) continue;

            try {
                COLORS_BY_NAME.put(field.getName(), (Color) field.get(null));
            } catch (IllegalAccessException ignored) {
            }
        }

        COLORS = COLORS_BY_NAME.values().toArray(new Color[0]);

        Class<?> craftMetaSkull = ReflectionUtils.getCraftClass("inventory.CraftMetaSkull");
        Preconditions.checkNotNull(craftMetaSkull);

        SET_PROFILE = Reflection.getMethod(craftMetaSkull, "setProfile", GameProfile.class);
        PROFILE = Reflection.getFieldSetter(craftMetaSkull, "profile");
    }

    public static Color getRandomColor() {
        return COLORS[ThreadLocalRandom.current().nextInt(COLORS.length)];
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

    public static Vector offsetVector(Vector vector, float yawDegrees, float pitchDegrees) {
        double yaw = Math.toRadians(-yawDegrees), pitch = Math.toRadians(-pitchDegrees);

        double cosYaw = Math.cos(yaw), cosPitch = Math.cos(pitch);
        double sinYaw = Math.sin(yaw), sinPitch = Math.sin(pitch);

        double initialX, initialY, initialZ, x, y, z;

        initialX = vector.getX();
        initialY = vector.getY();
        x = initialX * cosPitch - initialY * sinPitch;
        y = initialX * sinPitch + initialY * cosPitch;

        initialZ = vector.getZ();
        initialX = x;
        z = initialZ * cosYaw - initialX * sinYaw;
        x = initialZ * sinYaw + initialX * cosYaw;

        return new Vector(x, y, z);
    }

    public static String getTimeString(long millis) {
        long days = TimeUnit.MILLISECONDS.toDays(millis);

        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);

        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder builder = new StringBuilder();

        if (days > 0L) {
            builder.append(days).append(Config.ACRONYM_DAY.asString());
        }

        if (hours > 0L) {
            if (days > 0L) builder.append(", ");
            builder.append(hours).append(Config.ACRONYM_HOUR.asString());
        }

        if (minutes > 0L) {
            if (hours > 0L || days > 0L) builder.append(", ");
            builder.append(minutes).append(Config.ACRONYM_MINUTE.asString());
        }

        if (seconds > 0L) {
            if (minutes > 0L || hours > 0L || days > 0L) builder.append(", ");
            builder.append(seconds).append(Config.ACRONYM_SECOND.asString());
        }

        return builder.toString();
    }

    public static boolean spawnCustom() {
        for (StackTraceElement stacktrace : new Throwable().getStackTrace()) {
            String method = stacktrace.getMethodName();
            if (IGNORE_METHODS.contains(method)) return false;

            String clazz = stacktrace.getClassName();
            if (method.equals("spawn") && clazz.equals("NPCEntity")) return false;
        }
        return true;
    }

    public static String[] splitData(String string) {
        String[] split = StringUtils.split(StringUtils.deleteWhitespace(string), ',');
        if (split.length == 0) split = StringUtils.split(string, ' ');
        return split;
    }

    public static Color getColor(String string) {
        return string.equalsIgnoreCase("$RANDOM") ? getRandomColor() : COLORS_BY_NAME.get(string);
    }

    public static <T extends Enum<T>> T getRandomFromEnum(Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return constants[ThreadLocalRandom.current().nextInt(constants.length)];
    }

    public static <T extends Enum<T>> T getOrEitherRandomOrNull(Class<T> clazz, String name) {
        if (name.equalsIgnoreCase("$RANDOM")) return getRandomFromEnum(clazz);
        return getOrNull(clazz, name);
    }

    public static <T extends Enum<T>> T getOrNull(Class<T> clazz, String name) {
        try {
            return Enum.valueOf(clazz, name);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public static int getRangedAmount(String string) {
        String[] data = string.split("-");
        if (data.length == 1) {
            try {
                return Integer.parseInt(data[0]);
            } catch (IllegalArgumentException ignored) {
            }
        } else if (data.length == 2) {
            try {
                int min = Integer.parseInt(data[0]);
                int max = Integer.parseInt(data[1]);
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return 1;
    }
}