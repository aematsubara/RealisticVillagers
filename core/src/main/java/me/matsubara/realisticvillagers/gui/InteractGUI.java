package me.matsubara.realisticvillagers.gui;

import lombok.Getter;
import lombok.Setter;
import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import me.matsubara.realisticvillagers.gui.anim.RainbowAnimation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

@Getter
public abstract class InteractGUI implements InventoryHolder {

    protected final String name;
    protected final int size;
    protected final RealisticVillagers plugin;
    protected final IVillagerNPC npc;
    protected final Inventory inventory;
    protected final boolean useNPC;
    protected @Setter int taskId;
    protected RainbowAnimation animation;
    private boolean shouldStopInteracting;

    private static final UnaryOperator<String> EMPTY = string -> string;
    public static final Material[] PANES = {
            Material.WHITE_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.PINK_STAINED_GLASS_PANE,
            Material.GRAY_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.BROWN_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.BLACK_STAINED_GLASS_PANE};

    protected InteractGUI(RealisticVillagers plugin, IVillagerNPC npc, String name, int size, @Nullable UnaryOperator<String> operator, boolean useNPC) {
        this.name = name;
        this.plugin = plugin;
        this.npc = npc;
        this.useNPC = useNPC;

        String title = getTitle();
        if (npc != null) title = title.replace("%villager-name%", npc.getVillagerName());

        this.inventory = Bukkit.createInventory(this, (this.size = size), (operator != null ? operator : EMPTY).apply(title));

        this.shouldStopInteracting = true;
        this.taskId = (animation = new RainbowAnimation(this)).runTaskTimer(plugin, 0L, 1L).getTaskId();
    }

    protected String getTitle() {
        return plugin.getConfig().getString("gui." + name + ".title");
    }

    protected ItemStack getGUIItem(String itemName) {
        return getGUIItem(itemName, null);
    }

    protected ItemStack getGUIItem(String itemName, @Nullable UnaryOperator<String> operator) {
        return plugin.getItem("gui." + name + ".items." + itemName, useNPC && npc != null ? npc : null)
                .replace(operator != null ? operator : EMPTY)
                .build();
    }

    protected void clear(int[] @NotNull ... arrays) {
        for (int[] array : arrays) {
            for (int i : array) {
                inventory.clear(i);
            }
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public IVillagerNPC getNPC() {
        return npc;
    }

    public boolean shouldStopInteracting() {
        return shouldStopInteracting;
    }

    public void setShouldStopInteracting(boolean shouldStopInteracting) {
        this.shouldStopInteracting = shouldStopInteracting;
    }

    public static int getValidSize(@NotNull RealisticVillagers plugin, String sizePath, int min) {
        int size = plugin.getConfig().getInt("gui." + sizePath + ".size");
        return getValidSize(size, min);
    }

    private static int getValidSize(int size, int min) {
        return size < min ? min : Math.min(size, 54);
    }
}