package me.matsubara.realisticvillagers.data;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;

@SuppressWarnings("unused")
public enum GUIInteractType {
    CHAT,
    GREET,
    STORY,
    JOKE,
    FLIRT,
    BE_PROUD_OF,
    INSULT,
    GIFT;

    public boolean isChat() {
        return this == CHAT;
    }

    public boolean isGreet() {
        return this == GREET;
    }

    public boolean isStory() {
        return this == STORY;
    }

    public boolean isJoke() {
        return this == JOKE;
    }

    public boolean isFlirt() {
        return this == FLIRT;
    }

    public boolean isBeProudOf() {
        return this == BE_PROUD_OF;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isInsult() {
        return this == INSULT;
    }

    public boolean isGift() {
        return this == GIFT;
    }

    public @NotNull String getName() {
        if (isBeProudOf()) return "proud-of";
        return name().toLowerCase(Locale.ROOT);
    }
}