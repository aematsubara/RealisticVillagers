package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
public class CombatSettingsGUI extends InteractGUI {

    private final ItemStack players;
    private final ItemStack animals;
    private final ItemStack monsters;

    public CombatSettingsGUI(RealisticVillagers plugin, IVillagerNPC npc, @NotNull Player player) {
        super(plugin, npc, "combat-settings", 27, null, false);

        players = getGUIItem("players");
        animals = getGUIItem("animals");
        monsters = getGUIItem("monsters");

        inventory.setItem(10, players);
        inventory.setItem(13, animals);
        inventory.setItem(16, monsters);

        player.openInventory(inventory);
    }
}