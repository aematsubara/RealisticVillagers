package me.matsubara.realisticvillagers.data;

public enum ChangeItemType {
    EATING,
    SHOWING_TRADES,
    TAMING,
    HEALING_GOLEM,
    USING_BONE_MEAL,
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

    public boolean isUsingBoneMeal(boolean usingBoneMeal) {
        return this != USING_BONE_MEAL && usingBoneMeal;
    }
}