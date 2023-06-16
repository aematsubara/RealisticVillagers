package me.matsubara.realisticvillagers.data;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public enum ExpectingType {
    BED,
    GIFT;

    public boolean isBed() {
        return this == BED;
    }

    public boolean isGift() {
        return this == GIFT;
    }

    @Contract(pure = true)
    @Override
    public @NotNull String toString() {
        return name();
    }
}