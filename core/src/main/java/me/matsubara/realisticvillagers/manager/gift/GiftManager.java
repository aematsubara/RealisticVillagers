package me.matsubara.realisticvillagers.manager.gift;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.util.*;

/**
 * Loads and provides gift data from the {@code gifts} section of config.yml.
 *
 * <p>The new item-centric format is:
 * <pre>
 * gifts:
 *   default-cooldown-seconds: 300
 *   max-gain: 15          # max reputation a player can gain per villager per day via gifts
 *   max-loss: 10          # max reputation a player can lose per villager per day via gifts
 *   items:
 *     GOLDEN_APPLE:
 *       category: LOVED
 *       reputation: 12
 *       inventory-loot-only: false   # optional, default false
 * </pre>
 */
public final class GiftManager {

    private final RealisticVillagers plugin;

    /** Material → Gift for O(1) lookups. */
    private final Map<Material, Gift> gifts = new EnumMap<>(Material.class);

    /**
     * Daily reputation cap tracking: villagerUUID → (playerUUID → DailyGiftData).
     * Automatically resets at UTC midnight via {@link DailyGiftData#rolloverIfNeeded()}.
     */
    private final Map<UUID, Map<UUID, DailyGiftData>> dailyCaps = new HashMap<>();

    private int defaultCooldownSeconds = 300;
    private int maxGain = 15;
    private int maxLoss = 10;

    public GiftManager(@NotNull RealisticVillagers plugin) {
        this.plugin = plugin;
        loadGiftCategories();
    }

    // ───────────────────────── Loading ─────────────────────────────────────────

    public void loadGiftCategories() {
        gifts.clear();

        defaultCooldownSeconds = plugin.getGiftsConfig().getInt("default-cooldown-seconds", 300);
        maxGain = plugin.getGiftsConfig().getInt("max-gain", 9999);
        maxLoss = plugin.getGiftsConfig().getInt("max-loss", 9999);

        ConfigurationSection items = plugin.getGiftsConfig().getConfigurationSection("items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            Material material = PluginUtils.getOrNull(Material.class, key.toUpperCase(Locale.ROOT));
            if (material == null) {
                plugin.getLogger().warning("Unknown material in gifts.items: " + key);
                continue;
            }

            String categoryName = items.getString(key + ".category", "NEUTRAL").toUpperCase(Locale.ROOT);
            GiftCategory category = PluginUtils.getOrDefault(GiftCategory.class, categoryName, GiftCategory.NEUTRAL);
            int reputation = items.getInt(key + ".reputation", 0);
            boolean inventoryLootOnly = items.getBoolean(key + ".inventory-loot-only", false);

            gifts.put(material, new Gift(material, category, reputation, inventoryLootOnly));
        }
    }

    // ───────────────────────── Lookups ─────────────────────────────────────────

    /**
     * Returns the {@link Gift} registered for the given material, or {@code null} if
     * that material is not listed under {@code gifts.items}.
     */
    public @Nullable Gift getGift(@NotNull Material material) {
        return gifts.get(material);
    }

    /**
     * Returns all registered {@link Gift} objects (read-only view).
     * Used by {@code RealisticVillagers#reloadWantedItems()}.
     */
    public @NotNull Set<Gift> getAllGifts() {
        return Collections.unmodifiableSet(new HashSet<>(gifts.values()));
    }

    /**
     * Backwards-compatible helper: returns all gifts regardless of the path argument.
     * Called by {@code RealisticVillagers#reloadWantedItems()} with {@code "default-wanted-items"}.
     */
    public @NotNull Set<Gift> getGiftsFromCategory(@SuppressWarnings("unused") String ignoredPath) {
        return getAllGifts();
    }

    // ───────────────────────── Daily Cap ───────────────────────────────────────

    /**
     * Applies the daily {@code max-gain} / {@code max-loss} cap to a raw reputation delta.
     *
     * <p>Returns the actual delta that should be applied (may be 0 if the cap is exhausted).
     * Automatically resets each UTC day.
     *
     * @param villagerUUID UUID of the villager receiving the gift
     * @param playerUUID   UUID of the gifting player
     * @param rawDelta     signed reputation change (positive = gain, negative = loss)
     * @return clamped delta that is safe to apply
     */
    public int applyDailyCap(@NotNull UUID villagerUUID, @NotNull UUID playerUUID, int rawDelta) {
        if (rawDelta == 0) return 0;

        DailyGiftData data = dailyCaps
                .computeIfAbsent(villagerUUID, k -> new HashMap<>())
                .computeIfAbsent(playerUUID, k -> new DailyGiftData());
        data.rolloverIfNeeded();

        if (rawDelta > 0) {
            int remaining = maxGain - data.gained;
            if (remaining <= 0) return 0;
            int applied = Math.min(rawDelta, remaining);
            data.gained += applied;
            return applied;
        } else {
            int remaining = maxLoss - data.lost;
            if (remaining <= 0) return 0;
            int applied = Math.min(-rawDelta, remaining);
            data.lost += applied;
            return -applied;
        }
    }

    // ───────────────────────── Accessors ───────────────────────────────────────

    public int getDefaultCooldownSeconds() {
        return defaultCooldownSeconds;
    }

    public int getMaxGain() {
        return maxGain;
    }

    public int getMaxLoss() {
        return maxLoss;
    }

    // ───────────────────────── Inner types ─────────────────────────────────────

    private static final class DailyGiftData {
        int gained = 0;
        int lost = 0;
        LocalDate date = LocalDate.now();

        void rolloverIfNeeded() {
            LocalDate today = LocalDate.now();
            if (!today.equals(date)) {
                gained = 0;
                lost = 0;
                date = today;
            }
        }
    }
}