package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * A fully config-defined chest GUI (see "gui.custom.<name>" in gui.yml), always opened for a
 * specific villager+player pair (typically from a hologram menu item whose id matches the name).
 * Item click behaviour is driven entirely by each item's "actions:" list, executed by
 * {@code InventoryListeners.runCustomGuiActions}.
 */
@Getter
public final class CustomGUI extends InteractGUI {

    private final String guiName;

    public CustomGUI(RealisticVillagers plugin, IVillagerNPC npc, @NotNull Player player, @NotNull String guiName) {
        super(plugin, npc, "custom." + guiName, getValidSize(plugin, "custom." + guiName, 9),
                string -> string.replace("%player%", player.getName())
                        .replace("%reputation%", npc != null ? String.valueOf(npc.getReputation(player.getUniqueId())) : "0"),
                true);
        this.guiName = guiName;

        fillInventory();
        player.openInventory(inventory);
    }

    private void fillInventory() {
        ConfigurationSection items = plugin.getGuiConfig().getConfigurationSection("gui.custom." + guiName + ".items");
        if (items == null) return;

        for (String key : items.getKeys(false)) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                continue;
            }
            if (slot >= 0 && slot < size) inventory.setItem(slot, getGUIItem(key));
        }
    }
}
