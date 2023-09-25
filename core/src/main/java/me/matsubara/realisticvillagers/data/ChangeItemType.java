package me.matsubara.realisticvillagers.data;

public enum ChangeItemType {
    EATING,
    SHOWING_TRADES,
    TAMING,
    HEALING_GOLEM,
    HELPING_FAMILY,
    USING_BONE_MEAL,
    USING_HOE,
    USING_FISHING_ROD,
    LOOTING,
    NONE;

    public boolean isEating(boolean eating) {
        return this != EATING && eating;
    }

    public boolean isShowingTrades(boolean showingTrades) {
        return this != SHOWING_TRADES && showingTrades;
    }

    public boolean isTaming(boolean taming) {
        return this != TAMING && taming;
    }

    public boolean isHealingGolem(boolean healingGolem) {
        return this != HEALING_GOLEM && healingGolem;
    }

    public boolean isHelpingFamily(boolean helpingFamily) {
        return this != HELPING_FAMILY && helpingFamily;
    }

    public boolean isUsingBoneMeal(boolean usingBoneMeal) {
        return this != USING_BONE_MEAL && usingBoneMeal;
    }

    public boolean isUsingHoe(boolean usingHoe) {
        return this != USING_HOE && usingHoe;
    }

    public boolean isUsingFishingRod(boolean usingFishingRod) {
        return this != USING_FISHING_ROD && usingFishingRod;
    }

    public boolean isLooting(boolean looting) {
        return this != LOOTING && looting;
    }
}