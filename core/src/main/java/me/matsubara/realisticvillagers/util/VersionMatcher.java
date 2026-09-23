package me.matsubara.realisticvillagers.util;

import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum VersionMatcher {
    v1_18(null, "1.18.2"),
    v1_19(null, "1.19.4"),
    v1_20_6(null, "1.20.6"),
    v1_21_8("v1_21_4", "1.21.6", "1.21.7", "1.21.8"),
    v1_21_10(null, "1.21.9", "1.21.10"),
    v1_21_11(null, "1.21.11"),
    v26_1(null, "26.1", "26.1.1", "26.1.2", "26.2");

    private final String differentName;
    private final String[] versions;

    VersionMatcher(@Nullable String differentName, String... versions) {
        this.differentName = differentName;
        this.versions = versions;
    }

    public static @Nullable VersionMatcher getByMinecraftVersion() {
        String current = normalize(Bukkit.getBukkitVersion().split("-")[0]);
        for (VersionMatcher version : values()) {
            if (ArrayUtils.contains(version.versions, current)) {
                return version;
            }
        }
        // Fallback: if the running version is newer than the latest known, use the latest NMS.
        VersionMatcher latest = values()[values().length - 1];
        String latestKnown = latest.versions[latest.versions.length - 1];
        if (compareVersions(current, latestKnown) > 0) {
            return latest;
        }
        return null;
    }

    public static boolean isExactMatch() {
        String current = normalize(Bukkit.getBukkitVersion().split("-")[0]);
        for (VersionMatcher version : values()) {
            if (ArrayUtils.contains(version.versions, current)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String version) {
        // Purpur uses "26.2.build.2600" — strip the ".build.NNNN" suffix
        return version.replaceAll("\\.build\\.\\d+$", "");
    }

    private static int compareVersions(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int length = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < length; i++) {
            int numA = i < partsA.length ? parseIntSafe(partsA[i]) : 0;
            int numB = i < partsB.length ? parseIntSafe(partsB[i]) : 0;
            if (numA != numB) return Integer.compare(numA, numB);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getPackageName() {
        return differentName != null ? differentName : name();
    }

    public boolean higherOrEqualThan(@NotNull VersionMatcher compare) {
        return ordinal() >= compare.ordinal();
    }
}