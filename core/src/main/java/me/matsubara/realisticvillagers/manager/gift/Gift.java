package me.matsubara.realisticvillagers.manager.gift;

import lombok.Getter;
import org.bukkit.Material;

/**
 * Represents a single giftable item loaded from {@code gifts.items} in config.yml.
 *
 * <ul>
 *   <li>{@link #type}              – the Bukkit material.</li>
 *   <li>{@link #category}          – LOVED / NEUTRAL / DISLIKED.</li>
 *   <li>{@link #reputation}        – raw reputation delta (may be negative for DISLIKED items).</li>
 *   <li>{@link #inventoryLootOnly} – if {@code true}, villagers won't walk towards this item
 *       on the ground; it is only gained via direct gifting.</li>
 * </ul>
 */
@Getter
public class Gift {

    private final Material type;
    private final GiftCategory category;
    private final int reputation;
    private final boolean inventoryLootOnly;

    public Gift(Material type, GiftCategory category, int reputation, boolean inventoryLootOnly) {
        this.type = type;
        this.category = category;
        this.reputation = reputation;
        this.inventoryLootOnly = inventoryLootOnly;
    }

    public boolean is(Material type) {
        return this.type == type;
    }
}