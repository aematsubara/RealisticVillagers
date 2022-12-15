package me.matsubara.realisticvillagers.gui;

import me.matsubara.realisticvillagers.RealisticVillagers;
import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

public abstract class InteractGUI implements InventoryHolder {

    protected final String name;
    protected final int size;
    protected final RealisticVillagers plugin;
    protected final IVillagerNPC npc;
    protected final Inventory inventory;
    private boolean shouldStopInteracting;

    private static final UnaryOperator<String> EMPTY = string -> string;
    public static final String VILLAGER_HEAD_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNGNhOGVmMjQ1OGEyYjEwMjYwYjg3NTY1NThmNzY3OWJjYjdlZjY5MWQ0MWY1MzRlZmVhMmJhNzUxMDczMTVjYyJ9fX0=";

    protected InteractGUI(RealisticVillagers plugin, IVillagerNPC npc, String name, int size, @Nullable UnaryOperator<String> operator) {
        this.name = name;
        this.plugin = plugin;
        this.npc = npc;

        String title = getTitle();
        if (npc != null) title = title.replace("%villager-name%", npc.getVillagerName());

        this.inventory = Bukkit.createInventory(this, (this.size = size), (operator != null ? operator : EMPTY).apply(title));

        this.shouldStopInteracting = true;
    }

    protected String getTitle() {
        return plugin.getConfig().getString("gui." + name + ".title");
    }

    protected ItemStack getGUIItem(String itemName) {
        return plugin.getItem("gui." + name + ".items." + itemName).build();
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
}