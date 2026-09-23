package me.matsubara.realisticvillagers.village;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * What a recorded building is called on screen.
 * <p>
 * Recorded buildings are filed under their plain type — residential, workplace, mixed, unknown —
 * and the wording is looked up here, every time, so renaming a type in the config renames it on
 * every building of that type at once. Writing the wording into the file name instead would
 * freeze whatever it happened to be on the day the building was recorded, and the two would drift
 * apart from then on.
 * <p>
 * Shared rather than sitting in one menu, because the same building is named in three places —
 * the commission list, the hologram, and the map — and three copies of this rule is three chances
 * for them to disagree about what the same building is called.
 */
public final class BuildingNames {

    private BuildingNames() {
    }

    /**
     * The display name for a blueprint.
     * <p>
     * A file somebody named themselves is shown as they named it; only the type keys are
     * translated, so dropping in {@code windmill.schem} gives you a Windmill and not a guess.
     */
    public static @NotNull String display(@NotNull RealisticVillagers plugin, @NotNull Blueprint blueprint) {
        String raw = blueprint.getName();
        String[] words = raw.split(" ");

        String last = words[words.length - 1].toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");

        String label = labelFor(plugin, last);
        if (label == null) return PluginUtils.translate(raw);
        if (words.length == 1) return label;

        // A trade in front of the type: "Farmer — Workplace" says more than "Workplace" and is
        // still the configured type word, not a second name to keep in step.
        String trade = String.join(" ", Arrays.copyOf(words, words.length - 1));

        return PluginUtils.translate(text(plugin, "gui.commission.name-with-trade", "&f%trade% &7— %type%")
                .replace("%trade%", trade)
                .replace("%type%", label));
    }

    private static @Nullable String labelFor(@NotNull RealisticVillagers plugin, @NotNull String type) {
        return switch (type) {
            case "residential" -> text(plugin, "gui.radar.type-residential", "&aResidential");
            case "workplace" -> text(plugin, "gui.radar.type-workplace", "&6Workplace");
            case "mixed" -> text(plugin, "gui.radar.type-mixed", "&bHome & workshop");
            case "unknown" -> text(plugin, "gui.radar.type-unknown", "&7Unknown");
            default -> null;
        };
    }

    private static @NotNull String text(@NotNull RealisticVillagers plugin, String path, String fallback) {
        String value = plugin.getGuiConfig() == null ? null : plugin.getGuiConfig().getString(path);
        return PluginUtils.translate(value != null && !value.isEmpty() ? value : fallback);
    }
}
