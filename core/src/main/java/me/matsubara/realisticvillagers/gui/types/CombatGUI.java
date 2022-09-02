package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import me.matsubara.realisticvillagers.util.EntityHead;
import me.matsubara.realisticvillagers.util.InventoryUpdate;
import me.matsubara.realisticvillagers.util.ItemBuilder;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public final class CombatGUI extends InteractGUI {

    private final Player player;
    private final List<EntityHead> heads;
    private final String keyword;

    private int current;
    private int pages;

    private final static int[] SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private final static int[] STATUS_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private final static int[] HOTBAR = {28, 29, 30, 31, 32, 33, 34};

    private final ItemStack previous;
    private final ItemStack search;
    private final ItemStack clearSearch;
    private final ItemStack close;
    private final ItemStack next;
    private final ItemStack enabled;
    private final ItemStack disabled;

    public CombatGUI(RealisticVillagers plugin, Player player, IVillagerNPC npc, @Nullable String keyword) {
        super("combat", plugin, npc, 45, null);
        this.player = player;

        previous = getGUIItem("previous");
        search = getGUIItem("search");
        clearSearch = getGUIItem("clear-search");
        close = getGUIItem("close");
        next = getGUIItem("next");
        enabled = getGUIItem("enabled");
        disabled = getGUIItem("disabled");

        this.heads = new ArrayList<>();
        for (EntityHead skull : EntityHead.values()) {
            if (skull.getType() != null) heads.add(skull);
        }

        this.keyword = keyword;

        if (keyword != null && !keyword.isEmpty()) {
            this.heads.removeIf(head -> !head.name().toLowerCase().contains(keyword.toLowerCase()));
        }

        player.openInventory(inventory);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::updateInventory);
    }

    public void updateInventory() {
        inventory.clear();

        pages = (int) (Math.ceil((double) heads.size() / SLOTS.length));

        ItemStack background = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setDisplayName("&7")
                .build();

        for (int i = 0; i < 44; i++) {
            if (ArrayUtils.contains(SLOTS, i)
                    || ArrayUtils.contains(STATUS_SLOTS, i)
                    || ArrayUtils.contains(HOTBAR, i)) continue;
            inventory.setItem(i, background);
        }

        if (current > 0) inventory.setItem(28, previous);
        inventory.setItem(31, keyword != null ? clearSearch : search);
        inventory.setItem(44, close);
        if (current < pages - 1) inventory.setItem(34, next);

        InventoryUpdate.updateInventory(plugin, player, getTitle());

        if (heads.isEmpty()) return;

        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : SLOTS) {
            slotIndex.put(ArrayUtils.indexOf(SLOTS, i), i);
        }

        int startFrom = current * SLOTS.length;
        boolean isLastPage = current == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < heads.size() - startFrom) : (index < SLOTS.length); index++, aux++) {
            EntityHead skull = heads.get(aux);

            String name = plugin.getConfig().getString("variable-text.entity." + skull.name().toLowerCase());

            ItemBuilder builder = new ItemBuilder(getGUIItem("entity"));
            if (skull.getUrl() == null) {
                builder.setType(skull.getHead().getType());
            } else {
                builder.setHead(skull.getUrl(), true);
            }

            // Set item in the respective slot.
            inventory.setItem(slotIndex.get(index), builder
                    .replace("%entity-type%", name)
                    .build());

            boolean enabled = npc.isTarget(skull.getType());

            inventory.setItem(slotIndex.get(index) + 9, new ItemBuilder(enabled ? this.enabled : this.disabled)
                    .setData(plugin.getEntityTypeKey(), PersistentDataType.STRING, skull.name())
                    .build());
        }
    }

    @Override
    protected String getTitle() {
        return super.getTitle()
                .replace("%page%", String.valueOf(current + 1))
                .replace("%max-page%", String.valueOf(pages));
    }

    public void previousPage(boolean isShiftClick) {
        // If shift click, go to the first page; otherwise, go to the previous page.
        current = isShiftClick ? 0 : current - 1;
        updateInventory();
    }

    public void nextPage(boolean isShiftClick) {
        // If shift click, go to the last page; otherwise, go to the next page.
        current = isShiftClick ? pages - 1 : current + 1;
        updateInventory();
    }
}