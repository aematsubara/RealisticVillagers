package me.matsubara.realisticvillagers.gui.types;

import me.matsubara.realisticvillagers.village.ElectoralProgram;
import me.matsubara.realisticvillagers.village.MayorManager;
import me.matsubara.realisticvillagers.village.Village;
import me.matsubara.realisticvillagers.village.VillageManager;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The mayor's screen: what the settlement looks like right now, and whether the player has
 * earned the right to commission work from it.
 * <p>
 * This is the shell the radar and the schematic reader will hang off; for now it answers the
 * two questions a player actually has when they walk up to the mayor — "how am I doing here"
 * and "why can't I build yet".
 */
@Getter
public final class MayorGUI implements InventoryHolder {

    private final RealisticVillagers plugin;
    private final UUID villageId;
    private final Inventory inventory;

    private static final int SIZE = 27;

    public MayorGUI(@NotNull RealisticVillagers plugin, @NotNull Village village, @NotNull Player viewer) {
        this.plugin = plugin;
        this.villageId = village.getId();

        String title = text("gui.mayor.title", "&8Mayor of %village%")
                .replace("%village%", village.getDisplayName());

        this.inventory = Bukkit.createInventory(this, SIZE, PluginUtils.translate(title));

        build(village, viewer);
    }

    private void build(@NotNull Village village, @NotNull Player viewer) {
        VillageManager villages = plugin.getVillageManager();
        MayorManager mayors = plugin.getMayorManager();

        inventory.setItem(4, villageItem(village, villages));
        inventory.setItem(11, standingItem(village, viewer, villages, mayors));
        inventory.setItem(SLOT_COMMISSION, commissionItem(village, viewer, mayors));
        inventory.setItem(SLOT_STORAGE, storageItem(village));
        inventory.setItem(SLOT_RADAR, radarItem());
        inventory.setItem(SLOT_BORDERS, bordersItem(viewer));

        // Always shown, even with no programme to show. "Nothing here" answers the question;
        // an empty slot leaves the player wondering whether they missed it.
        inventory.setItem(SLOT_PROGRAM, programItem(village.getMayorProgram()));
    }

    private @NotNull ItemStack villageItem(@NotNull Village village, @NotNull VillageManager villages) {
        List<String> lore = new ArrayList<>();
        lore.add(text("gui.mayor.residents", "&7Residents: &f%count%")
                .replace("%count%", String.valueOf(villages.getResidents(village).size())));
        lore.add(text("gui.mayor.radius", "&7Radius: &f%radius% blocks")
                .replace("%radius%", String.valueOf(village.getEffectiveRadius(villages.getDefaultRadius()))));
        lore.add(text("gui.mayor.center", "&7Centre: &f%x%, %z%")
                .replace("%x%", String.valueOf(village.getCenterX()))
                .replace("%z%", String.valueOf(village.getCenterZ())));

        return new ItemBuilder(Material.BELL)
                .setDisplayName(text("gui.mayor.village-name", "&e%village%")
                        .replace("%village%", village.getDisplayName()))
                .setLore(lore)
                .build();
    }

    private @NotNull ItemStack standingItem(
            @NotNull Village village,
            @NotNull Player viewer,
            @NotNull VillageManager villages,
            @NotNull MayorManager mayors) {

        int reputation = villages.getMayorReputation(village, viewer);
        int required = mayors.getRequiredReputation();

        List<String> lore = new ArrayList<>();
        lore.add(text("gui.mayor.reputation", "&7Standing: &f%value% &7/ &f%required%")
                .replace("%value%", String.valueOf(reputation))
                .replace("%required%", String.valueOf(required)));
        lore.add("");
        // Spell out where this number comes from — it is the single most confusing part of
        // the system, since talking to the mayor itself never moves it.
        lore.add(text("gui.mayor.reputation-hint",
                "&8Earned through the villagers themselves,&8 never by talking to the mayor."));

        return new ItemBuilder(reputation >= required ? Material.EMERALD : Material.EMERALD_BLOCK)
                .setDisplayName(text("gui.mayor.standing-name", "&aYour standing"))
                .setLore(lore)
                .build();
    }

    /** Opens the list of buildings the settlement can raise. */
    public static final int SLOT_COMMISSION = 13;

    private @NotNull ItemStack commissionItem(@NotNull Village village, @NotNull Player viewer, @NotNull MayorManager mayors) {
        MayorManager.CommissionResult result = mayors.canCommission(village, viewer);

        String statusPath = switch (result) {
            case ALLOWED -> "gui.mayor.commission-allowed";
            case NO_MAYOR -> "gui.mayor.commission-no-mayor";
            case LOW_REPUTATION -> "gui.mayor.commission-low-reputation";
            case NO_FAMILY -> "gui.mayor.commission-no-family";
        };

        String fallback = switch (result) {
            case ALLOWED -> "&aYou may commission new buildings.";
            case NO_MAYOR -> "&cThis settlement has no mayor.";
            case LOW_REPUTATION -> "&cYour standing with the mayor is too low.";
            case NO_FAMILY -> "&cYou need a family among this village's residents.";
        };

        List<String> lore = new ArrayList<>();
        lore.add(text(statusPath, fallback));

        return new ItemBuilder(result.isAllowed() ? Material.WRITABLE_BOOK : Material.BARRIER)
                .setDisplayName(text("gui.mayor.commission-name", "&eBuilding commissions"))
                .setLore(lore)
                .build();
    }

    /**
     * Opens the chest-grid map. Fixed so the click handler can find it, and centred under the
     * row above rather than tucked off to one side.
     */
    public static final int SLOT_RADAR = 20;

    /** Turns the settlement's edge on and off in the world. */
    public static final int SLOT_BORDERS = 24;

    /** What the sitting mayor governs on — the middle of the row, since it describes the rest. */
    public static final int SLOT_PROGRAM = 22;

    private @NotNull ItemStack bordersItem(@NotNull Player viewer) {
        var borders = plugin.getBorderVisualizer();
        boolean showing = borders != null && borders.isShowing(viewer);

        List<String> lore = new ArrayList<>();
        lore.add(text(showing ? "gui.mayor.borders-on" : "gui.mayor.borders-off",
                showing ? "&aCurrently shown." : "&7Currently hidden."));

        return new ItemBuilder(showing ? Material.LIME_DYE : Material.GRAY_DYE)
                .setDisplayName(text("gui.mayor.borders-name", "&eSettlement border"))
                .setLore(lore)
                .build();
    }


    private @NotNull ItemStack radarItem() {
        // A mutable list, because PluginUtils.translate colours the lore in place — List.of gives
        // an immutable one and the whole screen dies on an UnsupportedOperationException before
        // it can open. Every other lore here is built with ArrayList for the same reason.
        List<String> lore = new ArrayList<>();
        lore.add(text("gui.mayor.radar-hint", "&7See the settlement laid out, and what stands where."));

        return new ItemBuilder(Material.FILLED_MAP)
                .setDisplayName(text("gui.mayor.radar-name", "&eVillage map"))
                .setLore(lore)
                .build();
    }

    /** Opens the stores, where goods can be given or drawn out. */
    public static final int SLOT_STORAGE = 15;

    private @NotNull ItemStack storageItem(@NotNull Village village) {
        VillageStorage storage = village.getStorage();

        List<String> lore = new ArrayList<>();
        if (storage.isEmpty()) {
            lore.add(text("gui.mayor.storage-empty", "&7The stores are empty."));
        } else {
            int shown = 0;
            for (Map.Entry<Material, Integer> entry : storage.getItems().entrySet()) {
                if (shown++ >= 10) {
                    lore.add(text("gui.mayor.storage-more", "&8...and more"));
                    break;
                }
                lore.add(text("gui.mayor.storage-entry", "&7%item%: &f%amount%")
                        .replace("%item%", prettify(entry.getKey()))
                        .replace("%amount%", String.valueOf(entry.getValue())));
            }
        }

        return new ItemBuilder(Material.CHEST)
                .setDisplayName(text("gui.mayor.storage-name", "&eSettlement stores"))
                .setLore(lore)
                .build();
    }

    /**
     * The platform the sitting mayor won on, and what it does to the settlement.
     * <p>
     * Spelled out rather than named, because the name alone says nothing: knowing a mayor is
     * "Industrious" is only useful once you can see it means faster gathering and dearer
     * building.
     */
    private @NotNull ItemStack programItem(@Nullable ElectoralProgram program) {
        List<String> lore = new ArrayList<>();

        if (program == null) {
            lore.add(text("gui.mayor.program-none", "&7This mayor took office without a platform."));

            return new ItemBuilder(Material.GRAY_DYE)
                    .setDisplayName(text("gui.mayor.program-name", "&eGoverning programme: &f%program%")
                            .replace("%program%", text("gui.mayor.program-none-name", "none")))
                    .setLore(lore)
                    .build();
        }

        lore.add(effect("gui.mayor.effect-trade", "&7Trade prices: %value%", program.getTradePriceMultiplier()));
        lore.add(effect("gui.mayor.effect-cost", "&7Build cost: %value%", program.getBuildCostMultiplier()));
        lore.add(effect("gui.mayor.effect-time", "&7Build time: %value%", program.getBuildTimeMultiplier()));
        lore.add(effect("gui.mayor.effect-harvest", "&7Resource gathering: %value%", program.getHarvestMultiplier()));

        String name = plugin.getGuiConfig() == null
                ? null
                : plugin.getGuiConfig().getString("gui.election.programs." + program.getKey());

        return new ItemBuilder(Material.WRITTEN_BOOK)
                .setDisplayName(text("gui.mayor.program-name", "&eGoverning programme: &f%program%")
                        .replace("%program%", name != null && !name.isEmpty()
                                ? PluginUtils.translate(name)
                                : program.getKey()))
                .setLore(lore)
                .build();
    }

    /** Renders a multiplier the way a player reads it: 0.8 becomes -20%, coloured by direction. */
    private @NotNull String effect(String path, String fallback, double multiplier) {
        int percent = (int) Math.round((multiplier - 1.0d) * 100.0d);

        String value;
        if (percent == 0) {
            value = "&7—";
        } else {
            value = (percent > 0 ? "&c+" : "&a") + percent + "%";
        }

        return text(path, fallback).replace("%value%", PluginUtils.translate(value));
    }

    private @NotNull String effectLine(String path, String fallback, double multiplier) {
        int percent = (int) Math.round((multiplier - 1.0d) * 100.0d);

        String value;
        if (percent == 0) {
            value = "&7unchanged";
        } else if (percent < 0) {
            value = "&a" + percent + "%";
        } else {
            value = "&c+" + percent + "%";
        }

        return text(path, fallback).replace("%value%", value);
    }

    private static @NotNull String prettify(@NotNull Material material) {
        String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
    }

    private @NotNull String text(String path, String fallback) {
        // Screen text belongs to gui.yml, not the main config.
        String value = plugin.getGuiConfig().getString(path);
        return value != null && !value.isEmpty() ? value : fallback;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
