package me.matsubara.realisticvillagers.gui;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.util.InventoryUpdate;
import org.apache.commons.lang3.ArrayUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public abstract class PaginatedGUI extends InteractGUI {

    protected final Player player;
    protected final List<ItemStack> items;

    protected @Getter int currentPage;
    protected int pages;

    private final int[] slots;
    private final int[] hotbar;

    public PaginatedGUI(RealisticVillagers plugin, IVillagerNPC npc, String name, int size, Player player, List<ItemStack> items, @Nullable Integer page) {
        super(plugin, npc, name, size, null, false);
        this.player = player;
        this.items = items;
        this.currentPage = page != null ? page : 0;

        int requiredSlotRows = size / 9 - 3;

        slots = new int[requiredSlotRows * 7];

        for (int row = 0; row < requiredSlotRows; row++) {
            int current = row + 1;
            int currentRequired = current * 9 + 1;
            for (int slot = currentRequired; slot < currentRequired + 7; slot++) {
                slots[slot - currentRequired + row * 7] = slot;
            }
        }

        int last = slots[slots.length - 1];
        hotbar = IntStream.range(last + 3, last + 10).toArray();

        updateInventory();
        player.openInventory(inventory);
        InventoryUpdate.updateInventory(player, getTitle());
    }

    public void updateInventory() {
        clear(slots, hotbar);

        pages = (int) (Math.ceil((double) items.size() / slots.length));

        addButtons();

        InventoryUpdate.updateInventory(player, getTitle());

        if (items.isEmpty()) return;

        Map<Integer, Integer> slotIndex = new HashMap<>();
        for (int i : slots) {
            slotIndex.put(ArrayUtils.indexOf(slots, i), i);
        }

        int startFrom = currentPage * slots.length;
        boolean isLastPage = currentPage == pages - 1;

        for (int index = 0, aux = startFrom; isLastPage ? (index < items.size() - startFrom) : (index < slots.length); index++, aux++) {
            // Set item in the respective slot.
            inventory.setItem(slotIndex.get(index), items.get(aux));
        }
    }

    public abstract void addButtons();

    @Override
    protected String getTitle() {
        return super.getTitle()
                .replace("%page%", String.valueOf(currentPage + 1))
                .replace("%max-page%", String.valueOf(pages == 0 ? 1 : pages));
    }

    public void previousPage(boolean isShiftClick) {
        currentPage = isShiftClick ? 0 : currentPage - 1;
        updateInventory();
    }

    public void nextPage(boolean isShiftClick) {
        currentPage = isShiftClick ? pages - 1 : currentPage + 1;
        updateInventory();
    }
}