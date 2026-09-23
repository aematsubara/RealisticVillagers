package me.matsubara.realisticvillagers.gui.types;

import me.matsubara.realisticvillagers.village.MayorManager;
import me.matsubara.realisticvillagers.village.Village;
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
 * The settlement's stores, as something a player can actually put things into and take things
 * out of.
 * <p>
 * It was a list of numbers before — the mayor's screen printed what was held and there was no way
 * to add to it or draw from it, which made the whole pool something you watched rather than
 * something you used. A player who wants to fund a building had no way to contribute to it.
 * <p>
 * Taking and giving are gated differently on purpose. <b>Anyone may deposit</b>: putting goods
 * into a village you have no standing in is a gift, and gifts are how standing is earned in the
 * first place. <b>Taking needs the same standing as commissioning does</b>, because the stores
 * are what the settlement builds with, and a stranger emptying them is theft dressed up as a
 * menu.
 */
@Getter
public final class StorageGUI implements InventoryHolder {

    private final RealisticVillagers plugin;
    private final UUID villageId;
    private final Inventory inventory;

    /** Slot → the material shown on it, so a click knows what to withdraw. */
    private final Map<Integer, Material> slots = new HashMap<>();

    private final boolean mayWithdraw;

    private static final int SIZE = 54;
    private static final int CONTENT_SLOTS = 45;

    public static final int SLOT_BACK = SIZE - 1;
    public static final int SLOT_HELP = SIZE - 5;
    public static final int SLOT_PREV = SIZE - 9;
    public static final int SLOT_NEXT = SIZE - 2;

    private final int page;
    private final int pages;

    public StorageGUI(@NotNull RealisticVillagers plugin, @NotNull Village village, @NotNull Player viewer) {
        this(plugin, village, viewer, 0);
    }

    public StorageGUI(@NotNull RealisticVillagers plugin, @NotNull Village village, @NotNull Player viewer, int page) {
        this.plugin = plugin;
        this.villageId = village.getId();

        int total = village.getStorage().getItems().size();
        this.pages = Math.max(1, (total + CONTENT_SLOTS - 1) / CONTENT_SLOTS);
        this.page = Math.max(0, Math.min(page, pages - 1));

        MayorManager mayors = plugin.getMayorManager();
        this.mayWithdraw = mayors != null && mayors.canCommission(village, viewer).isAllowed();

        String title = text("gui.storage.title", "&8Stores of %village%")
                .replace("%village%", village.getDisplayName());

        this.inventory = Bukkit.createInventory(this, SIZE, PluginUtils.translate(title));

        build(village);
    }

    private void build(@NotNull Village village) {
        VillageStorage storage = village.getStorage();

        // A settlement's stores can hold more kinds of goods than fit on one screen, and the rest
        // used to be simply invisible — including, silently, the ones a building needed.
        List<Map.Entry<Material, Integer>> items = new ArrayList<>(storage.getItems().entrySet());

        int from = page * CONTENT_SLOTS;
        int slot = 0;

        for (int i = from; i < items.size() && slot < CONTENT_SLOTS; i++) {
            Map.Entry<Material, Integer> entry = items.get(i);

            inventory.setItem(slot, stack(entry.getKey(), entry.getValue()));
            slots.put(slot, entry.getKey());
            slot++;
        }

        if (pages > 1) {
            if (page > 0) {
                inventory.setItem(SLOT_PREV, new ItemBuilder(Material.ARROW)
                        .setDisplayName(text("gui.storage.previous", "&e« Previous page"))
                        .build());
            }
            if (page < pages - 1) {
                inventory.setItem(SLOT_NEXT, new ItemBuilder(Material.ARROW)
                        .setDisplayName(text("gui.storage.next", "&eNext page »"))
                        .build());
            }
        }

        if (storage.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER)
                    .setDisplayName(text("gui.storage.empty", "&7The stores are empty."))
                    .build());
        }

        List<String> help = new ArrayList<>();
        help.add(text("gui.storage.help-deposit", "&7Shift-click an item in your inventory to give it."));
        help.add(mayWithdraw
                ? text("gui.storage.help-withdraw", "&7Click a stack here to take it.")
                : text("gui.storage.help-no-withdraw", "&cYou need standing with the mayor to take from the stores."));

        inventory.setItem(SLOT_HELP, new ItemBuilder(Material.BOOK)
                .setDisplayName(text("gui.storage.help", "&eThe settlement's stores"))
                .setLore(help)
                .build());

        inventory.setItem(SLOT_BACK, new ItemBuilder(Material.BARRIER)
                .setDisplayName(text("gui.storage.back", "&c<< Back"))
                .build());
    }

    /**
     * One kind of goods, shown as the real item.
     * <p>
     * The stack is capped at what the item can normally hold, and how many are actually held is
     * spelled out in the lore — a hundred planks cannot be shown as one stack of a hundred, and
     * quietly showing 64 would misreport the stores.
     */
    private @NotNull ItemStack stack(@NotNull Material material, int held) {
        List<String> lore = new ArrayList<>();
        lore.add(text("gui.storage.held", "&7Held: &f%amount%").replace("%amount%", String.valueOf(held)));

        if (mayWithdraw) {
            lore.add("");
            lore.add(text("gui.storage.take", "&eClick to take up to one stack"));
        }

        return new ItemBuilder(material)
                .setDisplayName(text("gui.storage.item", "&f%item%").replace("%item%", prettify(material)))
                .setAmount(Math.max(1, Math.min(material.getMaxStackSize(), held)))
                .setLore(lore)
                .build();
    }

    public int getPage() {
        return page;
    }

    public @Nullable Material getMaterialAt(int slot) {
        return slots.get(slot);
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
