package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class VillagerRiptideEvent extends VillagerEvent {

    private final ItemStack item;

    private static final HandlerList handlers = new HandlerList();

    public VillagerRiptideEvent(IVillagerNPC npc, ItemStack item) {
        super(npc);
        this.item = item;
    }

    @NotNull
    public ItemStack getItem() {
        return item;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}