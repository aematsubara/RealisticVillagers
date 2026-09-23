package me.matsubara.realisticvillagers.village;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The settlement's shared pool of goods.
 * <p>
 * Workers deposit what they produce here and builders draw from it, so it is deliberately
 * a plain item ledger rather than a real inventory — nothing about it is tied to a chest
 * in the world, and it survives the chunk it was filled in being unloaded.
 */
public final class VillageStorage {

    private final Map<Material, Integer> items = new EnumMap<>(Material.class);

    /** Adds {@code amount} of {@code material}; non-positive amounts are ignored. */
    public void add(@Nullable Material material, int amount) {
        if (material == null || amount <= 0) return;
        items.merge(material, amount, Integer::sum);
    }

    /**
     * Removes up to {@code amount} of {@code material}.
     *
     * @return how many were actually removed, which is less than {@code amount} when the
     * storage ran short.
     */
    public int remove(@Nullable Material material, int amount) {
        if (material == null || amount <= 0) return 0;

        Integer current = items.get(material);
        if (current == null) return 0;

        int removed = Math.min(current, amount);
        if (removed >= current) {
            items.remove(material);
        } else {
            items.put(material, current - removed);
        }
        return removed;
    }

    /** Whether the storage holds at least {@code amount} of {@code material}. */
    public boolean has(@Nullable Material material, int amount) {
        return count(material) >= amount;
    }

    public int count(@Nullable Material material) {
        if (material == null) return 0;
        return items.getOrDefault(material, 0);
    }

    /** Live, read-only view of everything held. */
    public Map<Material, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
    }

    public void save(@NotNull ConfigurationSection section) {
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            section.set(entry.getKey().name(), entry.getValue());
        }
    }

    public void load(@Nullable ConfigurationSection section) {
        items.clear();
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            // Materials get renamed between Minecraft versions; a stored key that no longer
            // resolves is dropped rather than failing the whole village load.
            Material material = Material.matchMaterial(key);
            if (material == null) continue;

            int amount = section.getInt(key, 0);
            if (amount > 0) items.put(material, amount);
        }
    }
}
