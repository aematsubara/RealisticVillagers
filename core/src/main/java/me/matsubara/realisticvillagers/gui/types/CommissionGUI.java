package me.matsubara.realisticvillagers.gui.types;

import me.matsubara.realisticvillagers.village.Blueprint;
import me.matsubara.realisticvillagers.village.Blueprints;
import me.matsubara.realisticvillagers.village.BuildingNames;
import me.matsubara.realisticvillagers.village.ConstructionManager;
import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageNames;
import me.matsubara.realisticvillagers.village.VillageStorage;
import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import me.matsubara.realisticvillagers.util.PluginUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * What the settlement can build, what each one costs, and whether its stores cover it.
 * <p>
 * The materials are the point of this screen. A commission spends goods the whole village worked
 * for, so the answer to "can we afford this, and what are we short of" has to be readable before
 * anything is committed — not discovered as a refusal after the fact.
 */
@Getter
public final class CommissionGUI implements InventoryHolder {

    private final RealisticVillagers plugin;
    private final UUID villageId;
    private final Inventory inventory;

    /** Slot → the blueprint on it. */
    private final Map<Integer, String> blueprintSlots = new HashMap<>();

    /**
     * Whether this viewer may rename buildings.
     * <p>
     * Held to the same standing as commissioning one. A name is what everybody on the server
     * sees on a shared list, so leaving it open to anyone passing through makes it a billboard;
     * tying it to the people already trusted to spend the settlement's stores costs nothing and
     * needs no separate idea of ownership.
     */
    private final boolean mayRename;

    private static final int SIZE = 54;

    /** Content fills everything above the bottom row; the bottom row is the controls. */
    private static final int PER_PAGE = SIZE - 9;

    public static final int SLOT_PREV = SIZE - 9;
    public static final int SLOT_PAGE = SIZE - 5;
    public static final int SLOT_NEXT = SIZE - 2;
    public static final int SLOT_BACK = SIZE - 1;

    /**
     * Most material lines one building shows before the rest become a count.
     * <p>
     * A tooltip taller than the screen is unreadable and hides its own conclusion, so this is
     * kept well inside a screenful even with the name, size, time and verdict around it.
     */
    private static final int MAX_MATERIAL_LINES = 8;

    /** Which page is shown, from zero. */
    private final int page;

    /** How many there are in total, so the caller can tell whether another page exists. */
    private final int pages;

    public CommissionGUI(@NotNull RealisticVillagers plugin, @NotNull Village village, @NotNull Player viewer) {
        this(plugin, village, viewer, 0);
    }

    public CommissionGUI(@NotNull RealisticVillagers plugin, @NotNull Village village, @NotNull Player viewer, int page) {
        this.plugin = plugin;
        this.villageId = village.getId();

        Blueprints all = plugin.getBlueprints();
        int total = all == null ? 0
                : all.available(VillageNames.groupFor(village.getCenter()), viewer.getUniqueId(),
                        village.getWorldName()).size();

        this.pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        this.page = Math.max(0, Math.min(page, pages - 1));

        // Renaming needs no permission of its own: you can only see your own discoveries, so the
        // only names you can reach are ones nobody else is looking at. The server's buildings are
        // shared, and refuse to be renamed further down.
        this.mayRename = true;

        String title = text("gui.commission.title", "&8Build in %village%")
                .replace("%village%", village.getDisplayName());

        this.inventory = Bukkit.createInventory(this, SIZE, PluginUtils.translate(title));

        build(village, viewer);
    }

    private void build(@NotNull Village village, @NotNull Player viewer) {
        // Only what suits this landscape: a desert village is offered desert buildings and the
        // ones that suit anywhere, never a taiga lodge.
        Blueprints blueprints = plugin.getBlueprints();
        List<Blueprint> all = blueprints == null
                ? List.of()
                : blueprints.available(VillageNames.groupFor(village.getCenter()), viewer.getUniqueId(),
                        village.getWorldName());

        double costMultiplier = village.getMayorProgram() != null
                ? village.getMayorProgram().getBuildCostMultiplier()
                : 1.0d;
        double timeMultiplier = village.getMayorProgram() != null
                ? village.getMayorProgram().getBuildTimeMultiplier()
                : 1.0d;

        // Only this page's worth. Everything past the first screenful used to be dropped without
        // a word, so a settlement that had learned more than forty-five buildings simply could
        // not be shown the rest of them.
        int from = page * PER_PAGE;

        int slot = 0;
        for (int i = from; i < all.size() && slot < PER_PAGE; i++) {
            Blueprint blueprint = all.get(i);

            inventory.setItem(slot, entry(village, blueprint, costMultiplier, timeMultiplier));
            blueprintSlots.put(slot, blueprint.getId());
            slot++;
        }

        pageButtons();

        if (all.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .setDisplayName(text("gui.commission.none", "&cNothing here can be built yet."))
                    .setLore(hint(text("gui.commission.none-hint",
                            "&7Record a village you have found, or add structures to the blueprints folder.")))
                    .build());
        }

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .setDisplayName(text("gui.commission.back", "&c« Back"))
                .build());
    }

    /** Arrows either side of a page counter, shown only when there is somewhere to go. */
    private void pageButtons() {
        if (pages <= 1) return;

        if (page > 0) {
            inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                    .setDisplayName(text("gui.commission.previous", "&e« Previous page"))
                    .build());
        }

        if (page < pages - 1) {
            inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                    .setDisplayName(text("gui.commission.next", "&eNext page »"))
                    .build());
        }

        inventory.setItem(SLOT_PAGE, new ItemBuilder(Material.PAPER)
                .setDisplayName(text("gui.commission.page", "&7Page &f%page%&7/&f%pages%")
                        .replace("%page%", String.valueOf(page + 1))
                        .replace("%pages%", String.valueOf(pages)))
                .build());
    }

    public int getPage() {
        return page;
    }

    /**
     * One building, with its bill of materials measured against the stores.
     * <p>
     * Every line says both numbers — held and needed — because "you cannot afford this" without
     * saying what is missing leaves the player to work it out by elimination.
     */
    private @NotNull ItemStack entry(
            @NotNull Village village,
            @NotNull Blueprint blueprint,
            double costMultiplier,
            double timeMultiplier) {

        Map<Material, Integer> cost = ConstructionManager.scaledCost(blueprint, costMultiplier);
        VillageStorage storage = village.getStorage();

        boolean affordable = true;

        List<String> lore = new ArrayList<>();
        lore.add(text("gui.commission.region", "&8From: &7%region%")
                .replace("%region%", blueprint.getRegion() == null
                        ? text("gui.commission.region-any", "anywhere")
                        : blueprint.getRegion()));
        lore.add(text("gui.commission.size", "&7Size: &f%width%x%depth%x%height%")
                .replace("%width%", String.valueOf(blueprint.getWidth()))
                .replace("%depth%", String.valueOf(blueprint.getDepth()))
                .replace("%height%", String.valueOf(blueprint.getHeight())));
        lore.add(text("gui.commission.time", "&7Takes: &f%seconds%s")
                .replace("%seconds%", String.valueOf(Math.max(1, (int) Math.round(blueprint.getSeconds() * timeMultiplier)))));
        lore.add("");
        lore.add(text("gui.commission.materials", "&7Materials:"));

        // Shortest useful answer, not the whole bill.
        //
        // A building recorded from a real village is made of twenty-odd different things, and
        // listing every one produced a tooltip taller than the screen — which hid the very line
        // saying whether it could be built. What a player needs is "can I afford it, and what am
        // I short of", so the missing materials come first and the rest is a count.
        List<Map.Entry<Material, Integer>> entries = new ArrayList<>(cost.entrySet());
        entries.sort((a, b) -> {
            boolean shortA = storage.count(a.getKey()) < a.getValue();
            boolean shortB = storage.count(b.getKey()) < b.getValue();
            if (shortA != shortB) return shortA ? -1 : 1;

            // Then the biggest piles, since those are what a settlement actually has to gather.
            return Integer.compare(b.getValue(), a.getValue());
        });

        for (Map.Entry<Material, Integer> item : cost.entrySet()) {
            if (storage.count(item.getKey()) < item.getValue()) affordable = false;
        }

        int shown = 0;
        for (Map.Entry<Material, Integer> item : entries) {
            if (shown >= MAX_MATERIAL_LINES) break;

            int held = storage.count(item.getKey());
            boolean enough = held >= item.getValue();

            lore.add(text(enough ? "gui.commission.material-have" : "gui.commission.material-missing",
                            enough ? "&a+ &f%item%&7: %held%/%needed%" : "&c- &f%item%&7: %held%/%needed%")
                    .replace("%item%", prettify(item.getKey()))
                    .replace("%held%", String.valueOf(held))
                    .replace("%needed%", String.valueOf(item.getValue())));
            shown++;
        }

        if (entries.size() > shown) {
            lore.add(text("gui.commission.material-more", "&8…and %count% more")
                    .replace("%count%", String.valueOf(entries.size() - shown)));
        }

        lore.add("");
        lore.add(affordable
                ? text("gui.commission.can-build", "&aClick to place it")
                : text("gui.commission.cannot-build", "&cThe stores are short."));

        if (mayRename) lore.add(text("gui.commission.rename-hint", "&8Shift-click to rename it"));

        return new ItemBuilder(blueprint.getIcon())
                .setDisplayName(BuildingNames.display(plugin, blueprint))
                .setLore(lore)
                .build();
    }

    /**
     * A one-line lore list that can be written to.
     * <p>
     * List.of gives an immutable one, and the colouring pass rewrites lore in place — so passing
     * one straight in takes the whole screen down before it opens. This has now happened twice,
     * which is once more than it should have.
     */
    private static @NotNull List<String> hint(@NotNull String line) {
        List<String> lore = new ArrayList<>();
        lore.add(line);
        return lore;
    }

    /** The blueprint id on this slot, or {@code null}. */
    public @Nullable String getBlueprintAt(int slot) {
        return blueprintSlots.get(slot);
    }

    private static @NotNull String prettify(@NotNull Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private @NotNull String text(String path, String fallback) {
        String value = plugin.getGuiConfig() == null ? null : plugin.getGuiConfig().getString(path);
        return PluginUtils.translate(value != null && !value.isEmpty() ? value : fallback);
    }
}
