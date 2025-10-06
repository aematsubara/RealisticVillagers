package me.matsubara.realisticvillagers.util;

import com.cryptomorin.xseries.reflection.XReflection;
import com.cryptomorin.xseries.reflection.minecraft.MinecraftPackage;
import com.google.common.base.Preconditions;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import net.md_5.bungee.api.ChatColor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.bukkit.*;
import org.bukkit.Color;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.MarkedYAMLException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PluginUtils {

    private static final Pattern PATTERN = Pattern.compile("&(#[\\da-fA-F]{6})");
    private static final NavigableMap<Integer, String> ROMAN_NUMERALS = new TreeMap<>();

    private static final Map<String, Color> COLORS_BY_NAME = new HashMap<>();
    private static final BlockFace[] CLOCKWISE = {
            BlockFace.NORTH,
            BlockFace.NORTH_NORTH_EAST,
            BlockFace.NORTH_EAST,
            BlockFace.EAST_NORTH_EAST,
            BlockFace.EAST,
            BlockFace.EAST_SOUTH_EAST,
            BlockFace.SOUTH_EAST,
            BlockFace.SOUTH_SOUTH_EAST,
            BlockFace.SOUTH,
            BlockFace.SOUTH_SOUTH_WEST,
            BlockFace.SOUTH_WEST,
            BlockFace.WEST_SOUTH_WEST,
            BlockFace.WEST,
            BlockFace.WEST_NORTH_WEST,
            BlockFace.NORTH_WEST,
            BlockFace.NORTH_NORTH_WEST};
    private static final Color[] COLORS;

    private static final Class<?> CRAFT_ENTITY = XReflection.ofMinecraft()
            .inPackage(MinecraftPackage.CB, "entity")
            .named("CraftEntity")
            .unreflect();

    private static final Class<?> CRAFT_META_SKULL = XReflection.ofMinecraft()
            .inPackage(MinecraftPackage.CB, "inventory")
            .named("CraftMetaSkull")
            .unreflect();

    private static final MethodHandle SET_PROFILE = Reflection.getMethod(CRAFT_META_SKULL, "setProfile", false, GameProfile.class);
    private static final MethodHandle SET_OWNER_PROFILE = SET_PROFILE != null ? null : Reflection.getMethod(SkullMeta.class, "setOwnerProfile", false, PlayerProfile.class);

    private static final MethodHandle GET_HANDLE = Reflection.getMethod(Objects.requireNonNull(CRAFT_ENTITY), "getHandle");
    private static final MethodHandle ABS_MOVE_TO = Reflection.getMethod(
            XReflection.ofMinecraft()
                    .inPackage(MinecraftPackage.NMS, "world.entity")
                    .named("Entity")
                    .unreflect(),
            "a",
            MethodType.methodType(void.class, double.class, double.class, double.class, float.class, float.class),
            false,
            false,
            "setLocation",
            "absMoveTo");

    public static final Map<Class<?>, Object> DEFAULT_VALUES = Map.of(
            String.class, "",
            Integer.class, 0,
            Long.class, 0L,
            Float.class, 0.0f,
            Double.class, 0.0d,
            Boolean.class, false);

    static {
        ROMAN_NUMERALS.put(1000, "M");
        ROMAN_NUMERALS.put(900, "CM");
        ROMAN_NUMERALS.put(500, "D");
        ROMAN_NUMERALS.put(400, "CD");
        ROMAN_NUMERALS.put(100, "C");
        ROMAN_NUMERALS.put(90, "XC");
        ROMAN_NUMERALS.put(50, "L");
        ROMAN_NUMERALS.put(40, "XL");
        ROMAN_NUMERALS.put(10, "X");
        ROMAN_NUMERALS.put(9, "IX");
        ROMAN_NUMERALS.put(5, "V");
        ROMAN_NUMERALS.put(4, "IV");
        ROMAN_NUMERALS.put(1, "I");

        for (Field field : Color.class.getDeclaredFields()) {
            if (!field.getType().equals(Color.class)) continue;

            try {
                COLORS_BY_NAME.put(field.getName(), (Color) field.get(null));
            } catch (IllegalAccessException ignored) {
            }
        }

        COLORS = COLORS_BY_NAME.values().toArray(new Color[0]);
    }

    public static @NotNull Vector getDirection(@NotNull BlockFace face) {
        int modX = face.getModX(), modY = face.getModY(), modZ = face.getModZ();
        Vector direction = new Vector(modX, modY, modZ);
        if (modX != 0 || modY != 0 || modZ != 0) direction.normalize();
        return direction;
    }

    public static @Nullable BlockFace yawToFace(float yaw, int type) {
        if (type == 0x3) {
            int index = Math.round(yaw / 90f) & 0x3;
            return CLOCKWISE[index * 4];
        }

        int arrayLength = type == 0x7 ? 8 : type == 0x15 ? 16 : -1;
        if (arrayLength == -1) return null;

        int index = Math.round(yaw / (360f / arrayLength)) & (arrayLength - 1);
        return CLOCKWISE[index];
    }

    public static float faceToYaw(BlockFace face) {
        int index = ArrayUtils.indexOf(CLOCKWISE, face);
        if (index == -1) return 0.0f;

        float yaw = index * 22.5f;
        return yaw % 360.0f;
    }

    public static Color getRandomColor() {
        return COLORS[RandomUtils.nextInt(0, COLORS.length)];
    }

    public static @NotNull String translate(String message) {
        Preconditions.checkArgument(message != null, "Message can't be null.");

        Matcher matcher = PATTERN.matcher(ChatColor.translateAlternateColorCodes('&', message));
        StringBuilder buffer = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of(matcher.group(1)).toString());
        }

        return matcher.appendTail(buffer).toString();
    }

    @Contract("_ -> param1")
    public static @NotNull List<String> translate(@NotNull List<String> messages) {
        messages.replaceAll(PluginUtils::translate);
        return messages;
    }

    public static @NotNull ItemStack createHead(String texture, boolean isUrl) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        applySkin(headMeta, texture, isUrl);
        head.setItemMeta(headMeta);
        return head;
    }

    public static void applySkin(SkullMeta meta, String texture, boolean isUrl) {
        applySkin(meta, UUID.randomUUID(), texture, isUrl);
    }

    public static void applySkin(SkullMeta meta, UUID uuid, String texture, boolean isUrl) {
        try {
            // If the serialized profile field isn't set, ItemStack#isSimilar() and ItemStack#equals() throw an error.
            if (SET_PROFILE != null) {
                GameProfile profile = new GameProfile(uuid, "");

                String value = isUrl ? new String(Base64.getEncoder().encode(String
                        .format("{textures:{SKIN:{url:\"%s\"}}}", "http://textures.minecraft.net/texture/" + texture)
                        .getBytes())) : texture;

                profile.getProperties().put("textures", new Property("textures", value));
                SET_PROFILE.invoke(meta, profile);
            } else if (SET_OWNER_PROFILE != null) {
                PlayerProfile profile = Bukkit.createPlayerProfile(uuid, "");

                PlayerTextures textures = profile.getTextures();
                String url = isUrl ? "http://textures.minecraft.net/texture/" + texture : getURLFromTexture(texture);
                textures.setSkin(new URL(url));

                profile.setTextures(textures);
                SET_OWNER_PROFILE.invoke(meta, profile);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    public static String getURLFromTexture(String texture) {
        // Decode Base64.
        String decoded = new String(Base64.getDecoder().decode(texture));

        // Get url from JSON.
        return JsonParser.parseString(decoded).getAsJsonObject()
                .getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url")
                .getAsString();
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Vector offsetVector(@NotNull Vector vector, float yawDegrees, float pitchDegrees) {
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

    public static @NotNull String formatMillis(long millis) {
        Duration duration = Duration.ofMillis(millis);

        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        StringBuilder builder = new StringBuilder();

        if (days > 0) builder
                .append(days)
                .append(Config.ACRONYM_DAY.asString())
                .append(" ");

        if (hours > 0) builder
                .append(hours)
                .append(Config.ACRONYM_HOUR.asString())
                .append(" ");

        if (minutes > 0) builder
                .append(minutes)
                .append(Config.ACRONYM_MINUTE.asString())
                .append(" ");

        if (seconds > 0 || builder.isEmpty()) builder
                .append(seconds)
                .append(Config.ACRONYM_SECOND.asString());

        return builder.toString().trim();
    }

    public static String[] splitData(String string) {
        String[] split = StringUtils.split(StringUtils.deleteWhitespace(string), ',');
        if (split.length == 0) split = StringUtils.split(string, ' ');
        return split;
    }

    public static Color getColor(@NotNull String string) {
        if (string.equalsIgnoreCase("$RANDOM")) return getRandomColor();

        if (string.matches(PATTERN.pattern())) {
            java.awt.Color temp = ChatColor.of(string.substring(1)).getColor();
            return Color.fromRGB(temp.getRed(), temp.getGreen(), temp.getBlue());
        }

        return COLORS_BY_NAME.get(string);
    }

    public static <T extends Enum<T>> T getRandomFromEnum(@NotNull Class<T> clazz) {
        T[] constants = clazz.getEnumConstants();
        return constants[RandomUtils.nextInt(0, constants.length)];
    }

    public static <T extends Enum<T>> T getOrEitherRandomOrNull(Class<T> clazz, @NotNull String name) {
        if (name.equalsIgnoreCase("$RANDOM")) return getRandomFromEnum(clazz);
        return getOrNull(clazz, name);
    }

    public static @Nullable PotionType getValidPotionType(@NotNull String name) {
        if (name.equalsIgnoreCase("$RANDOM")) {
            PotionType type;
            do {
                type = getRandomFromEnum(PotionType.class);
            } while (isInvalidPotionType(type));
            return type;
        }

        PotionType type = getOrNull(PotionType.class, name);
        return isInvalidPotionType(type) ? null : type;
    }

    @SuppressWarnings("deprecation")
    private static boolean isInvalidPotionType(PotionType type) {
        return type == null
                || type.name().startsWith("LONG_")
                || type.name().startsWith("STRONG_")
                || type.getEffectType() == null;
    }

    public static <T extends Enum<T>> T getOrNull(Class<T> clazz, String name) {
        return getOrDefault(clazz, name, null);
    }

    public static <T extends Enum<T>> T getOrDefault(Class<T> clazz, String name, T defaultValue) {
        try {
            return Enum.valueOf(clazz, name);
        } catch (IllegalArgumentException exception) {
            return defaultValue;
        }
    }

    public static int getRangedAmount(@NotNull String string) {
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
                return RandomUtils.nextInt(min, max + 1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return 1;
    }

    public static String toRoman(int number) {
        Map.Entry<Integer, String> entry = ROMAN_NUMERALS.floorEntry(number);
        Integer key = entry.getKey();
        String value = entry.getValue();
        return number == key ? value : value + toRoman(number - key);
    }

    public static boolean isSteveSkin(@NotNull java.awt.Color color) {
        return color.getRed() > 0 || color.getGreen() > 0 || color.getBlue() > 0;
    }

    public static @NotNull BufferedImage removeHat(@NotNull BufferedImage raw) {
        // Remove second layer from head (a.k.a. hat).
        int width = raw.getWidth();
        int height = raw.getHeight();

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int[] pixels = new int[width * height];
        raw.getRGB(0, 0, width, height, pixels, 0, width);

        for (int i = 0; i < pixels.length; i++) {
            int x = (i <= width - 1) ? i : i - (int) (width * (Math.floor((double) i / width)));
            int y = (i <= width - 1) ? 0 : (int) (height * (Math.floor((double) i / height))) / height;

            if ((x >= 32 && x <= 64 && y >= 8 && y <= 15) || (x >= 40 && x <= 55 && y >= 0 && y <= 7)) {
                pixels[i] = 0x00ffffff;
            }
        }
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    public static @NotNull BufferedImage convertTo64x64(@NotNull BufferedImage image) {
        if (image.getHeight() != 32) return image;

        BufferedImage to64x64 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);

        Graphics2D graphics = to64x64.createGraphics();

        // Draw default skin.
        graphics.drawImage(image, 0, 0, null);

        // Copy and draw the necessary parts to make it 64x64.
        graphics.drawImage(to64x64.getSubimage(0, 16, 16, 16), 16, 48, null);
        graphics.drawImage(to64x64.getSubimage(40, 16, 16, 16), 32, 48, null);

        graphics.dispose();

        // Save new image.
        return to64x64;
    }

    public static @NotNull String capitalizeFully(String string) {
        // Fighting deprecation of WordUtils...
        string = string.toLowerCase(Locale.ROOT);
        if (StringUtils.isEmpty(string)) return string;

        char[] buffer = string.toCharArray();
        boolean capitalizeNext = true;
        for (int i = 0; i < buffer.length; i++) {
            char character = buffer[i];
            if (Character.isWhitespace(character)) {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                buffer[i] = Character.toTitleCase(character);
                capitalizeNext = false;
            }
        }
        return new String(buffer);
    }

    public static @Nullable FileConfiguration reloadConfig(RealisticVillagers plugin, @NotNull File file, @Nullable Consumer<File> error) {
        File backup = null;
        try {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String time = format.format(new Date(System.currentTimeMillis()));

            // When error is null, that means that the file has already regenerated, so we don't need to create a backup.
            if (error != null) {
                backup = new File(file.getParentFile(), file.getName().split("\\.")[0] + "_" + time + ".bak");
                FileUtils.copyFile(file, backup);
            }

            FileConfiguration configuration = new YamlConfiguration();
            configuration.load(file);

            if (backup != null) FileUtils.deleteQuietly(backup);

            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            Logger logger = plugin.getLogger();

            logger.severe("An error occurred while reloading the file {" + file.getName() + "}.");

            boolean errorLogged = false;
            if (backup != null && exception instanceof InvalidConfigurationException invalid) {
                errorLogged = true;

                Throwable cause = invalid.getCause();
                if (cause instanceof MarkedYAMLException marked) {
                    handleError(backup, marked.getProblemMark().getLine());
                } else {
                    errorLogged = false;
                }
            }

            if (errorLogged) {
                logger.severe("The file will be restarted and a copy of the old file will be saved indicating which line had an error.");
            } else {
                logger.severe("The file will be restarted and a copy of the old file will be saved.");
            }

            if (error == null) {
                exception.printStackTrace();
                return null;
            }

            // Only replace the file if an exception ocurrs.
            FileUtils.deleteQuietly(file);
            error.accept(file);

            return reloadConfig(plugin, file, null);
        }
    }

    private static void handleError(@NotNull File backup, int line) {
        try {
            Path path = backup.toPath();

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            lines.set(line, lines.get(line) + " # <--------------------< ERROR <--------------------<");

            Files.write(path, lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    public static boolean hasAnyOf(@NotNull InventoryHolder holder, NamespacedKey key) {
        for (ItemStack item : holder.getInventory().getContents()) {
            if (isItem(item, key)) return true;
        }
        return false;
    }

    public static @NotNull String getProfessionOrType(LivingEntity living) {
        return (living instanceof Villager villager ? villager.getProfession().name() : living.getType().name())
                .toLowerCase(Locale.ROOT)
                .replace("_", "-");
    }

    public static boolean isItem(ItemStack item, NamespacedKey key) {
        ItemMeta meta;
        if (item == null || (meta = item.getItemMeta()) == null) return false;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(key, PersistentDataType.INTEGER);
    }

    public static @NotNull String getRandomSex() {
        return ThreadLocalRandom.current().nextBoolean() ? "male" : "female";
    }

    public static void teleportWithPassengers(@NotNull LivingEntity living, Location targetLocation) {
        if (living.teleport(targetLocation)) return;
        if (GET_HANDLE == null || ABS_MOVE_TO == null) return;

        // We can't teleport entities with passengers with the API.
        try {
            Object nmsEntity = GET_HANDLE.invoke(CRAFT_ENTITY.cast(living));
            ABS_MOVE_TO.invoke(
                    nmsEntity,
                    targetLocation.getX(),
                    targetLocation.getY(),
                    targetLocation.getZ(),
                    targetLocation.getYaw(),
                    targetLocation.getPitch());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getOrDefault(
            @NotNull Map<String, Object> args,
            String key,
            @NotNull Class<T> clazz) {
        return getOrDefault(args, key, clazz, (T) DEFAULT_VALUES.getOrDefault(clazz, null));
    }

    public static <T> T getOrDefault(
            @NotNull Map<String, Object> args,
            String key,
            @NotNull Class<T> clazz,
            T defaultValue) {
        return getOrDefault(args, key, clazz, Function.identity(), defaultValue);
    }

    public static <T, Z> Z getOrDefault(
            @NotNull Map<String, Object> args,
            String key,
            @NotNull Class<T> clazz,
            Function<T, Z> mapper,
            Z defaultValue) {
        Object value = args.get(key);
        return value != null ? mapper.apply(clazz.cast(value)) : defaultValue;
    }
}