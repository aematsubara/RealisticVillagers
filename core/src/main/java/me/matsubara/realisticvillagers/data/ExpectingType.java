package me.matsubara.realisticvillagers.data;

public enum ExpectingType {
    BED,
    GIFT;

    public boolean isBed() {
        return this == BED;
    }

    public boolean isGift() {
        return this == GIFT;
    }
}
