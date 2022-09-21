package me.matsubara.realisticvillagers.event;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class VillagerPickGiftEvent extends VillagerEvent {

    private final Player gifter;
    private final ItemStack gift;

    private static final HandlerList handlers = new HandlerList();

    public VillagerPickGiftEvent(IVillagerNPC npc, Player gifter, ItemStack gift) {
        super(npc);
        this.gift = gift;
        this.gifter = gifter;
    }

    public ItemStack getGift() {
        return gift;
    }

    public Player getGifter() {
        return gifter;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return handlers;
    }

    @SuppressWarnings("unused")
    public static @NotNull HandlerList getHandlerList() {
        return handlers;
    }
}