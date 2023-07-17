package me.matsubara.realisticvillagers.gui.types;

import lombok.Getter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.files.Config;
import me.matsubara.realisticvillagers.gui.InteractGUI;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
public class NewSkinGUI extends InteractGUI {

    private final Player player;
    private final boolean isMale;
    private final boolean isAdult;
    private final ItemStack fromPlayer;
    private final ItemStack fromConsole;

    public NewSkinGUI(RealisticVillagers plugin, @NotNull Player player, boolean isMale, boolean isAdult) {
        super(plugin, null, "new-skin", 27, title -> title
                        .replace("%sex%", isMale ? Config.MALE.asString() : Config.FEMALE.asString())
                        .replace("%age-stage%", isAdult ? Config.ADULT.asString() : Config.KID.asString()),
                false);

        this.player = player;
        this.isMale = isMale;
        this.isAdult = isAdult;

        inventory.setItem(10, fromPlayer = getGUIItem("from-player"));
        inventory.setItem(16, fromConsole = getGUIItem("from-console"));

        player.openInventory(inventory);
    }
}