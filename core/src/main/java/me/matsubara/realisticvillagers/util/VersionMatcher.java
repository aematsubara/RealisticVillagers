package me.matsubara.realisticvillagers.util;

import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

public enum VersionMatcher {
    v1_18("1.18.2"),
    v1_19("1.19.4"),
    v1_20_1("1.20.1"),
    v1_20_2("1.20.2"),
    v1_20_4("1.20.4"),
    v1_20_6("1.20.6"),
    v1_21("1.21", "1.21.1"),
    v1_21_4("1.21.4");

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
}