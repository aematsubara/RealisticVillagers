package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@Getter
public class CombatSettingsGUI extends InteractGUI {

    public CombatSettingsGUI(RealisticVillagers plugin, IVillagerNPC npc, @NotNull Player player) {
        super(plugin, npc, "combat-settings", 27, null, false);

        inventory.setItem(10, getGUIItem("players"));
        inventory.setItem(13, getGUIItem("animals"));
        inventory.setItem(16, getGUIItem("monsters"));

        player.openInventory(inventory);
    }
}