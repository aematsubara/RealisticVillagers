package me.matsubara.realisticvillagers.data;

public enum InteractType {
    GUI,
    FOLLOWING,
    STAY;

    public boolean isGUI() {
        return this == GUI;
    }

    public boolean isFollowing() {
        return this == FOLLOWING;
    }

    public boolean isStay() {
        return this == STAY;
    }
}