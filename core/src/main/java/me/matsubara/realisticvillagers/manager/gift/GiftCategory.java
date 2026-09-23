package me.matsubara.realisticvillagers.manager.gift;

import java.util.Locale;

/**
 * Describes how a villager feels about receiving a particular item as a gift.
 *
 * <ul>
 *   <li>{@link #LOVED}    – the villager is thrilled; gives a large reputation bonus.</li>
 *   <li>{@link #NEUTRAL}  – the villager accepts it politely; gives a small reputation bonus.</li>
 *   <li>{@link #DISLIKED} – the villager is offended; causes a reputation loss.</li>
 * </ul>
 */
public enum GiftCategory {
    LOVED,
    NEUTRAL,
    DISLIKED;

    /** Lower-case version of the enum name, used for message/config path lookups. */
    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Returns {@code true} for categories that grant positive reputation. */
    public boolean isPositive() {
        return this != DISLIKED;
    }
}