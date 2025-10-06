package me.matsubara.realisticvillagers.util;

import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum VersionMatcher {
    v1_18("1.18.2"),
    v1_19("1.19.4"),
    v1_20_6("1.20.6"),
    v1_21_4("1.21.7", "1.21.8");

    private final String[] versions;

    VersionMatcher(String... versions) {
        this.versions = versions;
    }

    public static @Nullable VersionMatcher getByMinecraftVersion() {
        String current = Bukkit.getBukkitVersion().split("-")[0];
        for (VersionMatcher version : values()) {
            if (ArrayUtils.contains(version.versions, current)) {
                return version;
            }
        }
        return null;
    }

    public boolean higherOrEqualThan(@NotNull VersionMatcher compare) {
        return ordinal() >= compare.ordinal();
    }
}